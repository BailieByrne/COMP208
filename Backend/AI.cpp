#include "AI.h"
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

/**
 * Constructor implementation
 * IMPORTANT: Remove the logic from the constructor to avoid heavy computation
 */
AI::AI(double S0, double mu, double sigma, double sentiment, std::string ticker, int difficulty, std::uint64_t seedBase) {
        /**
        AI will be passed the params to run monte carlo sim
        //Dependant on its difficulty is how many fixed points it will know.
        //The AI will then predict its own graph an optimise for the greatest deltas for price gain
        //OPTIONAL: Add a holding system if the price is relativley low towards the end of the day, it will hold and wait for a price increase.
        **/

        //Local Class var declarations.
        //Here we are going to skew by +- 5% to make the AI less accurate,
        //This is a choice however this will break the reproducibility of the AI, so I will add a seed based random generator to generate the skew values so at least the same seed will produce the same results even with the skew.
        std::mt19937 gen(static_cast<std::mt19937::result_type>(seedBase ^ 0x8F4A7C159D3B2E61ULL));
        //XOR to mix the seed and remove patterns while staying deterministic, the constant is just a random large number I picked.
        std::uniform_real_distribution<double> dist(0.95, 1.05);
        this->S0 = S0;
        this->mu = mu * dist(gen); 
        //Skew the drift by +-5% to make the AI less accurate and it will  make the game more fun and challenging
        this ->sigma = sigma * dist(gen); 
        //forgot to declare sigma resutling in garbage values and NANs

        this->sentiment = sentiment;
        this->ticker = ticker;
        this->difficulty = difficulty;
        this->seedBase = seedBase;
        Known_Points.push_back(std::make_tuple(0, S0)); 
        //The first point is always known as its the starting price.
        //Plus the first point is needed for brownian bridge segmentation.


        //Extract Known_Points from difficulty
        //Accidentally made it to accurate, even with 3 points its quite accurate
        switch(difficulty){
            case 1:
                Known_Points_Amount = 3;
                MonteCarloRuns = 1000;
                tradingOperations = 8;
                searchWindow = 20;
                break;
            case 2:
                Known_Points_Amount = 5;
                MonteCarloRuns = 2000;
                tradingOperations = 12;
                searchWindow = 15;
                break;
            case 3:
                Known_Points_Amount = 10;
                MonteCarloRuns = 5000;
                tradingOperations = 16;
                searchWindow = 10;
                break;
                break;
            case 4:
                Known_Points_Amount = 20;
                MonteCarloRuns = 10000;
                tradingOperations = 14;
                searchWindow = 5;
                break;
            default:
                Known_Points_Amount = 3;
                MonteCarloRuns = 1000;  
                tradingOperations = 8;   
                searchWindow = 20;      
        };
        
    } 

/**
 * Logic Seperated from constructor
 */
void AI::run() {
        getKnownPoints(); //Populate the known points.

        //need the final price for the brownian bridge:
        // Known_Points.push_back(std::make_tuple(509, final_price)); 
        
        std::cout << "AI constructor entry: S0=" << S0 << " mu=" << mu 
              << " sigma=" << sigma << " sentiment=" << sentiment << "\n";
       
        //Ive scaled the monte carlo runs, i added mean fusing to preserver the overall shape of the monte carlo mean but added some randomness by fusing it with a random path and also taking the median path to prevent outliers dominating the prediction, this should give us a more realistic predicted path
        MonteCarloSimulate(MonteCarloRuns); 
        //Higher difficulty is higher computation, on my system it can run 8 full graphs + AI in under 1 second which is plenty fast
        //More runs is smoother line
        //now store the prediction in a CSV
        writeToCSV(); 
        writeSignalsToCSV(); //Write the trade signals to a separate CSV for the UI to read and display
}

/**
 * IMPORTANT:
 * THIS OPERATES IN LOG SPACE PRICES MUST BE log(price) 
 * This is for the linear Guassian process
 */
