# Monte Carlo Testing of the Engine

## Goal

The C++ engine runs once and generates a `predicted_prices.csv` file.

The purpose of this testing harness is to:

- Run the engine multiple times (e.g. 50+ runs)
- Store each run in a separate folder
- Allow statistical analysis of the results
- Support edge-case testing
- Enable aggregation (mean, variance, confidence bands)

Example output structure:

outputs/run_0001/predicted_prices.csv  
outputs/run_0002/predicted_prices.csv  

---

## Why This Matters

Monte Carlo simulation is stochastic.  
Running the engine once is not sufficient for evaluation.

We must:
- Run it many times
- Average results
- Analyse variance
- Detect instability or edge-case failure

---

## Next Steps

- Implement `run_mc.py` to automate repeated execution
- Implement `analysis.py` to aggregate results
- Implement `plots.py` to visualise outputs
- Implement `edge_cases.py` to test failure scenarios
