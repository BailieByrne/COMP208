#include <iostream>
#include <vector>
#include <cmath>
#include <random>
#include <sstream>
#include <fstream>
#include <string>
#include <iomanip>
#include <cstring>
#include <functional>
#include <cstdint>
#include "AI.cpp"


//UUID Code i found generates a random V4 UUID, 2^122 possibile UUIDS
static std::string generateUuidV4() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<int> dist(0, 15);
    std::uniform_int_distribution<int> dist2(8, 11);

    std::stringstream ss;
    ss << std::hex;
    for (int i = 0; i < 8; i++) ss << dist(gen);
    ss << "-";
    for (int i = 0; i < 4; i++) ss << dist(gen);
    ss << "-4";
    for (int i = 0; i < 3; i++) ss << dist(gen);
    ss << "-" << dist2(gen);
    for (int i = 0; i < 3; i++) ss << dist(gen);
    ss << "-";
    for (int i = 0; i < 12; i++) ss << dist(gen);
    return ss.str();
}


int main(int argc, char *argv[]) {
    // Parameters for Brownian motion stock price simulation
    const int points = 510; // 510 minutes in a trading day (8:00 to 16:30)
    
    // Default parameters
    double S0 = 500.0;      // Initial stock price / previous close
    int difficulty = 2;     // AI difficulty level
    std::string ticker;     // Stock ticker
    std::string userSeed;   // Benchmark seed string

    // Parse command line arguments, add cerror for better handling
    if (argc < 2) {
        std::cerr << "Usage: " << argv[0] << " <ticker> [difficulty] [S0] [seed]\n";
        std::cerr << "Example: " << argv[0] << " AAPL 2 500.0 benchmark_seed_01\n";
        return 1;
    }

    //Extract the ticker
    ticker = argv[1];

    //Extract the difficulty else default to 1
    if (argc >= 3) {
        try {
            difficulty = std::stoi(argv[2]);
            if (difficulty < 1 || difficulty > 4) {
                std::cerr << "Difficulty must be 1-4, got " << difficulty << "\n";
                return 1;
            }
        } catch (const std::exception& e) {
            std::cerr << "Invalid difficulty: " << argv[2] << "\n";
            return 1;
        }
    }

    //Take startign price useful for the server to chain gameplay
    if (argc >= 4) {
        try {
            S0 = std::stod(argv[3]);
            if (S0 <= 0) {
                std::cerr << "S0 must be positive, got " << S0 << "\n";
                return 1;
            }
        } catch (const std::exception& e) {
            std::cerr << "Invalid S0: " << argv[3] << "\n";
            return 1;
        }
    }

    //Take Seed If provided (DEBUG Mostly)
    if (argc >= 5) {
        userSeed = argv[4];
    }

    //No seed , generate one with UUIDV4
    if (userSeed.empty() || userSeed == "NULL" || userSeed == "null") {
        userSeed = generateUuidV4();
    }

    //Use an unsigned 64 but int for the hash so collapses 2^122 to 2^64 still enough possible paths
    std::uint64_t numericSeed = static_cast<std::uint64_t>(std::hash<std::string>{}(userSeed));

    //Default
    /**
     * TODO:
     * - Add command line args for the params and seed (piecewise)
     */
    double mu = 0.05;       // Drift
    double sigma = 0.4;     // Volatility
    double sentiment = 0.0; // Market sentiment


    //spit out the Seed for debugging and reproducibility
    std::cout << "Seed input=" << userSeed << " numeric_seed=" << numericSeed << "\n";

    // Random number generation using normal distributions

    //here I added seed based generation so i can replay exact days worth of data
    std::mt19937 gen(static_cast<std::mt19937::result_type>(numericSeed));
    std::normal_distribution<> dis(0.0, 1.0);
    
    // Vector to store the price points
    std::vector<double> prices;
    prices.reserve(points); // Pre-allocate memory
    prices.push_back(S0);

    //Use the points to create deltas for the timne
    double dt = 1.0 / static_cast<double>(points); // Timestep per point
    
    // Precompute constants outside loop
    double drift = (mu * sentiment - 0.5 * sigma * sigma) * dt;
    double vol_sqrt_dt = sigma * std::sqrt(dt);

    // Open CSV file and write header
    std::ofstream csv_file(ticker + std::string("_stock_prices.csv"));
    csv_file << "Time,Ticker,Price\n";
    csv_file << "0," << ticker << "," << S0 << "\n"; // Write initial price

    // Loops through the points to generate the stock prices using the GBM and ito
    for (int i = 1; i < points; ++i) {
        double Z = dis(gen);
        double price = prices[i - 1] * std::exp(drift + vol_sqrt_dt * Z);
        prices.push_back(price);
        csv_file << i << "," << ticker << "," << price << "\n"; // Write immediately
    }
    
    csv_file.close();

    // Create the AI with provided difficulty
    AI ai(S0, mu, sigma, sentiment, ticker, difficulty, numericSeed);
    // Since seperating the logic from the constructor, we need to call the run function to execute the AI run
    ai.run();

    // Write the predicted prices to console for debugging
    std::cout << "Generated " << ticker << "_stock_prices.csv and " << ticker << "_predicted_prices.csv\n";
    return 0;
}