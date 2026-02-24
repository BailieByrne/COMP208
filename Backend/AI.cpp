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


class AI {
    //Wrap these in priv/pub
    const int points = 510; // 510 minutes in a trading day (8:00 to 16:30)
    double S0;
    double mu;
    double sigma;
    double sentiment;
    char* STOCK;
    int difficulty;
    double final_price;
public:
/**
 * IMPORTANt:
 * Remove the logic from the constructor to avoid heavy computation
 */
    AI(double S0, double mu, double sigma, double sentiment, char* STOCK, int difficulty) {
        /**
        AI will be passed the params to run monte carlo sim
        //Dependant on its difficulty is how many fixed points it will know.
        //The AI will then predict its own graph an optimise for the greatest deltas for price gain
        //OPTIONAL: Add a holding system if the price is relativley low towards the end of the day, it will hold and wait for a price increase.
        assert(difficulty >= 1 && difficulty <= 4, "Difficulty must be between 1 and 4");
        **/
        //Local Class var declarations.
        this->S0 = S0;
        this->mu = mu;
        this->sigma = sigma;
        this->sentiment = sentiment;
        this->STOCK = STOCK;
        this->difficulty = difficulty;
        this->final_price = final_price;
        Known_Points.push_back(std::make_tuple(0, S0)); 
        //The first point is always known as its the starting price.
        //Plus the first point is needed for brownian bridge segmentation.

        //Extract Known_Points from difficulty
        switch(difficulty){
            case 1:
                Known_Points_Amount = 3;
                MonteCarloRuns = 1000;
                break;
            case 2:
                Known_Points_Amount = 5;
                MonteCarloRuns = 2000;
                break;
            case 3:
                Known_Points_Amount = 10;
                MonteCarloRuns = 5000;
                break;
            case 4:
                Known_Points_Amount = 50;
                MonteCarloRuns = 10000;
                break;
            default:
                Known_Points_Amount = 1;
                MonteCarloRuns = 1000;           
        };
        getKnownPoints(); //Populate the known points.

        //need the final price for the brownian bridge:
        // Known_Points.push_back(std::make_tuple(509, final_price)); 
        

        /**
         * WRAP THIS IN THE MONTE CARLO
         */

         //Outdated 1 pass, L2 error of 150-650
        MonteCarloSimulate(MonteCarloRuns); //Run the monte carlo sim with 1000 runs,
        //More runs is smoother line
        //now store the prediction in a CSV
        writeToCSV();

        
    }


