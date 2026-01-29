#include <iostream>
#include <vector>
#include <cmath>
#include <random>
#include <sstream>
#include <iomanip>
#include "deps/matplot/matplotlibcpp.h"

namespace plt = matplotlibcpp;

int main() {
    // Parameters ffor Brownian motion stock price simulation
    int points = 256; // Nice computer science number 256
    double S0 = 500.0;  // Initial stock price (we can adjust this as needed)
    double mu = 0.05;   // Drift
    double sigma = 0.5; // Volatility
    double sentiment = 5.0; // Market sentiment (we can alter this to affect price spikes)
    
    // Trading hours: 8:00 AM to 16:30 (4:30 PM) = 8.5 hours per day
    double trading_hours_per_day = 8.5;
    int minutes_per_day = static_cast<int>(trading_hours_per_day * 60); // 510 minutes
    
    // Random number generation using normal distribution
    std::random_device rd;
    std::mt19937 gen(rd());
    std::normal_distribution<> dis(0.0, 1.0);
    
    // Vector to store the price points
    std::vector<double> prices;
    prices.push_back(S0);

    //Use the points to create deltas for the timne
    double dt = 1.0 / static_cast<double>(points); // Timestep per point

    //Loops through the points to generate the stock prices using the GBM formula with lto correction
    for (int i = 1; i < points; ++i) {
        double Z = dis(gen);
        double drift = (mu * sentiment - 0.5 * sigma * sigma) * dt;
        double diffusion = sigma * std::sqrt(dt) * Z;
        double price = prices[i - 1] * std::exp(drift + diffusion);
        prices.push_back(price);
    }

    // Map points across a single trading day (8:00 to 16:30)
    std::vector<double> x_minutes;
    x_minutes.reserve(points);
    double step_minutes = static_cast<double>(minutes_per_day) / (points - 1);
    for (int i = 0; i < points; ++i) {
        x_minutes.push_back(i * step_minutes);
    }

    // Create time labels every 30 minutes from 08:00 to 16:30
    std::vector<double> tick_positions;
    std::vector<std::string> tick_labels;
    for (int minute_in_day = 0; minute_in_day <= minutes_per_day; minute_in_day += 30) {
        int hours = 8 + (minute_in_day / 60);
        int minutes_part = minute_in_day % 60;

        std::ostringstream oss;
        oss << std::setfill('0') << std::setw(2) << hours
            << ":" << std::setfill('0') << std::setw(2) << minutes_part;

        tick_positions.push_back(minute_in_day);
        tick_labels.push_back(oss.str());
    }

    // Plot the stock prices across one trading day
    plt::figure_size(760, 640);
    plt::plot(x_minutes, prices);
    plt::xticks(tick_positions, tick_labels);
    plt::xlim(0, minutes_per_day);
    plt::xlabel("Time of Day");
    plt::ylabel("Stock Price (£)");
    plt::title("Stock Price Simulation (One Day, " + std::to_string(points) + " points)");
    plt::grid(true);
    plt::tight_layout();
    plt::show();
    
    std::cout << "Generated one trading day with " << points << " points, sentiment = " << sentiment << "\n";
    std::cout << "Initial price: " << S0 << ", Final price: " << prices.back() << "\n";
    
    return 0;
}