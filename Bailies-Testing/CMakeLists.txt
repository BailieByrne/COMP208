cmake_minimum_required(VERSION 3.10)
project(StockSimulation)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set default build type to Release to avoid python_d.lib requirement
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Find Python
find_package(Python3 COMPONENTS Interpreter Development REQUIRED)

# Find NumPy include directory
execute_process(
    COMMAND "${Python3_EXECUTABLE}" -c "import numpy; print(numpy.get_include())"
    OUTPUT_VARIABLE NUMPY_INCLUDE_DIR
    OUTPUT_STRIP_TRAILING_WHITESPACE
)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${Python3_INCLUDE_DIRS}
    ${NUMPY_INCLUDE_DIR}
)

# Suppress specific warnings for MSVC
if(MSVC)
    add_compile_options(/wd4996 /wd4244)
endif()

# Add executable
add_executable(stock_sim main.cpp)

# Link Python library
target_link_libraries(stock_sim ${Python3_LIBRARIES})

# AUTO prints the build being successfule
if(MSVC)
    add_custom_command(TARGET stock_sim POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E echo ""
        COMMAND ${CMAKE_COMMAND} -E echo "===================="
        COMMAND ${CMAKE_COMMAND} -E echo "BUILD SUCCESSFUL"
        COMMAND ${CMAKE_COMMAND} -E echo "===================="
        VERBATIM
    )
endif()

# Print configuration info
message(STATUS "Python3 executable: ${Python3_EXECUTABLE}")
message(STATUS "Python3 include dirs: ${Python3_INCLUDE_DIRS}")
message(STATUS "Python3 libraries: ${Python3_LIBRARIES}")
message(STATUS "NumPy include dir: ${NUMPY_INCLUDE_DIR}")
