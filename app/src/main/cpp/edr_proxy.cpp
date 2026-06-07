#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <lwip/init.h>
#include <lwip/netif.h>
#include <lwip/tcp.h>
#include <lwip/udp.h>
#include <lwip/timeouts.h>
#include <sys/epoll.h>

extern "C" void edr_emit_telemetry(int sourcePort, const char* destIp, int destPort, int protocol, int txBytes, int rxBytes);
extern JavaVM* g_jvm;

#include <vector>
#include <mutex>
#include <deque>
#include <time.h>
#include <unordered_map>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/resource.h>

struct TcpSession {
    struct tcp_pcb* pcb;
    int physical_fd;
    bool is_connected;
    uint32_t original_dest_ip;
    uint16_t original_dest_port;
    std::vector<uint8_t> tx_buffer;
};

static std::unordered_map<int, TcpSession*> fd_to_session;
static std::mutex g_session_mutex;
static std::mutex g_lwip_mutex;
static int g_epoll_fd = -1;

static void free_tcp_session(TcpSession* session) {
    if (!session) return;
    if (session->physical_fd >= 0) {
        epoll_ctl(g_epoll_fd, EPOLL_CTL_DEL, session->physical_fd, NULL);
        close(session->physical_fd);
        fd_to_session.erase(session->physical_fd);
        session->physical_fd = -1;
    }
    if (session->pcb) {
        tcp_arg(session->pcb, NULL);
        tcp_recv(session->pcb, NULL);
        tcp_err(session->pcb, NULL);
        tcp_poll(session->pcb, NULL, 0);
        tcp_close(session->pcb); 
        session->pcb = NULL;
    }
    delete session;
}

struct NatEntry {
    uint32_t original_dst_ip;
    uint16_t original_dst_port;
};
static std::unordered_map<uint16_t, NatEntry> g_tcp_nat_map;
static const uint16_t PROXY_TCP_PORT = 10080;

static uint16_t calculate_checksum(const uint8_t *buf, int len) {
    uint32_t sum = 0;
    for (int i = 0; i < len - 1; i += 2) {
        sum += (buf[i] << 8) | buf[i+1];
    }
    if (len & 1) {
        sum += (buf[len - 1] << 8);
    }
    while (sum >> 16) {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }
    return (uint16_t)~sum;
}

