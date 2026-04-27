# COMP208: GBM Stock Price Simulator with Brownian Bridge AI

> A C++ Monte Carlo stock price simulator using Geometric Brownian Motion in log-space with Itô correction, Brownian Bridge path conditioning, and a multithreaded AI prediction engine, built as part of a second-year university group project and risk benchmarking tool.

---

## Overview

This project simulates intraday stock price paths over a full trading day (510 minutes, 08:00–16:30) using **Geometric Brownian Motion (GBM)** the same stochastic model underpinning the [Black-Scholes](https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=&cad=rja&uact=8&ved=2ahUKEwiHn7vJ7OqTAxXFaUEAHXy2MpAQFnoECBgQAQ&url=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FBlack%25E2%2580%2593Scholes_model&usg=AOvVaw3Y3P-LHLGDz-OHGgwisPKx&opi=89978449) options pricing framework.

On top of the raw simulation, an AI prediction engine uses **Brownian Bridge interpolation** to condition Monte Carlo paths on partially observed price data, modelling the real-world information asymmetry a trader faces during a live session. The difficulty system controls how many price points the AI knows, creating a tunable benchmark for testing trading strategies against an opponent with varying levels of information.

---

## The Maths

### Geometric Brownian Motion

Stock prices are modelled as a GBM process, described by the stochastic differential equation:

```
dS = μS dt + σS dW
```

Where:
- `S` — current stock price
- `μ` — drift (expected annualised return)
- `σ` — volatility
- `dW` — Wiener process increment (normally distributed noise)

#### Why Log-Space?

Rather than simulating prices directly, the implementation works in log-space throughout. Taking `X = log(S)`, Itô's lemma gives the exact discrete update:

```
X(t + dt) = X(t) + (μ - ½σ²)dt + σ√dt · Z,   Z ~ N(0,1)
```

The `(μ - ½σ²)` term is the **Itô correction** — without it, naive discretisation introduces a systematic upward bias in expected price, this project handles it correctly.

Converting back: `S(t) = exp(X(t))`, which guarantees prices remain strictly positive thanks to exponetiation.

#### Sentiment Scaling

A `sentiment` multiplier scales the drift term:

```
μ_eff = μ × sentiment
```

This allows the simulation to model bullish (`sentiment > 1`) or bearish (`sentiment < 1`) market conditions beyond the base drift, enabling more realistic scenario testing, also for gameplay mechanics allows control over events occuring in the game, or to simualte black-swan events.

---

### Brownian Bridge

The AI engine does not predict a completely random path. Instead, it uses **Brownian Bridge interpolation** to condition each Monte Carlo path on known anchor points, these are prices at specific timestamps that the AI has been given access to.

For a bridge between known points `(t_start, X_start)` and `(t_end, X_end)`, the conditional distribution at intermediate time `t` is Gaussian with:

```
mean(τ)     = X_start + α·τ + (τ/ΔT)·(X_end - X_start - α·ΔT)
variance(τ) = σ²·τ·(1 - τ/ΔT)
```

Where:
- `τ = t - t_start` (time since segment start)
- `ΔT = t_end - t_start` (total segment length)
- `α = μ_eff - ½σ²` (log-space drift)

This is the correct formulation for a **drifted Brownian Bridge**, not just a linear interpolation, but a full probabilistic path that respects the underlying GBM dynamics while being anchored at known endpoints.

Beyond the last known point, the model reverts to a free GBM walk, reflecting genuine uncertainty about the close just as a real trader would face, the reason we chose to remove the final anchor was to sustain the uncertainty and allow the model more freedom for interesting gameplay, whole constraining the AI's performance.

---

### Monte Carlo Simulation

The AI runs `N` independent Monte Carlo paths (scaled by difficulty), each generated via the Brownian Bridge approach above. From these paths, the engine computes:

- **Per-timestep mean and variance** : accumulated using a numerically stable two-pass method
- **Median path** : computed using `std::nth_element` (O(n), not O(n log n)) to resist outlier domination
- **Final predicted path** :  a 50/50 blend of the median path and a randomly selected individual path, introducing controlled stochasticity into the prediction while preserving the overall distribution shape

This blending approach avoids the over-smoothing problem of using the mean alone (which suppresses realistic price volatility) while preventing outlier paths from dominating. As Monte carlo runs `N` increases the stochastic noise collapses into a single line which is terrible for gameplay, despite being probabalistically correct.

---

## Architecture

```
main.cpp
│
├── GBM price generation (log-space, Itô-corrected)
│   └── Writes ground truth to {TICKER}_stock_prices.csv
│
└── AI (AI.cpp)
    │
    ├── getKnownPoints()
    │   └── Samples N random anchor points from the ground truth CSV
    │       (N determined by difficulty level)
    │
    ├── predictGraph() — called per Monte Carlo run
    │   ├── Converts known prices to log-space
    │   ├── Runs Brownian Bridge between each consecutive anchor pair
    │   └── Extends freely beyond the last known point
    │
    ├── MonteCarloSimulate()
    │   ├── Spawns std::thread workers (hardware_concurrency)
    │   ├── Per-thread local accumulators (cache-friendly, low contention)
    │   ├── Single mutex merge at thread completion
    │   ├── Computes mean, variance, median (nth_element)
    │   └── Blends median + random path for final prediction
    │
    └── writeToCSV()
        └── Writes prediction to {TICKER}_predicted_prices.csv
```

---

## Difficulty Levels

