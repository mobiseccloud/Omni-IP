cmake_minimum_required(VERSION 3.22.1)
project("omniip")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(omniip_bridge SHARED bridge.cpp sha256.cpp)
target_compile_options(omniip_bridge PRIVATE -fvisibility=hidden -fno-exceptions -fno-rtti)
target_link_options(omniip_bridge PRIVATE -s)
target_link_libraries(omniip_bridge log)
