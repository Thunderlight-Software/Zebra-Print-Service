# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

add_definitions(-DANDROID)

if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_definitions(-DNDEBUG)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections")
    set(LINKER_FLAGS "${LINKER_FLAGS} --strip-all")
    Message("--- Release Mode ---")
endif()

include_directories(src/main/cpp/)

add_library( # Sets the name of the library.
        ZebraUtils

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        jni.cpp
        ZPLHelper.cpp
        CPCHelper.cpp
        Internal.h)

find_library( # Sets the name of the path variable.
        log-lib
        z
        jnigraphics
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

target_link_libraries( # Specifies the target library.
        ZebraUtils
        -ljnigraphics
        -lz
        -llog

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})