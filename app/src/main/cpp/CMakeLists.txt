cmake_minimum_required(VERSION 3.22.1)
project("omniip")

add_library(omniip_bridge SHARED bridge.cpp)
target_link_libraries(omniip_bridge log)