The difficulty system models **information asymmetry** — how much of the true price path the AI has access to. This is the key mechanism for risk benchmarking.

| Difficulty | Known Points | Monte Carlo Runs | Characteristic |
|:---:|:---:|:---:|---|
| 1 | 3 | 1,000 | Minimal information| broad uncertainty |
| 2 | 5 | 2,000 | Sparse anchors | moderate uncertainty |
| 3 | 10 | 5,000 | Reasonable coverage | narrow uncertainty |
| 4 | 50 | 10,000 | Dense anchors | near-optimal prediction |

Known points are sampled randomly (no cherry-picking) and sorted chronologically before use. Even at difficulty 1, the Brownian Bridge conditioning produces surprisingly accurate predictions which is thanks to the conditioning and accuracy of our formulaes.

---

## Multithreading Design

The Monte Carlo engine is parallelised using `std::thread` with a deliberately minimal locking strategy:

- Each thread maintains **local accumulators** (`localSum`, `localSumSq`) | no shared memory writes during path generation
- A single `std::mutex` is acquired **once per thread** at completion to merge into the global accumulators
- Thread count is set to `std::thread::hardware_concurrency()` with a fallback of 4 `(typically minimum spec)`

This design avoids false sharing, eliminates per-path locking overhead, and scales cleanly with core count. On an 8-core machine, all 8 simulation graphs plus the AI prediction complete in under 1 second at difficulty 4 (10,000 Monte Carlo runs).

Upon testing the threaded approach at 1,000 runs the time taken on `8 Cores` was ~ 40ms, scaling up to 10,000 runs the observed runtime was 400ms showing scaling at a O(n) complexity as expected.

---

## Build & Run

### Requirements

- C++17 or later
- POSIX threads (standard on Linux/macOS; use MinGW or WSL on Windows)

This currently:
1. Generates the ground truth GBM price path → `AAPL_stock_prices.csv`
2. Runs the AI prediction engine → `AAPL_predicted_prices.csv`

### Parameters (edit in `main.cpp`)

| Parameter | Default | Description |
|---|---|---|
| `S0` | 500.0 | Initial stock price |
| `mu` | 0.05 | Annualised drift |
| `sigma` | 0.4 | Annualised volatility |
| `sentiment` | 1.0 | Market sentiment multiplier |
| `difficulty` | 4 | AI difficulty (1–4) |

---

## Output Format

Both output CSVs share the same schema:

```
Time,Ticker,Price
0,AAPL,500.0
1,AAPL,500.84
...
509,AAPL,487.23
```

- `Time` : minute index (0–509, representing 08:00–16:30)
- `Ticker` : CLI-supplied ticker symbol
- `Price` : simulated or predicted price at that minute

---

## Known Limitations & Planned Work

- **Parameters are hardcoded in `main.cpp`** — CLI flags for `mu`, `sigma`, `sentiment`, and `difficulty` are planned
- **`char*` ticker** — will be migrated to `std::string` for safer memory ownership
- **Single-day scope**  the model currently simulates one trading session; multi-day path chaining is planned
- **AI difficulty 1 is too accurate** — even 3 anchor points produce a low L2 error vs ground truth; planned mitigation is to add `±5%` parameter noise at lower difficulties to reflect realistic model uncertainty

---

## Context

This project was built as part of COMP208 (second year, University of Liverpool) and serves as the simulation and risk benchmarking engine for a larger trading game, where a human player trades against the AI prediction on an unknown ground truth path.

The goal is to benchmark trading decision-making under information asymmetry: the player sees the ground truth price unfold in real time, the AI sees only its anchor points and must predict the rest.

---
## To-Do

### Server–Client Communication (Java, TCP)
- [ ] Implement multithreaded Java TCP server to parse CSV price data
- [ ] Stream price points to clients in monotonic (simulated real-time) order
- [ ] Design lightweight message protocol  
  - `DATA|time|price`  
  - `ACTION|BUY|qty`
- [ ] Support multiple concurrent clients using `ExecutorService`
- [ ] Implement client networking layer (receive data + send BUY/SELL/HOLD decisions)

---

### Frontend Integration (JavaFX + FXML)
- [ ] Link UI buttons (BUY / SELL / HOLD) to backend actions
- [ ] Display live price updates from server feed
- [ ] Add real-time price chart visualisation
- [ ] Ensure thread safety (separate networking from JavaFX UI thread)

---

### Database Implementation (Relational)
- [ ] Design schema for users and game sessions
- [ ] Implement secure authentication (hashed + salted passwords)
- [ ] Store game results (PnL, difficulty, timestamp)
- [ ] Apply AES-256 encryption for sensitive stored data where appropriate

---

### Gameplay Systems
- [ ] Implement portfolio tracking (cash, holdings, PnL)
- [ ] Add server-side trade execution logic
- [ ] Create game session lifecycle (start → run → end-of-day summary)

---

### Simulation & AI Enhancements
- [ ] Add parameter noise at lower difficulties to reduce AI over-accuracy
- [ ] Support CLI/config inputs for:
  - `mu`
  - `sigma`
  - `sentiment`
  - `difficulty`
- [ ] Extend simulation to multi-day price paths

---

### Stretch Goal — 2D Exploration Layer
- [ ] Integrate 16-bit style 2D world (Tiled)
- [ ] Implement player movement system
- [ ] Add NPC dialogue interactions (market hints, insights) -> (Sentiment Manipulation)
- [ ] Introduce purchasable upgrades / power-ups (roguelike elements)