extern "C" u32_t sys_now(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (u32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

extern "C" u32_t sys_arch_protect(void) {
    return 0;
}

extern "C" void sys_arch_unprotect(u32_t pval) {
    (void)pval;
}
#include <mutex>
#include <deque>

struct CapturedPayload {
    uint32_t timestamp;
    uint16_t src_port;
    uint16_t dst_port;
    uint8_t protocol;
    std::vector<uint8_t> data;
};

static std::deque<CapturedPayload> g_payload_buffer;
static std::mutex g_payload_mutex;
static const size_t MAX_PAYLOADS = 100;

static void store_payload(uint16_t src_port, uint16_t dst_port, uint8_t protocol, const uint8_t* payload, size_t len) {
    std::lock_guard<std::mutex> lock(g_payload_mutex);
    if (g_payload_buffer.size() >= MAX_PAYLOADS) {
        g_payload_buffer.pop_front();
    }
    CapturedPayload cp;
    cp.timestamp = time(NULL);
    cp.src_port = src_port;
    cp.dst_port = dst_port;
    cp.protocol = protocol;
    if (payload && len > 0) {
        cp.data.assign(payload, payload + len);
    }
    g_payload_buffer.push_back(cp);
}

// ================= UDP NAT PROXY =================

struct UdpSession {
    int physical_fd;
    uint32_t tun_src_ip;
    uint16_t tun_src_port;
    uint32_t original_dst_ip;
    uint16_t original_dst_port;
    uint64_t last_activity_ms;
    struct udp_pcb* pcb;
};

static std::unordered_map<uint64_t, UdpSession*> udp_nat_table;
static std::mutex g_udp_mutex;
static std::unordered_map<uint16_t, NatEntry> g_udp_nat_map;
static const uint16_t PROXY_UDP_PORT = 10053;

static uint64_t get_udp_session_key(uint32_t local_ip, uint16_t local_port, uint32_t remote_ip, uint16_t remote_port) {
    uint64_t key = 0;
    key ^= std::hash<uint32_t>()(local_ip);
    key ^= std::hash<uint16_t>()(local_port) << 1;
    key ^= std::hash<uint32_t>()(remote_ip) << 2;
    key ^= std::hash<uint16_t>()(remote_port) << 3;
    return key;
}

static void free_udp_session(uint64_t key, UdpSession* session) {
    if (!session) return;
    if (session->physical_fd >= 0) {
        epoll_ctl(g_epoll_fd, EPOLL_CTL_DEL, session->physical_fd, NULL);
        close(session->physical_fd);
    }
    if (session->pcb) {
        udp_remove(session->pcb);
    }
    delete session;
    udp_nat_table.erase(key);
}

extern "C" bool edr_protect_socket(int fd);

static void udp_proxy_recv(void *arg, struct udp_pcb *pcb, struct pbuf *p, const ip_addr_t *addr, u16_t port) {
    if (p == NULL) return;
    
    uint32_t tun_src_ip = addr->addr;
    uint16_t tun_src_port = port;
    
    uint64_t key = get_udp_session_key(pcb->local_ip.addr, pcb->local_port, tun_src_ip, tun_src_port);
    UdpSession* session = nullptr;
    
    std::lock_guard<std::mutex> lock(g_udp_mutex);
    
    auto it = udp_nat_table.find(key);
    if (it == udp_nat_table.end()) {
        uint32_t true_remote_ip = 0;
        uint16_t true_remote_port = 0;
        if (g_udp_nat_map.find(tun_src_port) != g_udp_nat_map.end()) {
            true_remote_ip = g_udp_nat_map[tun_src_port].original_dst_ip;
            true_remote_port = g_udp_nat_map[tun_src_port].original_dst_port;
        } else {
            pbuf_free(p);
            return;
        }

        int sock = socket(AF_INET, SOCK_DGRAM | SOCK_NONBLOCK, 0);
        if (sock < 0 || !edr_protect_socket(sock)) {
            if (sock >= 0) close(sock);
            pbuf_free(p);
            return;
        }
        
        session = new UdpSession();
        session->physical_fd = sock;
        session->tun_src_ip = tun_src_ip;
        session->tun_src_port = tun_src_port;
        session->original_dst_ip = true_remote_ip;
        session->original_dst_port = true_remote_port;
        session->last_activity_ms = sys_now();
        
        struct udp_pcb *v_pcb = udp_new();
        udp_bind(v_pcb, &pcb->local_ip, pcb->local_port);
        udp_connect(v_pcb, addr, port);
        session->pcb = v_pcb;
        
        udp_nat_table[key] = session;
        
        struct epoll_event ev;
        ev.events = EPOLLIN;
        ev.data.fd = sock;
        epoll_ctl(g_epoll_fd, EPOLL_CTL_ADD, sock, &ev);
    } else {
        session = it->second;
        session->last_activity_ms = sys_now();
    }
    
    if (p->tot_len > 0) {
        std::vector<uint8_t> temp_buf(p->tot_len);
        pbuf_copy_partial(p, temp_buf.data(), p->tot_len, 0);
        
        struct sockaddr_in remote_addr;
        remote_addr.sin_family = AF_INET;
        remote_addr.sin_port = htons(session->original_dst_port);
        remote_addr.sin_addr.s_addr = session->original_dst_ip;
        
        sendto(session->physical_fd, temp_buf.data(), temp_buf.size(), 0, (struct sockaddr*)&remote_addr, sizeof(remote_addr));
    }
    
    pbuf_free(p);
}

static void init_udp_proxy() {
    struct udp_pcb *pcb = udp_new();
    if (pcb != NULL) {
        udp_bind(pcb, IP_ANY_TYPE, PROXY_UDP_PORT); 
        udp_recv(pcb, udp_proxy_recv, NULL);
    }
}


// ================= TCP NAT PROXY =================

static err_t tcp_proxy_recv(void *arg, struct tcp_pcb *tpcb, struct pbuf *p, err_t err) {
    TcpSession* session = (TcpSession*)arg;
    
    if (p == NULL) {
        std::lock_guard<std::mutex> lock(g_session_mutex);
        free_tcp_session(session);
        return ERR_OK;
    }
    
    if (!session || session->physical_fd < 0) {
        pbuf_free(p);
        return ERR_ABRT;
    }

    if (p->tot_len > 0) {
        std::vector<uint8_t> temp_buf(p->tot_len);
        pbuf_copy_partial(p, temp_buf.data(), p->tot_len, 0);
        
        if (session->is_connected) {
            send(session->physical_fd, temp_buf.data(), temp_buf.size(), 0);
        } else {
            session->tx_buffer.insert(session->tx_buffer.end(), temp_buf.begin(), temp_buf.end());
        }
    }

    tcp_recved(tpcb, p->tot_len);
    pbuf_free(p);
    return ERR_OK;
}

static void tcp_proxy_err(void *arg, err_t err) {
    TcpSession* session = (TcpSession*)arg;
    std::lock_guard<std::mutex> lock(g_session_mutex);
    if (session) {
        session->pcb = NULL; 
        free_tcp_session(session);
    }
}

static err_t tcp_proxy_accept(void *arg, struct tcp_pcb *newpcb, err_t err) {
    TcpSession* session = new TcpSession();
    session->pcb = newpcb;
    session->is_connected = false;
    
    {
        std::lock_guard<std::mutex> lock(g_session_mutex);
        if (g_tcp_nat_map.find(newpcb->remote_port) != g_tcp_nat_map.end()) {
            session->original_dest_ip = g_tcp_nat_map[newpcb->remote_port].original_dst_ip;
            session->original_dest_port = g_tcp_nat_map[newpcb->remote_port].original_dst_port;
        } else {
            delete session;
            return ERR_ABRT;
        }
    }
    
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0 || !edr_protect_socket(sock)) {
        if (sock >= 0) close(sock);
        delete session;
        return ERR_ABRT;
    }
    
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);
    session->physical_fd = sock;
    
    {
        std::lock_guard<std::mutex> lock(g_session_mutex);
        fd_to_session[sock] = session;
    }
    
    struct sockaddr_in remote_addr;
    remote_addr.sin_family = AF_INET;
    remote_addr.sin_port = htons(session->original_dest_port);
    remote_addr.sin_addr.s_addr = session->original_dest_ip;
    connect(sock, (struct sockaddr*)&remote_addr, sizeof(remote_addr));
    
    struct epoll_event ev;
    ev.events = EPOLLIN | EPOLLOUT | EPOLLRDHUP | EPOLLERR;
    ev.data.fd = sock;
    epoll_ctl(g_epoll_fd, EPOLL_CTL_ADD, sock, &ev);
    
    tcp_arg(newpcb, session);
    tcp_recv(newpcb, tcp_proxy_recv);
    tcp_err(newpcb, tcp_proxy_err);
    
    return ERR_OK;
}