void AI::getBrownianBridgeSegment(std::vector<double>& X,int t_start,int t_end,double X_start,double X_end,double alpha,double sigma,double dt,std::mt19937& gen){
            //gen the normal distribution
            std::normal_distribution<> dis(0.0, 1.0);

            //Get the steps between the time frame
            int steps = t_end - t_start;

            //total continous time
            double DeltaT = steps * dt;

            /**
             * Tau is the intermediate time between t_start and t_end, we will iterate through the time steps and calculate the mean and variance of the brownian bridge at each intermediate time step, then sample from the normal distribution to get the value of X at that time step.
             */
            for (int i = 1; i< steps; i++){

                //Getting index
                int t = t_start + i;

                //time since start of segment
                double tau = i * dt;

                //Calc the mean and variance of the brownian bridge at time t
                double mean = X_start + alpha * tau + (tau / DeltaT) * (X_end - X_start - alpha * DeltaT);
                double variance = sigma * sigma * tau * (1.0 - tau / DeltaT);
                
                //get the standard deviation
                double stddev = std::sqrt(variance);

                //Sample it from the distribution
                X[t] = mean + stddev * dis(gen);
            }

            //Enforce the know points exist in the segment
            X[t_start] = X_start;
            X[t_end] = X_end;
        }

void AI::getKnownPoints() {
    std::ifstream csv_file(this->ticker + std::string("_stock_prices.csv"));
        if (!csv_file.is_open()) {
            std::cerr << "Failed to open " << this->ticker << "_stock_prices.csv\n";
            return;
        }

        // read all rows
        std::vector<std::tuple<int, double>> all_points;
        std::string line;

        // Skip header
        std::getline(csv_file, line);

        while (std::getline(csv_file, line)) {
            std::stringstream ss(line);
            //Extract Header values from CSV
            std::string time_str, ticker_str, price_str;
            std::getline(ss, time_str, ',');
            std::getline(ss, ticker_str, ',');
            std::getline(ss, price_str, ',');

            //Stoid and stod can throw errors so wrap them
            try {
                int time = std::stoi(time_str);
                double price = std::stod(price_str);
                all_points.emplace_back(time, price);
            } catch (const std::exception& e) {
                std::cerr << "Error parsing line: " << line << " - " << e.what() << "\n";
            }
        }

        //Close file for safety
        csv_file.close();

        // Create index vector 0–509 (510 total points)
        std::vector<int> indices(all_points.size());
        std::iota(indices.begin(), indices.end(), 0);

        // Shuffle indices
        std::mt19937 gen(static_cast<std::mt19937::result_type>(this->seedBase ^ 0x9E3779B97F4A7C15ULL));
        std::shuffle(indices.begin(), indices.end(), gen);

        // Take first Known_Points_Amount entries
        // THis also prevents dupplicates as were taking the first n entries
        for (int i = 0; i < Known_Points_Amount; i++) {
            Known_Points.push_back(all_points[indices[i]]);
        }

        // Then sort the vector using a lambda to have the points in chronological time order
        std::sort(Known_Points.begin(), Known_Points.end(), [](const std::tuple<int, double>& a, const std::tuple<int, double>& b) {
            return std::get<0>(a) < std::get<0>(b);
        });

        /**
         * OPTIONAL: Sort in place to increase efficiency, (100 points max is negligible but good practice)
         */
}

