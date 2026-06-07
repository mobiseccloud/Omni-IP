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

// ================= UDP HONEYPOT =================
static void udp_honeypot_recv(void *arg, struct udp_pcb *pcb, struct pbuf *p, const ip_addr_t *addr, u16_t port) {
    if (p == NULL) return;
    
    __android_log_print(ANDROID_LOG_INFO, "OmniIP-EDR", "Honeypot Captured %d bytes of UDP TX data on port %d!", p->tot_len, pcb->local_port);
    
    if (p->tot_len > 0) {
        std::vector<uint8_t> temp_buf(p->tot_len);
        pbuf_copy_partial(p, temp_buf.data(), p->tot_len, 0);
        store_payload(port, pcb->local_port, 17, temp_buf.data(), p->tot_len);
    }
    
    pbuf_free(p);
}

static void init_udp_proxy() {
    struct udp_pcb *pcb = udp_new();
    if (pcb != NULL) {
        udp_bind(pcb, IP_ANY_TYPE, 53); // DNS Honeypot
        udp_recv(pcb, udp_honeypot_recv, NULL);
    }
}
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <errno.h>

extern "C" bool edr_protect_socket(int fd);

static int create_physical_socket(const ip4_addr_t *dest_ip, u16_t dest_port, bool is_udp) {
    int sock = socket(AF_INET, is_udp ? SOCK_DGRAM : SOCK_STREAM, 0);
    if (sock < 0) return -1;
    
    // Protect the socket from VPN routing loop via JNI
    if (!edr_protect_socket(sock)) {
        __android_log_print(ANDROID_LOG_ERROR, "OmniIP-EDR", "Failed to protect socket %d", sock);
        close(sock);
        return -1;
    }
    
    // Make socket non-blocking
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);
    
    struct sockaddr_in remote_addr;
    remote_addr.sin_family = AF_INET;
    remote_addr.sin_port = htons(dest_port);
    remote_addr.sin_addr.s_addr = dest_ip->addr;
    
    // Initiate non-blocking connect
    int ret = connect(sock, (struct sockaddr*)&remote_addr, sizeof(remote_addr));
    if (ret < 0 && errno != EINPROGRESS) {
        close(sock);
        return -1;
    }
    
    __android_log_print(ANDROID_LOG_INFO, "OmniIP-EDR", "Successfully created and protected physical socket %d to port %d", sock, dest_port);
    return sock;
}
// ================= TCP HONEYPOT & PROXY =================

static err_t tcp_honeypot_recv(void *arg, struct tcp_pcb *tpcb, struct pbuf *p, err_t err) {
    if (p == NULL) {
        // Connection closed by client
        tcp_close(tpcb);
        return ERR_OK;
    }
    
    // Telemetry: We captured TX data from the malicious app!
    __android_log_print(ANDROID_LOG_INFO, "OmniIP-EDR", "Honeypot Captured %d bytes of TX data on port %d!", p->tot_len, tpcb->local_port);
    
    if (p->tot_len > 0) {
        std::vector<uint8_t> temp_buf(p->tot_len);
        pbuf_copy_partial(p, temp_buf.data(), p->tot_len, 0);
        store_payload(tpcb->remote_port, tpcb->local_port, 6, temp_buf.data(), p->tot_len);
    }

    // Acknowledge the data so the app thinks it was successfully sent
    tcp_recved(tpcb, p->tot_len);
    edr_emit_telemetry(tpcb->remote_port, "10.0.0.2", tpcb->local_port, 6, p->tot_len, 0);
    
    // Free the packet buffer
    pbuf_free(p);
    
    // For starvation: we DO NOT send anything back. The app starves waiting for RX.
    // As explicitly directed: "execute tcp_recved(tpcb, p->tot_len) and pbuf_free(p), but intentionally skip any relay logic"
    return ERR_OK;
}

static err_t tcp_starvation_poll(void *arg, struct tcp_pcb *tpcb) {
    __android_log_print(ANDROID_LOG_WARN, "OmniIP-EDR", "Starvation GC: Aborting idle socket on port %d", tpcb->local_port);
    tcp_abort(tpcb);
    return ERR_ABRT;
}

static err_t tcp_honeypot_accept(void *arg, struct tcp_pcb *newpcb, err_t err) {
    __android_log_print(ANDROID_LOG_INFO, "OmniIP-EDR", "Honeypot Accepted connection from App to port %d", newpcb->local_port);
    
    // Set up the receive callback for this connection
    tcp_recv(newpcb, tcp_honeypot_recv);
    // Set up poll for Starvation GC (poll interval = 120, which is roughly 60 seconds since poll triggers every 500ms in lwIP)
    tcp_poll(newpcb, tcp_starvation_poll, 120);
    return ERR_OK;
}

static void init_tcp_proxy() {
    // Create a raw TCP PCB that listens to everything
    // In LwIP, we can bind to IP_ANY_TYPE and port 0, but usually we need a listener for specific ports.
    struct tcp_pcb *pcb = tcp_new();
    if (pcb != NULL) {
        // Bind to all local IP addresses, port 80 (HTTP)
        tcp_bind(pcb, IP_ANY_TYPE, 80);
        pcb = tcp_listen(pcb);
        tcp_accept(pcb, tcp_honeypot_accept);
    }
    
    struct tcp_pcb *pcbs = tcp_new();
    if (pcbs != NULL) {
        // Bind to HTTPS (443)
        tcp_bind(pcbs, IP_ANY_TYPE, 443);
        pcbs = tcp_listen(pcbs);
        tcp_accept(pcbs, tcp_honeypot_accept);
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

    int epoll_fd = epoll_create1(0);
    struct epoll_event ev, events[10];
    
    ev.events = EPOLLIN;
    ev.data.fd = g_vpn_fd;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, g_vpn_fd, &ev);

    // Make g_vpn_fd non-blocking
    int flags = fcntl(g_vpn_fd, F_GETFL, 0);
    fcntl(g_vpn_fd, F_SETFL, flags | O_NONBLOCK);

    while (g_proxy_running) {
        uint32_t next_timeout_ms = sys_timeouts_sleeptime();
        int timeout = (next_timeout_ms == SYS_TIMEOUTS_SLEEPTIME_INFINITE) ? -1 : static_cast<int>(next_timeout_ms);
        
        int nfds = epoll_wait(epoll_fd, events, 10, timeout);

        if (nfds > 0) {
            for (int n = 0; n < nfds; ++n) {
                if (events[n].data.fd == g_vpn_fd) {
                    ssize_t length;
                    while ((length = read(g_vpn_fd, buffer, sizeof(buffer))) > 0) {
                        struct pbuf *p = pbuf_alloc(PBUF_RAW, length, PBUF_POOL);
                        if (p != NULL) {
                            pbuf_take(p, buffer, length);
                            if (g_vpn_netif.input(p, &g_vpn_netif) != ERR_OK) {
                                pbuf_free(p);
                            }
                        }
                    }
                }
            }
        }
        
        // Let lwip process timers since NO_SYS=1
        sys_check_timeouts();
    }
    
    close(epoll_fd);
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
    IP4_ADDR(&ipaddr, 10, 0, 0, 2);
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
    struct pbuf *p = pbuf_alloc(PBUF_RAW, length, PBUF_POOL);
    if (p != NULL) {
        pbuf_take(p, data, length);
        if (g_vpn_netif.input(p, &g_vpn_netif) != ERR_OK) {
            pbuf_free(p);
        }
    }
}
