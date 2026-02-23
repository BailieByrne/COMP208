#include <iostream>
#include <vector>
#include <cmath>
#include <random>
#include <sstream>
#include <fstream>
#include <string>
#include <iomanip>


int main() {
    // Parameters for Brownian motion stock price simulation
    int points = 510; // 510 minutes in a trading day (8:00 to 16:30)
    double S0 = 500.0;  // Initial stock price
    double mu = 0.05;   // Drift
    double sigma = 0.4; // Volatility
    double sentiment = 1.0; // Market sentiment
    
    // Random number generation using normal distribution
    std::random_device rd;
    std::mt19937 gen(rd());
    std::normal_distribution<> dis(0.0, 1.0);
    
    // Vector to store the price points
    std::vector<double> prices;
    prices.reserve(points); // Pre-allocate memory
    prices.push_back(S0);

    //Use the points to create deltas for the timne
    double dt = 1.0 / static_cast<double>(points); // Timestep per point
    
    // Pre-compute constants outside loop
    double drift = (mu * sentiment - 0.5 * sigma * sigma) * dt;
    double vol_sqrt_dt = sigma * std::sqrt(dt);

    // Open CSV file and write header
    std::ofstream csv_file("stock_prices.csv");
    csv_file << "Time,Ticker,Price\n";
    csv_file << "0,STOCK," << S0 << "\n"; // Write initial price

    //Loops through the points to generate the stock prices using the GBM formula with lto correction
    for (int i = 1; i < points; ++i) {
        double Z = dis(gen);
        double price = prices[i - 1] * std::exp(drift + vol_sqrt_dt * Z);
        prices.push_back(price);
        csv_file << i << ",STOCK," << price << "\n"; // Write immediately
    }
    
    csv_file.close();

    return 0;
}