void AI::MonteCarloSimulate(int runs)
{
    assert(runs > 0);

    const int P = this->points;

    // detect how many threads we can use
    int numThreads = std::thread::hardware_concurrency();
    if (numThreads == 0)
        numThreads = 4; // fallback just incase

    int runsPerThread = runs / numThreads;
    int remainingRuns = runs % numThreads;

    // instead of storing full paths
    // we store values per time step
    // this is way more cache freindly
    std::vector<std::vector<double>> valuesPerTime(P, std::vector<double>(runs));

    // global accumulators for mean and variance
    std::vector<double> globalSum(P, 0.0);
    std::vector<double> globalSumSq(P, 0.0);

    std::vector<std::thread> threads;
    std::mutex mergeMutex; // only used once per thread (very low contention)

    auto worker = [&](int startIndex, int threadRuns)
    {
        // local accumulators so threads dont fight eachother
        std::vector<double> localSum(P, 0.0);
        std::vector<double> localSumSq(P, 0.0);

        std::mt19937 gen(static_cast<std::mt19937::result_type>(this->seedBase ^ static_cast<std::uint64_t>(startIndex + 1) ^ 0xA5A5A5A5ULL));

        for (int r = 0; r < threadRuns; r++)
        {
            int globalIndex = startIndex + r;

            // generate one monte carlo path
            std::vector<double> path = this->predictGraph(gen);

            for (int t = 0; t < P; t++)
            {
                double price = path[t];

                // store value directly (no push_back, no resize)
                valuesPerTime[t][globalIndex] = price;

                localSum[t] += price;
                localSumSq[t] += price * price;
            }
        }

        // merge once at the end (way faster then locking per run)
        std::lock_guard<std::mutex> lock(mergeMutex);
        for (int t = 0; t < P; t++)
        {
            globalSum[t] += localSum[t];
            globalSumSq[t] += localSumSq[t];
        }
    };

    int currentStart = 0;

    for (int i = 0; i < numThreads; i++)
    {
        int threadRuns = runsPerThread + (i < remainingRuns ? 1 : 0);

        threads.emplace_back(worker, currentStart, threadRuns);

        currentStart += threadRuns;
    }

    for (auto& thread : threads)
        thread.join();

    // now compute mean + stddev
    MonteCarloMean.assign(P, 0.0);
    MonteCarloStdDev.assign(P, 0.0);

    for (int t = 0; t < P; t++)
    {
        double mean = globalSum[t] / runs;
        double variance = (globalSumSq[t] / runs) - (mean * mean);

        if (variance < 0.0)
            variance = 0.0; // floating point saftey

        assert(variance >= 0.0);
        MonteCarloMean[t] = mean;
        MonteCarloStdDev[t] = std::sqrt(variance);
    }

    // compute median path
    std::vector<double> medianPath(P);

    for (int t = 0; t < P; t++)
    {
        auto& vec = valuesPerTime[t];

        std::nth_element(vec.begin(),
                         vec.begin() + vec.size() / 2,
                         vec.end());

        double median = vec[vec.size() / 2];

        if (vec.size() % 2 == 0)
        {
            std::nth_element(vec.begin(),
                             vec.begin() + vec.size() / 2 - 1,
                             vec.end());

            median = 0.5 * (median + vec[vec.size() / 2 - 1]);
        }

        medianPath[t] = median;
    }

    // pick a random path index to blend
    std::mt19937 gen(static_cast<std::mt19937::result_type>(this->seedBase ^ 0xC3A5C85C97CB3127ULL));
    std::uniform_int_distribution<int> dist(0, runs - 1);

    int randomIndex = dist(gen);

    Predicted_Prices.resize(P);

    for (int t = 0; t < P; t++)
    {
        double randomVal = valuesPerTime[t][randomIndex];

        // weighted blend of median + random path 50/50 to preserve overall shape but add stochastic noise
        Predicted_Prices[t] =
            0.5 * medianPath[t] +
            0.5 * randomVal;
    }
}