static void init_tcp_proxy() {
    struct tcp_pcb *pcb = tcp_new();
    if (pcb != NULL) {
        tcp_bind(pcb, IP_ANY_TYPE, PROXY_TCP_PORT);
        pcb = tcp_listen(pcb);
        tcp_accept(pcb, tcp_proxy_accept);
    }
}

static int g_vpn_fd = -1;
static struct netif g_vpn_netif;
static pthread_t g_proxy_thread;
static volatile bool g_proxy_running = false;

static err_t vpn_netif_output(struct netif *netif, struct pbuf *p, const ip4_addr_t *ipaddr) {
    if (g_vpn_fd < 0) return ERR_IF;
    
    // Copy pbuf to a contiguous buffer
    uint8_t buffer[65535];
    uint16_t len = pbuf_copy_partial(p, buffer, p->tot_len, 0);
    
    if (len >= 20) {
        uint8_t protocol = buffer[9];
        uint8_t ihl = (buffer[0] & 0x0F) * 4;
        if (len >= ihl + 8 && (protocol == 6 || protocol == 17)) {
            uint16_t src_port = (buffer[ihl] << 8) | buffer[ihl+1];
            uint16_t dst_port = (buffer[ihl+2] << 8) | buffer[ihl+3];
            
            uint32_t orig_ip = 0;
            uint16_t orig_port = 0;
            
            if (protocol == 6 && src_port == PROXY_TCP_PORT) {
                std::lock_guard<std::mutex> lock(g_session_mutex);
                if (g_tcp_nat_map.find(dst_port) != g_tcp_nat_map.end()) {
                    orig_ip = g_tcp_nat_map[dst_port].original_dst_ip;
                    orig_port = g_tcp_nat_map[dst_port].original_dst_port;
                }
            } else if (protocol == 17 && src_port == PROXY_UDP_PORT) {
                std::lock_guard<std::mutex> lock(g_udp_mutex);
                if (g_udp_nat_map.find(dst_port) != g_udp_nat_map.end()) {
                    orig_ip = g_udp_nat_map[dst_port].original_dst_ip;
                    orig_port = g_udp_nat_map[dst_port].original_dst_port;
                }
            }
            
            if (orig_ip != 0) {
                // Rewrite Source IP & Port to Original Destination
                std::memcpy(&buffer[12], &orig_ip, 4);
                buffer[ihl] = (orig_port >> 8) & 0xFF;
                buffer[ihl+1] = orig_port & 0xFF;
                
                // Recalculate IP Checksum
                buffer[10] = 0; 
                buffer[11] = 0;
                uint16_t ip_chk = calculate_checksum(buffer, ihl);
                buffer[10] = (ip_chk >> 8) & 0xFF;
                buffer[11] = ip_chk & 0xFF;
                
                // Recalculate TCP/UDP Checksum
                if (protocol == 6) {
                    buffer[ihl+16] = 0; 
                    buffer[ihl+17] = 0;
                    int tcp_len = len - ihl;
                    uint32_t tcp_sum = 0;
                    for (int i = 12; i < 20; i += 2) tcp_sum += (buffer[i] << 8) | buffer[i+1];
                    tcp_sum += protocol; 
                    tcp_sum += tcp_len;
                    for (int i = ihl; i < len - 1; i += 2) tcp_sum += (buffer[i] << 8) | buffer[i+1];
                    if (len % 2 != 0) tcp_sum += (buffer[len - 1] << 8);
                    while (tcp_sum >> 16) tcp_sum = (tcp_sum & 0xFFFF) + (tcp_sum >> 16);
                    uint16_t tcp_chk = (uint16_t)~tcp_sum;
                    buffer[ihl+16] = (tcp_chk >> 8) & 0xFF;
                    buffer[ihl+17] = tcp_chk & 0xFF;
                } else if (protocol == 17) {
                    buffer[ihl+6] = 0; 
                    buffer[ihl+7] = 0;
                    int udp_len = len - ihl;
                    uint32_t udp_sum = 0;
                    for (int i = 12; i < 20; i += 2) udp_sum += (buffer[i] << 8) | buffer[i+1];
                    udp_sum += protocol; 
                    udp_sum += udp_len;
                    for (int i = ihl; i < len - 1; i += 2) udp_sum += (buffer[i] << 8) | buffer[i+1];
                    if (len % 2 != 0) udp_sum += (buffer[len - 1] << 8);
                    while (udp_sum >> 16) udp_sum = (udp_sum & 0xFFFF) + (udp_sum >> 16);
                    uint16_t udp_chk = (uint16_t)~udp_sum;
                    if (udp_chk == 0) udp_chk = 0xFFFF;
                    buffer[ihl+6] = (udp_chk >> 8) & 0xFF;
                    buffer[ihl+7] = udp_chk & 0xFF;
                }
                
                // Telemetry: dst_port is client's port. orig_ip is the remote server's IP
                struct in_addr dest_addr;
                dest_addr.s_addr = orig_ip;
                edr_emit_telemetry(dst_port, inet_ntoa(dest_addr), orig_port, protocol, 0, len);
            }
        }
    }
    
    // Write back to Android VPN interface
    ssize_t written = write(g_vpn_fd, buffer, len);
    if (written < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "OmniIP-EDR", "Failed to write to VPN FD");
        return ERR_IF;
    }
    return ERR_OK;
}

