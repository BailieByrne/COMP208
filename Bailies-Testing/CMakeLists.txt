cmake_minimum_required(VERSION 3.10)
project(StockSimulation)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set default build type to Release
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Detect operating system
if(APPLE)
    set(OS_NAME "macOS")
elseif(WIN32)
    set(OS_NAME "Windows")
elseif(UNIX)
    set(OS_NAME "Linux")
else()
    set(OS_NAME "Unknown")
endif()

message(STATUS "Building on: ${OS_NAME}")

# Find Python with better error handling
find_package(Python3 COMPONENTS Interpreter Development)

if(NOT Python3_FOUND)
    message(FATAL_ERROR 
        "\n"
        "========================================\n"
        "MISSING PYTHON\n"
        "========================================\n"
        "Python 3 was not found on your system.\n"
        "\n"
        "Installation instructions:\n"
        "- Windows: https://www.python.org/downloads/windows/\n"
        "- macOS: brew install python3\n"
        "\n"
        "After installing Python 3, run cmake again.\n"
        "========================================\n"
    )
endif()

# Check for NumPy
execute_process(
    COMMAND "${Python3_EXECUTABLE}" -c "import numpy; print(numpy.get_include())"
    OUTPUT_VARIABLE NUMPY_INCLUDE_DIR
    OUTPUT_STRIP_TRAILING_WHITESPACE
    RESULT_VARIABLE NUMPY_RESULT
)

if(NOT NUMPY_RESULT EQUAL 0)
    message(STATUS "NumPy not found. Attempting to install via pip...")
    
    execute_process(
        COMMAND "${Python3_EXECUTABLE}" -m pip install numpy
        RESULT_VARIABLE PIP_INSTALL_RESULT
    )
    
    if(NOT PIP_INSTALL_RESULT EQUAL 0)
        message(FATAL_ERROR 
            "\n"
            "========================================\n"
            "FAILED TO INSTALL NUMPY\n"
            "========================================\n"
            "NumPy could not be installed automatically.\n"
            "\n"
            "Please try installing manually:\n"
            "pip install numpy\n"
            "\n"
            "Then run cmake again.\n"
            "========================================\n"
        )
    endif()
    
    # Try to get NumPy include dir again
    execute_process(
        COMMAND "${Python3_EXECUTABLE}" -c "import numpy; print(numpy.get_include())"
        OUTPUT_VARIABLE NUMPY_INCLUDE_DIR
        OUTPUT_STRIP_TRAILING_WHITESPACE
        RESULT_VARIABLE NUMPY_RESULT
    )
    
    if(NOT NUMPY_RESULT EQUAL 0)
        message(FATAL_ERROR 
            "\n"
            "========================================\n"
            "NUMPY INSTALLATION VERIFICATION FAILED\n"
            "========================================\n"
            "NumPy was installed but couldn't be located.\n"
            "Please try running cmake again.\n"
            "========================================\n"
        )
    endif()
    
    message(STATUS "NumPy installed successfully!")
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${Python3_INCLUDE_DIRS}
    ${NUMPY_INCLUDE_DIR}
)

# Suppress compiler warnings
if(MSVC)
    add_compile_options(/wd4996 /wd4244)
elseif(APPLE OR UNIX)
    add_compile_options(-Wno-deprecated-declarations -Wno-conversion)
endif()

# Add executable
add_executable(stock_sim main.cpp)

# Link Python library
target_link_libraries(stock_sim ${Python3_LIBRARIES})

# Post-build success message
add_custom_command(TARGET stock_sim POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E echo ""
    COMMAND ${CMAKE_COMMAND} -E echo "===================="
    COMMAND ${CMAKE_COMMAND} -E echo "BUILD SUCCESSFUL"
    COMMAND ${CMAKE_COMMAND} -E echo "===================="
    COMMAND ${CMAKE_COMMAND} -E echo ""
    COMMAND ${CMAKE_COMMAND} -E echo "Run the executable:"
    COMMAND ${CMAKE_COMMAND} -E echo "./Release/stock_sim (Windows: .exe || macOS: .out)"
)

# Print configuration info
message(STATUS "Python3 executable: ${Python3_EXECUTABLE}")
message(STATUS "Python3 version: ${Python3_VERSION}")
message(STATUS "Python3 include dirs: ${Python3_INCLUDE_DIRS}")
message(STATUS "Python3 libraries: ${Python3_LIBRARIES}")
message(STATUS "NumPy include dir: ${NUMPY_INCLUDE_DIR}")