std::vector<double> AI::predictGraph(std::mt19937& gen){
    const int points = 510;
        assert(points > 1);

        //Need to do this redundant cast to keep DT a float
        double dt = 1.0 / static_cast<double>(points);

        //Now log space drift
        double mu_eff = this->mu * this->sentiment;
        double alpha = mu_eff - 0.5 * this->sigma * this->sigma;


        //Use the local copy
        const auto& localKnownPoints = Known_Points;

        //now convert known prices to log space for brownian parsing
        std::vector<std::pair<int,double>> logKnown;
        logKnown.reserve(localKnownPoints.size());
        for (const auto& kp : localKnownPoints){
            int time = std::get<0>(kp);
            double price = std::get<1>(kp);
            //LOG CONVERSION HERE
            double log_price = std::log(price);
            assert(std::isfinite(log_price));
            //Seperate the log conversion from the emplace to help debug
            logKnown.emplace_back(time, log_price);
        }

        //Create the full log path
        std::vector<double> X(points);
        //Now generate brownian segemnts using func
        for (size_t i = 0; i < logKnown.size() - 1; i++){
            //Time
            int t_start = logKnown[i].first;
            int t_end = logKnown[i+1].first;

            //Price
            double X_start = logKnown[i].second;
            double X_end = logKnown[i+1].second;

            //Get bridge segment:
            this->getBrownianBridgeSegment(
                X,
                t_start,
                t_end,
                X_start,
                X_end,
                alpha,
                this->sigma,
                dt,
                gen
            );
        }

        //Removed the final anchor as the graph was too preicitve
        //This allows thhe end of the graph to predict the close as knowing the end price makes the AI too good.
        //Adds more realism as the AI has to predict the close without knowing it, just like in real life.
        int last_time = logKnown.back().first;
        if (last_time < points - 1) {
            double X_start = logKnown.back().second;
            std::normal_distribution<> dis(0.0, 1.0);
            for (int t = last_time + 1; t < points; t++) {
                double dW = std::sqrt(dt) * dis(gen);
                double dX = alpha * dt + this->sigma * dW;
                X[t] = X[t-1] + dX;

            }
        }

        //Convert the prices back from log space into a local path
        std::vector<double> path;
        path.reserve(points);

        //Push the converted log prices back into normal space to get the final predicted path
        for (int i = 0; i < points; i++){
            double converted = std::exp(X[i]);
            path.push_back(converted);
        }

        return path;
    }

    /**
     * Self Explanatory function
     */
    void AI::writeToCSV(){
        std::ofstream csv_file(this->ticker + std::string("_predicted_prices.csv"));
        csv_file << "Time,Ticker,Price\n";

        for (size_t i = 0; i < Predicted_Prices.size(); ++i) {
            csv_file << i << "," << this->ticker << "," << Predicted_Prices[i] << "\n";
        }
        csv_file.close();
        std::cout << "Done";
    }

    void AI::writeSignalsToCSV() {
        std::cout << "\n=== AI Signals Debug ===" << std::endl;
        std::cout << "Ticker: " << this->ticker << std::endl;
        std::cout << "Predicted_Prices.size() = " << Predicted_Prices.size() << std::endl;
        std::cout << "Difficulty: " << difficulty << std::endl;
        std::cout << "Trading Operations: " << tradingOperations << std::endl;
        
        if (Predicted_Prices.empty()) {
            std::cerr << "ERROR: Predicted_Prices is empty! Skipping signal generation.\n";
            return;
        }
        
        // Debug: show first and last prices
        std::cout << "First price: " << Predicted_Prices[0] << ", Last price: " << Predicted_Prices.back() << std::endl;
        
        int window = searchWindow;
        std::cout << "Window size: " << window << std::endl;
        std::cout << "Search range: " << window << " to " << (Predicted_Prices.size() - window) << std::endl;
        
        std::vector<AISignal> signals = getTradeSignals(Predicted_Prices);
        std::cout << "Found " << signals.size() << " signals\n";

        std::ofstream csv_file(this->ticker + std::string("_ai_signals.csv"));
        if (!csv_file.is_open()) {
            std::cerr << "Failed to open " << this->ticker << "_ai_signals.csv\n";
            return;
        }

        csv_file << "TimeIdx,Ticker,Action,PredictedPrice,Magnitude,Fraction\n";

        for (const AISignal& signal : signals) {
            double fraction = getKellyFraction(Predicted_Prices, signal.timeIdx, signal.type);

            csv_file << signal.timeIdx << ","
                     << this->ticker << ","
                     << (signal.type == AISignal::BUY ? "BUY" : "SELL") << ","
                     << signal.predictedPrice << ","
                     << signal.magnitude << ","
                     << fraction << "\n";
        }

        csv_file.close();
        std::cout << "Signals written to " << this->ticker << "_ai_signals.csv\n";
        std::cout << "==================\n\n";
    }


/** 
 * Uses the well known kelly criterion to determine the optimal bet to trade
 * This fits nicely in logspace with the GBM as a design choice
 * k controls steepness, higher difficulty = steeper curve = more aggressive sizing.
 * This lets the AI scale the buy and sells relative to the difficulty and signal strength.
 */
double AI::getKellyFraction(std::vector<double>& predictedPrices, int timeIdx, AISignal::Action signalType) {
    int window = searchWindow;

    int lookAhead = std::min(timeIdx + window, (int)predictedPrices.size() - 1);

    double priceNow = predictedPrices[timeIdx];
    double priceLater = predictedPrices[lookAhead];

    //Stops /0 issues
    if (priceNow <= 0.0) {
        return 0.0;
    };

    // Expected return over the next index from lookahead
    double expectedReturn = std::abs(priceLater - priceNow) / priceNow;

    // Use price level stddev as the risk measure instead of log return variance
    std::vector<double> windowPrices;
    for (int i = timeIdx; i <= lookAhead; i++) {
        windowPrices.push_back(predictedPrices[i]);
    }

    // Calculate mean and variance of the prices in the window
    double meanP = std::accumulate(windowPrices.begin(), windowPrices.end(), 0.0) / windowPrices.size();
    double variance = 0.0;
    for (double p : windowPrices) {
        variance += (p - meanP) * (p - meanP);
    }
    variance /= windowPrices.size();

    // comparable to the expectedReturn percentage
    double normalisedVariance = variance / (meanP * meanP);

    // Floor at a realistic intraday volatility level (0.5% per window)
    // prevents kelly saturating the sigmoid on smooth predicted paths
    normalisedVariance = std::max(normalisedVariance, 0.0025);

    //Guard for variance which killes kelly values.
    if (normalisedVariance < 1e-8){
        return 0.0;
    }

    double kelly = expectedReturn / normalisedVariance;


    // sigmoid activation maps kelly into (-1 - 1) smoothly.
    double k;
    switch (difficulty) {
        case 1: k = 0.8;  break;
        case 2: k = 1.0; break;
        case 3: k = 1.5;  break;
        case 4: k = 1.75; break;
        default: k = 0.8;
    }

    /**
     * From testign kelly ranges where between 4-25 which was saturating the sigmoid hence the division to scale it down.
     */
    kelly /= 25.0;

    //Sigmoid activation bounds between 1 and -1
    kelly = (2.0 / (1.0 + std::exp(-k * kelly))) - 1.0;

    // Direction from signal type, kelly is purely position sizing magnitude
    if (signalType == AISignal::SELL) kelly = -kelly;

    // Sigmoid already bounds to (-1, 1) but assert for safety
    assert(kelly <= 1.0 && kelly >= -1.0);
    return kelly;
}
    

