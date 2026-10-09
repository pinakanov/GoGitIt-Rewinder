cmake_minimum_required(VERSION 3.22.1)
project(rewinder_llama C CXX)

# llama.cpp is downloaded at configure time (first sync needs internet).
# Qwen3.5 ("qwen35" architecture) needs a 2026 build of llama.cpp.
set(LLAMA_TAG b11521)

include(FetchContent)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
# ARMv8.2 + dot product: every mainstream Android chip since ~2018 (Cortex-A55/A75 and up).
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)

FetchContent_Declare(
    llama_cpp
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/${LLAMA_TAG}.tar.gz
)
FetchContent_MakeAvailable(llama_cpp)

add_library(rewinder_llama SHARED llama_jni.cpp)
target_link_libraries(rewinder_llama PRIVATE llama android log)
# Keep this library's private copy of ggml from clashing with whisper's copy.
target_link_options(rewinder_llama PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")