static err_t vpn_netif_init(struct netif *netif) {
    netif->name[0] = 'v';
    netif->name[1] = 'p';
    netif->output = vpn_netif_output;
    netif->mtu = 1500;
    netif->flags = NETIF_FLAG_LINK_UP | NETIF_FLAG_UP;
    return ERR_OK;
}

static void* proxy_loop(void* arg) {
    uint8_t buffer[65535];
    __android_log_print(ANDROID_LOG_INFO, "OmniIP-EDR", "User-Space Proxy Loop Started");
    
    JNIEnv *env;
    bool attached = false;
    if (g_jvm && g_jvm->AttachCurrentThread(&env, NULL) == 0) {
        attached = true;
    }

    g_epoll_fd = epoll_create1(0);
    struct epoll_event ev, events[10];

    while (g_proxy_running) {
        uint32_t next_timeout_ms;
        {
            std::lock_guard<std::mutex> lwip_lock(g_lwip_mutex);
            next_timeout_ms = sys_timeouts_sleeptime();
        }
        int timeout = (next_timeout_ms == SYS_TIMEOUTS_SLEEPTIME_INFINITE) ? -1 : static_cast<int>(next_timeout_ms);
        
        int nfds = epoll_wait(g_epoll_fd, events, 10, timeout);

        std::lock_guard<std::mutex> lwip_lock(g_lwip_mutex);

        if (nfds > 0) {
            for (int n = 0; n < nfds; ++n) {
                int current_fd = events[n].data.fd;
                bool is_tcp = false;
                {
                    std::lock_guard<std::mutex> lock(g_session_mutex);
                    if (fd_to_session.find(current_fd) != fd_to_session.end()) {
                        is_tcp = true;
                        TcpSession* session = fd_to_session[current_fd];
                        
                        if (events[n].events & EPOLLOUT) {
                            session->is_connected = true;
                            if (!session->tx_buffer.empty()) {
                                send(current_fd, session->tx_buffer.data(), session->tx_buffer.size(), 0);
                                session->tx_buffer.clear();
                            }
                            struct epoll_event ev_mod;
                            ev_mod.events = EPOLLIN | EPOLLRDHUP | EPOLLERR;
                            ev_mod.data.fd = current_fd;
                            epoll_ctl(g_epoll_fd, EPOLL_CTL_MOD, current_fd, &ev_mod);
                        }
                        
                        if (events[n].events & EPOLLIN) {
                            uint8_t rx_buf[8192];
                            ssize_t bytes = recv(current_fd, rx_buf, sizeof(rx_buf), 0);
                            if (bytes > 0 && session->pcb) {
                                tcp_write(session->pcb, rx_buf, bytes, TCP_WRITE_FLAG_COPY);
                                tcp_output(session->pcb);
                            } else if (bytes == 0) {
                                free_tcp_session(session);
                                continue;
                            }
                        }
                        
                        if (events[n].events & (EPOLLRDHUP | EPOLLERR | EPOLLHUP)) {
                            free_tcp_session(session);
                        }
                    }
                }
                
                if (!is_tcp) {
                    // UDP DOWNLINK HANDLING
                    std::lock_guard<std::mutex> lock_udp(g_udp_mutex);
                        UdpSession* u_session = nullptr;
                        uint64_t found_key = 0;
                        for (auto& pair : udp_nat_table) {
                            if (pair.second->physical_fd == current_fd) {
                                u_session = pair.second;
                                found_key = pair.first;
                                break;
                            }
                        }
                        
                        if (u_session) {
                            if (events[n].events & EPOLLIN) {
                                uint8_t rx_buf[8192];
                                struct sockaddr_in src_addr;
                                socklen_t addr_len = sizeof(src_addr);
                                ssize_t bytes = recvfrom(current_fd, rx_buf, sizeof(rx_buf), 0, (struct sockaddr*)&src_addr, &addr_len);
                                
                                if (bytes > 0 && u_session->pcb) {
                                    u_session->last_activity_ms = sys_now();
                                    struct pbuf *p = pbuf_alloc(PBUF_TRANSPORT, bytes, PBUF_POOL);
                                    if (p != NULL) {
                                        pbuf_take(p, rx_buf, bytes);
                                        
                                        ip_addr_t remote_ip_addr;
                                        remote_ip_addr.addr = u_session->tun_src_ip;
                                        
                                        udp_sendto(u_session->pcb, p, &remote_ip_addr, u_session->tun_src_port);
                                        pbuf_free(p);
                                    }
                                }
                            }
                        }
                }
            }
        }
        
        // ================= UDP GARBAGE COLLECTION (TTL) =================
        {
            std::lock_guard<std::mutex> lock_udp(g_udp_mutex);
            uint32_t now = sys_now();
            
            struct rlimit rl;
            int max_fds = 1024;
            if (getrlimit(RLIMIT_NOFILE, &rl) == 0) {
                max_fds = rl.rlim_cur;
            }
            
            int total_tracked_fds = udp_nat_table.size();
            uint32_t active_ttl = 60000;
            
            if (total_tracked_fds > (max_fds * 0.8)) {
                active_ttl = 5000; 
            }
            
            std::vector<uint64_t> to_delete;
            for (auto& pair : udp_nat_table) {
                if (now - pair.second->last_activity_ms > active_ttl) {
                    to_delete.push_back(pair.first);
                }
            }
            for (uint64_t key : to_delete) {
                free_udp_session(key, udp_nat_table[key]);
            }
        }
        
        // Let lwip process timers since NO_SYS=1
        sys_check_timeouts();
    }
    
    close(g_epoll_fd);
    if (attached) {
        g_jvm->DetachCurrentThread();
    }
    return NULL;
}

