cmake_minimum_required(VERSION 3.22.1)
project("omniip")

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/source/security/)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/lwip/src/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/lwip_port)

file(GLOB_RECURSE LWIP_SRC
    "lwip/src/core/*.c"
    "lwip/src/core/ipv4/*.c"
    "lwip/src/api/*.c"
    "lwip/src/netif/*.c"
)
list(FILTER LWIP_SRC EXCLUDE REGEX "slipif.c")

add_library(omniip_bridge SHARED bridge.cpp sha256.cpp edr_proxy.cpp ${LWIP_SRC})
target_compile_options(omniip_bridge PRIVATE -fvisibility=hidden -fno-exceptions -fno-rtti)
target_link_options(omniip_bridge PRIVATE "-Wl,-z,max-page-size=16384" -s)
target_link_libraries(omniip_bridge log)

