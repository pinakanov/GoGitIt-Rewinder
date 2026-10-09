cmake_minimum_required(VERSION 3.22.1)
project(rewinder_whisper C CXX)

# whisper.cpp is downloaded at configure time (first sync needs internet).
set(WHISPER_TAG v1.9.5)

include(FetchContent)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_FFMPEG OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
# ARMv8.2 + dot product: every mainstream Android chip since ~2018 (Cortex-A55/A75 and up).
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)

FetchContent_Declare(
    whisper_cpp
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/${WHISPER_TAG}.tar.gz
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(rewinder_whisper SHARED whisper_jni.cpp)
target_link_libraries(rewinder_whisper PRIVATE whisper android log)
# Keep this library's private copy of ggml from clashing with llama's copy.
target_link_options(rewinder_whisper PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")
