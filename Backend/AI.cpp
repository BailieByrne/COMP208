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
public:
    const int points = 510; // 510 minutes in a trading day (8:00 to 16:30)
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
        //Accidentally made it to accurate, even with 3 points its quite accurate
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
        
    } 
        
    /**
     * Logic Seperated from constructor
     */
    void run(){
        getKnownPoints(); //Populate the known points.

        //need the final price for the brownian bridge:
        // Known_Points.push_back(std::make_tuple(509, final_price)); 
        
        //Ive scaled the monte carlo runs, i added mean fusing to preserver the overall shape of the monte carlo mean but added some randomness by fusing it with a random path and also taking the median path to prevent outliers dominating the prediction, this should give us a more realistic predicted path.
        MonteCarloSimulate(MonteCarloRuns); 
        //Higher difficulty is higher computation, on my system it can run 8 full graphs + AI in under 1 second which is plenty fast
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

        // Read all rows
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

        std::mt19937 gen(std::random_device{}());

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
    std::mt19937 gen(std::random_device{}());
    std::uniform_int_distribution<int> dist(0, runs - 1);

    int randomIndex = dist(gen);

    Predicted_Prices.resize(P);

    for (int t = 0; t < P; t++)
    {
        double randomVal = valuesPerTime[t][randomIndex];

        // weighted blend of median + random path
        Predicted_Prices[t] =
            0.7 * medianPath[t] +
            0.3 * randomVal;
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
    std::vector<std::vector<double>> AllMonteCarloPaths;
    double S0;
    double mu;
    double sigma;
    double sentiment;
    char* STOCK;
    int difficulty;
    double final_price; //Store all paths to compute median
};


