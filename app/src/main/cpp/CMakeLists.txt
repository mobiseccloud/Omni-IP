cmake_minimum_required(VERSION 3.22.1)
project("omniip")

file(GLOB_RECURSE ICMPENGUIN_SRC "../../samples/network-scanners/icmpenguin/*.c" "../../samples/network-scanners/icmpenguin/*.cpp")
add_library(icmpenguin SHARED ${ICMPENGUIN_SRC})
target_include_directories(icmpenguin PRIVATE "../../samples/network-scanners/icmpenguin/")
target_link_libraries(icmpenguin log)

file(GLOB_RECURSE LIBSOCKET_SRC "../../samples/utilities/libsocket/*.c" "../../samples/utilities/libsocket/*.cpp")
add_library(libsocket SHARED ${LIBSOCKET_SRC})
target_include_directories(libsocket PRIVATE "../../samples/utilities/libsocket/")
target_link_libraries(libsocket log)

file(GLOB_RECURSE NMAP_SRC "../../samples/network-scanners/nmap-ndk/*.c" "../../samples/network-scanners/nmap-ndk/*.cpp")
add_library(nmap SHARED ${NMAP_SRC})
target_include_directories(nmap PRIVATE "../../samples/network-scanners/nmap-ndk/")
target_link_libraries(nmap log)

add_library(omniip_bridge SHARED bridge.cpp)
target_link_libraries(omniip_bridge icmpenguin libsocket nmap log)