    /**
     * IMPORTANT:
     * THIS OPERATES IN LOG SPACE PRICES MUST BE log(price) 
     * This is for the linear Guassian process
     */
    void getBrownianBridgeSegment(std::vector<double>& X,int t_start,int t_end,double X_start,double X_end,double alpha,double sigma,double dt,std::mt19937& gen){
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

    void getKnownPoints() {
        std::ifstream csv_file("stock_prices.csv");
        if (!csv_file.is_open()) {
            std::cerr << "Failed to open stock_prices.csv\n";
            return;
        }

        // Read all rows (skip header)
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

            int time = std::stoi(time_str);
            double price = std::stod(price_str);

            all_points.emplace_back(time, price);
        }

        csv_file.close();

        // Create index vector 0–509
        std::vector<int> indices(all_points.size());
        std::iota(indices.begin(), indices.end(), 0);

        // Shuffle indices
        std::random_device rd;
        std::mt19937 gen(rd());
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

void MonteCarloSimulate(int runs)
{
    assert(runs > 0);

    const int P = this->points;

    // determine thread count
    unsigned int numThreads = std::thread::hardware_concurrency();
    if (numThreads == 0)
        numThreads = 4;

    int runsPerThread = runs / numThreads;
    int remainingRuns = runs % numThreads;

    // accumulate stats for mean / std
    std::vector<double> globalSum(P, 0.0);
    std::vector<double> globalSumSq(P, 0.0);

    // store every simulated path
    AllMonteCarloPaths.clear();
    AllMonteCarloPaths.reserve(runs);

    //threads
    std::vector<std::thread> threads;
    std::mutex mergeMutex;
    std::mutex pathMutex;

    auto worker = [&](int threadRuns)
    {
        std::vector<double> localSum(P, 0.0);
        std::vector<double> localSumSq(P, 0.0);

        std::mt19937 gen(std::random_device{}());

        for (int r = 0; r < threadRuns; r++)
        {
            // generate one full stochastic path
            std::vector<double> path = this->predictGraph(gen);

            // store it for later median / randomness blending
            {
                std::lock_guard<std::mutex> lock(pathMutex);
                AllMonteCarloPaths.push_back(path);
            }

            // accumulate statistics
            for (int t = 0; t < P; t++)
            {
                double price = path[t];
                localSum[t] += price;
                localSumSq[t] += price * price;
            }
        }

        // merge thread-local stats safely
        {
            std::lock_guard<std::mutex> lock(mergeMutex);

            for (int t = 0; t < P; t++)
            {
                globalSum[t] += localSum[t];
                globalSumSq[t] += localSumSq[t];
            }
        }
    };

    // launch worker threads
    for (unsigned int i = 0; i < numThreads; i++)
    {
        int threadRuns = runsPerThread + ((int)i < remainingRuns ? 1 : 0);
        threads.emplace_back(worker, threadRuns);
    }

    for (auto &thread : threads)
        thread.join();

    // compute mean and standard deviation
    MonteCarloMean.assign(P, 0.0);
    MonteCarloStdDev.assign(P, 0.0);

    for (int t = 0; t < P; t++)
    {
        double mean = globalSum[t] / runs;
        double variance = (globalSumSq[t] / runs) - (mean * mean);

        if (variance < 0.0)
            variance = 0.0;

        MonteCarloMean[t] = mean;
        MonteCarloStdDev[t] = std::sqrt(variance);
    }

    // -----------------------------
    // compute median path
    // -----------------------------
    std::vector<double> medianPath(P, 0.0);

    if (!AllMonteCarloPaths.empty())
    {
        size_t numPaths = AllMonteCarloPaths.size();

        for (int t = 0; t < P; t++)
        {
            std::vector<double> values;
            values.reserve(numPaths);

            // collect values at time t from all simulated paths
            for (size_t r = 0; r < numPaths; r++)
                values.push_back(AllMonteCarloPaths[r][t]);

            // partial sort to get median efficiently
            std::nth_element(values.begin(),
                             values.begin() + values.size() / 2,
                             values.end());

            double median = values[values.size() / 2];

            // if even number of samples, average the two middle values
            if (values.size() % 2 == 0)
            {
                std::nth_element(values.begin(),
                                 values.begin() + values.size() / 2 - 1,
                                 values.end());

                double lower = values[values.size() / 2 - 1];
                double upper = median;

                median = 0.5 * (lower + upper);
            }

            medianPath[t] = median;
        }
    }

    // -----------------------------
    // blend median with one random path
    // -----------------------------
    std::vector<double> randomPath;

    if (!AllMonteCarloPaths.empty())
    {
        std::mt19937 gen(std::random_device{}());
        std::uniform_int_distribution<size_t> dist(
            0, AllMonteCarloPaths.size() - 1);

        size_t idx = dist(gen);
        randomPath = AllMonteCarloPaths[idx];
    }
    else
    {
        randomPath.assign(P, 0.0);
    }

    // final output path
    Predicted_Prices.resize(P);

    for (int t = 0; t < P; t++)
    {
        Predicted_Prices[t] =
            0.7 * medianPath[t] +
            0.3 * randomPath[t];
    }
}
    std::vector<double> predictGraph(std::mt19937& gen){
        const int points = 510;
        assert(points > 1);

        //Need to do this redundant cast to keep DT a float
        double dt = 1.0 / static_cast<double>(points);

        //Now log space drift
        double mu_eff = this->mu * this->sentiment;
        double alpha = mu_eff - 0.5 * this->sigma * this->sigma;

        //Randomness uses the provided generator

        //Use the local copy
        std::vector<std::tuple<int,double>> localKnownPoints = Known_Points;

        //now convert known prices to log space for brownian parsing
        std::vector<std::pair<int,double>> logKnown;
        for (const auto& kp : localKnownPoints){
            int time = std::get<0>(kp);
            double price = std::get<1>(kp);
            //LOG CONVERSION HERE
            double log_price = std::log(price);
            assert(std::isfinite(log_price));
            assert(log_price > 0);
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

        int last_time = logKnown.back().first;
        if (last_time < points - 1) {
            double X_start = logKnown.back().second;
            for (int t = last_time + 1; t < points; t++) {
                std::normal_distribution<> dis(0.0, 1.0);
                double dW = std::sqrt(dt) * dis(gen);
                double dX = alpha * dt + this->sigma * dW;
                X[t] = X[t-1] + dX;

            }
        }

        //Convert the prices back from log space into a local path
        std::vector<double> path;
        path.reserve(points);

        for (int i = 0; i < points; i++){
            double converted = std::exp(X[i]);
            path.push_back(converted);
        }

        return path;
    }

    void writeToCSV(){
        std::ofstream csv_file("predicted_prices.csv");
        csv_file << "Time,Ticker,Price\n";

        for (size_t i = 0; i < Predicted_Prices.size(); ++i) {
            csv_file << i << "," << STOCK << "," << Predicted_Prices[i] << "\n";
        }
        csv_file.close();
        std::cout << "Done";
    }
private:
    std::vector<double> Predicted_Prices;
    int Known_Points_Amount = 0; //Default ammount
    int MonteCarloRuns = 100; //Default ammount of monte carlo runs, can be changed for more accuracy but more runtime
    std::vector<std::tuple<int, double>> Known_Points; //Vector of tuples to store the known points (time, price)
    std::vector<double> MonteCarloMean;
    std::vector<double> MonteCarloStdDev;
    std::vector<std::vector<double>> AllMonteCarloPaths; //Store all paths to compute median
};