/**
 * Main fucn to read the predicted prices and generate the trades based on the signal
 */
std::vector<AISignal> AI::getTradeSignals(const std::vector<double>& predictedPrices) {
    std::vector<AISignal> allSignals;
    int sizeOfPredicted = predictedPrices.size();

    //Use predefiend window
    int window = searchWindow;

    /**
     * Main loop to find the min an max points in the predicted prices, check that we arent self comapring too.
     */
    for (int i = window; i < sizeOfPredicted - window; i++) {
        double currentPrice = predictedPrices[i];
        bool isMin = true;
        bool isMax = true;

        for (int j = i - window; j <= i + window; j++) {
            if (j == i) continue;
            if (predictedPrices[j] < currentPrice) isMin = false;
            if (predictedPrices[j] > currentPrice) isMax = false;
        }

        // Skip if the current price is the same as the previous price to avoid generating signals on flat lines
        if (std::abs(currentPrice - predictedPrices[i - 1]) < 1e-6){
            continue;
        }

        //Sets the sell
        if (isMin)
         {
            allSignals.push_back({i, AISignal::BUY,  currentPrice, 0.0});
        }
        else if (isMax){
            allSignals.push_back({i, AISignal::SELL, currentPrice, 0.0});
        }
    }

    // Step 1: Enforce alternation on the full chronological list first
    std::vector<AISignal> interleaved;
    AISignal::Action expected = AISignal::BUY;

    //Could optiimise dbut upon testing we had strings of buys and sells so forcing it to be interleaved then sort by magn.
    for (int i = 0; i < (int)allSignals.size(); i++) {
        if (allSignals[i].type == expected) {
            interleaved.push_back(allSignals[i]);

            //Ternary operation to flip expected type for next signal
            expected = (expected == AISignal::BUY) ? AISignal::SELL : AISignal::BUY;
        }
    }

    //I had an error where the parity for the kelly was off so this is a check to ensure the right size.
    // Pairs are guaranteed BUY->SELL so i and i+1 are always a valid pair
    for (int i = 0; i + 1 < (int)interleaved.size(); i++) {
        double delta = std::abs(interleaved[i + 1].predictedPrice - interleaved[i].predictedPrice);
        interleaved[i].magnitude = delta;
        interleaved[i + 1].magnitude = delta;
    }

    //this grousp into BUY/SELL pairs, sort by magnitude descending, keep top N pairs
    std::vector<std::pair<AISignal, AISignal>> pairs;
    for (int i = 0; i + 1 < (int)interleaved.size(); i += 2) {
        pairs.push_back({interleaved[i], interleaved[i + 1]});
    }

    //thansk COMP282 for the stable_sort reccomendation
    std::stable_sort(pairs.begin(), pairs.end(), [](const std::pair<AISignal,AISignal>& a, const std::pair<AISignal,AISignal>& b) {
        return a.first.magnitude > b.first.magnitude;
    });

    //This ensures theires enoiugh pairs so there isnt hanging buy or sells, and also has the operation limit as a cap.
    int maxPairs = tradingOperations / 2;
    if ((int)pairs.size() > maxPairs) pairs.resize(maxPairs);
    std::vector<AISignal> result;
    for (int i = 0; i < (int)pairs.size(); i++) {
        result.push_back(pairs[i].first);
        result.push_back(pairs[i].second);
    }

    //One final sort for chronology to ensure theyre in the right order.
    std::stable_sort(result.begin(), result.end(), [](const AISignal& a, const AISignal& b) {
        return a.timeIdx < b.timeIdx;
    });

    return result;
}
