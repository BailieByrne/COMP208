#pragma once
#include <iostream>
#include <cassert>
#include <fstream>
#include <vector>
#include <tuple>
#include <algorithm>
#include <random>
#include <numeric>
#include <string>
#include <sstream>
#include <cmath>
#include <thread>
#include <mutex>
#include <cstdint>

struct AISignal {
    int timeIdx;
    enum Action { BUY, SELL } type;
    double predictedPrice;
    double magnitude; // How strong the signal is
};

class AI {
public:
    const int points = 510; // 510 minutes in a trading day (8:00 to 16:30)

    AI(double S0, double mu, double sigma, double sentiment, std::string ticker, int difficulty, std::uint64_t seedBase);

    void run();

private:
    std::vector<double> Predicted_Prices;
    int Known_Points_Amount = 0;                            //Default amount
    int MonteCarloRuns = 100;                               //Default amount of monte carlo runs, can be changed for more accuracy but more runtime
    std::vector<std::tuple<int, double>> Known_Points;      //Vector of tuples to store the known points (time, price)
    std::vector<double> MonteCarloMean;
    std::vector<double> MonteCarloStdDev;
    std::vector<std::vector<double>> AllMonteCarloPaths;
    double S0;
    double mu;
    double sigma;
    double sentiment;
    std::string ticker;
    int difficulty;
    std::uint64_t seedBase = 0;     //Default seed 0 as the base
    double final_price = 0.0;       //Default to 0 — was uninitialized causing UB corrupting adjacent members
    int tradingOperations = 8;      //Default diff 1
    int searchWindow = 20;          //Default window for signal detection, also affected by difficulty

    // Private methods
    void getBrownianBridgeSegment(std::vector<double>& X, int t_start, int t_end, double X_start, double X_end, double alpha, double sigma, double dt, std::mt19937& gen);
    void getKnownPoints();
    void MonteCarloSimulate(int runs);
    std::vector<double> predictGraph(std::mt19937& gen);
    void writeToCSV();
    void writeSignalsToCSV();
    double getKellyFraction(std::vector<double>& predictedPrices, int timeIdx, AISignal::Action signalType);
    std::vector<AISignal> getTradeSignals(const std::vector<double>& predictedPrices);
};