extern "C" void edr_proxy_start(int vpn_fd) {
    if (g_proxy_running) return;
    
    g_vpn_fd = vpn_fd;
    lwip_init();
    
    ip4_addr_t ipaddr, netmask, gw;
    IP4_ADDR(&gw, 10, 0, 0, 1);
    IP4_ADDR(&ipaddr, 10, 0, 0, 1);
    IP4_ADDR(&netmask, 255, 255, 255, 0);
    
    netif_add(&g_vpn_netif, &ipaddr, &netmask, &gw, NULL, vpn_netif_init, ip_input);
    netif_set_default(&g_vpn_netif);
    netif_set_up(&g_vpn_netif);
    
    g_proxy_running = true;
    init_tcp_proxy();
    init_udp_proxy();
    pthread_create(&g_proxy_thread, NULL, proxy_loop, NULL);
}

extern "C" void edr_proxy_input_packet(const uint8_t* data, size_t length) {
    if (!g_proxy_running) return;
    
    std::vector<uint8_t> pkt(data, data + length);
    
    if (length >= 20) {
        uint8_t protocol = pkt[9];
        if (protocol == 6) { 
            uint8_t ihl = (pkt[0] & 0x0F) * 4;
            
            if (length >= ihl + 20) {
                uint16_t src_port = (pkt[ihl] << 8) | pkt[ihl+1];
                uint16_t dst_port = (pkt[ihl+2] << 8) | pkt[ihl+3];
                
                if (dst_port != PROXY_TCP_PORT) {
                    uint32_t dst_ip;
                    std::memcpy(&dst_ip, &pkt[16], 4);
                    
                    {
                        std::lock_guard<std::mutex> lock(g_session_mutex);
                        g_tcp_nat_map[src_port] = { dst_ip, dst_port };
                    }
                    
                    uint32_t proxy_ip = inet_addr("10.0.0.1");
                    std::memcpy(&pkt[16], &proxy_ip, 4);
                    pkt[ihl+2] = (PROXY_TCP_PORT >> 8) & 0xFF;
                    pkt[ihl+3] = PROXY_TCP_PORT & 0xFF;
                    
                    // Emit TX Telemetry
                    struct in_addr dest_addr;
                    dest_addr.s_addr = dst_ip;
                    edr_emit_telemetry(src_port, inet_ntoa(dest_addr), dst_port, protocol, length, 0);
                    
                    pkt[10] = 0; 
                    pkt[11] = 0;
                    uint16_t ip_checksum = calculate_checksum(pkt.data(), ihl);
                    pkt[10] = (ip_checksum >> 8) & 0xFF;
                    pkt[11] = ip_checksum & 0xFF;
                    
                    int tcp_len = length - ihl;
                    pkt[ihl+16] = 0; 
                    pkt[ihl+17] = 0;
                    
                    uint32_t tcp_sum = 0;
                    for (int i = 12; i < 20; i += 2) {
                        tcp_sum += (pkt[i] << 8) | pkt[i+1];
                    }
                    tcp_sum += protocol; 
                    tcp_sum += tcp_len;
                    
                    for (int i = ihl; i < length - 1; i += 2) {
                        tcp_sum += (pkt[i] << 8) | pkt[i+1];
                    }
                    if (length % 2 != 0) {
                        tcp_sum += (pkt[length - 1] << 8); 
                    }
                    
                    while (tcp_sum >> 16) {
                        tcp_sum = (tcp_sum & 0xFFFF) + (tcp_sum >> 16);
                    }
                    
                    uint16_t tcp_checksum = (uint16_t)~tcp_sum;
                    pkt[ihl+16] = (tcp_checksum >> 8) & 0xFF;
                    pkt[ihl+17] = tcp_checksum & 0xFF;
                }
            }
        } else if (protocol == 17) {
            uint8_t ihl = (pkt[0] & 0x0F) * 4;
            if (length >= ihl + 8) {
                uint16_t src_port = (pkt[ihl] << 8) | pkt[ihl+1];
                uint16_t dst_port = (pkt[ihl+2] << 8) | pkt[ihl+3];
                
                if (dst_port != PROXY_UDP_PORT) {
                    uint32_t dst_ip;
                    std::memcpy(&dst_ip, &pkt[16], 4);
                    
                    {
                        std::lock_guard<std::mutex> lock(g_udp_mutex);
                        g_udp_nat_map[src_port] = { dst_ip, dst_port };
                    }
                    
                    uint32_t proxy_ip = inet_addr("10.0.0.1");
                    std::memcpy(&pkt[16], &proxy_ip, 4);
                    pkt[ihl+2] = (PROXY_UDP_PORT >> 8) & 0xFF;
                    pkt[ihl+3] = PROXY_UDP_PORT & 0xFF;
                    
                    // Emit TX Telemetry
                    struct in_addr dest_addr;
                    dest_addr.s_addr = dst_ip;
                    edr_emit_telemetry(src_port, inet_ntoa(dest_addr), dst_port, protocol, length, 0);
                    
                    pkt[10] = 0; 
                    pkt[11] = 0;
                    uint16_t ip_checksum = calculate_checksum(pkt.data(), ihl);
                    pkt[10] = (ip_checksum >> 8) & 0xFF;
                    pkt[11] = ip_checksum & 0xFF;
                    
                    int udp_len = length - ihl;
                    pkt[ihl+6] = 0; 
                    pkt[ihl+7] = 0;
                    
                    uint32_t udp_sum = 0;
                    for (int i = 12; i < 20; i += 2) {
                        udp_sum += (pkt[i] << 8) | pkt[i+1];
                    }
                    udp_sum += protocol; 
                    udp_sum += udp_len;
                    
                    for (int i = ihl; i < length - 1; i += 2) {
                        udp_sum += (pkt[i] << 8) | pkt[i+1];
                    }
                    if (length % 2 != 0) {
                        udp_sum += (pkt[length - 1] << 8); 
                    }
                    
                    while (udp_sum >> 16) {
                        udp_sum = (udp_sum & 0xFFFF) + (udp_sum >> 16);
                    }
                    
                    uint16_t udp_checksum = (uint16_t)~udp_sum;
                    if (udp_checksum == 0) udp_checksum = 0xFFFF;
                    pkt[ihl+6] = (udp_checksum >> 8) & 0xFF;
                    pkt[ihl+7] = udp_checksum & 0xFF;
                }
            }
        } else {
            // Emit Telemetry for unhandled protocols (e.g., ICMP = 1)
            uint8_t ihl = (pkt[0] & 0x0F) * 4;
            if (length >= ihl + 8) {
                uint32_t dst_ip;
                std::memcpy(&dst_ip, &pkt[16], 4);
                struct in_addr dest_addr;
                dest_addr.s_addr = dst_ip;
                edr_emit_telemetry(0, inet_ntoa(dest_addr), 0, protocol, length, 0);
            }
        }
    }
    
    std::lock_guard<std::mutex> lwip_lock(g_lwip_mutex);
    struct pbuf *p = pbuf_alloc(PBUF_RAW, length, PBUF_POOL);
    if (p != NULL) {
        pbuf_take(p, pkt.data(), length);
        if (g_vpn_netif.input(p, &g_vpn_netif) != ERR_OK) {
            // LwIP input functions take ownership of the pbuf and free it internally,
            // even if they return an error. Do not double-free here!
        }
    }
}

extern "C" void edr_proxy_kill_tcp(uint32_t src_ip, uint32_t dst_ip, uint16_t src_port, uint16_t dst_port) {
    std::lock_guard<std::mutex> session_lock(g_session_mutex);
    std::lock_guard<std::mutex> lwip_lock(g_lwip_mutex);
    
    // Find the session that matches dst_ip, dst_port, and src_port
    for (auto it = fd_to_session.begin(); it != fd_to_session.end(); ++it) {
        TcpSession* session = it->second;
        if (session && session->pcb && 
            session->original_dest_ip == dst_ip && 
            session->original_dest_port == dst_port &&
            session->pcb->remote_port == src_port) {
            
            // Send RST via LWIP
            tcp_abort(session->pcb);
            session->pcb = NULL;
            free_tcp_session(session);
            break;
        }
    }
}
