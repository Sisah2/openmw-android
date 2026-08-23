cmake_minimum_required(VERSION 3.10)

project(glad C)

# Define the library target
add_library(glad SHARED src/gles2.c)

# Tell other targets where to find include headers 

target_include_directories(glad PUBLIC include)
