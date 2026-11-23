cmake_minimum_required(VERSION 3.22.1)

project("frida_test_bench")

add_library(hello_absolute SHARED hello_absolute.cpp)
add_library(hello_named SHARED hello_named.cpp)

find_library(log_library log)

target_link_libraries(hello_absolute ${log_library})
target_link_libraries(hello_named ${log_library})
