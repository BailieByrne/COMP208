// Cycle1.java
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;

class Cycle1 {
    private volatile boolean running = true;
    private ScheduledExecutorService scheduler;

    // Coffee powerup sub-scheduler
    //Additional powerups can be added here
    //E.g alcohol actually changes the step to 10
    private ScheduledFuture<?>         coffeeFuture    = null;
    private ScheduledExecutorService   coffeeScheduler = null;
    private volatile boolean           coffeeActive    = false;

    //Set const finals for steps and max index
    private static final int DEFAULT_STEP = 5;
    private static final int COFFEE_STEP  = 1;
    private static final int MAX_INDEX    = 509;

    // Shared client state map - injected from server so Cycle1 can check powerups
    private final Map<Integer, ClientState> clientStateMap;

    // ticker -> double[510] of prices, index 0-509 mirrors CSV Time column
    private final Map<String, double[]>  priceData  = new HashMap<>();
    private final Map<String, Integer>   priceIndex = new HashMap<>();

    public Cycle1(String csvPath, Map<Integer, ClientState> clientStateMap) {
        this.clientStateMap = clientStateMap;
        loadCSV(csvPath);
    }

    private void loadCSV(String csvPath) {
        Map<String, List<Double>> temp = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(Path.of(csvPath))) {
            br.readLine(); // skip header: Time,Ticker,Price

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String ticker = parts[1].trim();
                double price  = Double.parseDouble(parts[2].trim());

                if (!temp.containsKey(ticker)) temp.put(ticker, new ArrayList<>(510));
                temp.get(ticker).add(price);
            }
        } catch (Exception e) {
            System.err.println("Failed to load CSV: " + e.getMessage());
        }

        // Flatten to primitive double[] - faster access on hot send path
        for (Map.Entry<String, List<Double>> entry : temp.entrySet()) {
            List<Double> prices = entry.getValue();
            double[] arr = new double[prices.size()];
            for (int i = 0; i < prices.size(); i++) arr[i] = prices.get(i);
            priceData.put(entry.getKey(), arr);
            priceIndex.put(entry.getKey(), 0);
        }

        System.out.println("Loaded tickers: " + priceData.keySet());
    }

    // Advances index by step, clamps at MAX_INDEX, returns price at new position
    private double nextPrice(String ticker, int step) {
        double[] prices = priceData.get(ticker);
        int idx  = priceIndex.get(ticker);
        int next = Math.min(idx + step, MAX_INDEX);
        priceIndex.put(ticker, next);
        return prices[next];
    }

    private boolean simulationComplete() {
        return priceIndex.values().stream().allMatch(i -> i >= MAX_INDEX);
    }

    public String createStockPacket(String ticker, double price, int csvTick) {
        return String.format("{\"TICKER\": \"%s\", \"PRICE\": %.2f, \"TIME\": %d}", ticker, price, csvTick);
    }

    public Map<String, double[]> getPriceData() {
        return priceData;
    }

    // TIME in packet = priceIndex AFTER advance = exact CSV Time column value
    private void sendTick(Socket socket, List<String> stockList, int step) {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            for (String ticker : stockList) {
                try {
                    double price  = nextPrice(ticker, step);
                    int    csvTick = priceIndex.get(ticker);   // read after advance - mirrors CSV time
                    writer.write(createStockPacket(ticker, price, csvTick));
                    writer.write("\n");
                    writer.flush();
                } catch (Exception e) {
                    System.err.println("Send failed [" + ticker + "]: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to open socket writer: " + e.getMessage());
        }
    }

    private void startCoffeeMode(Socket socket, int clientID, List<String> stockList) {
        coffeeActive    = true;
        coffeeScheduler = Executors.newSingleThreadScheduledExecutor();
        System.out.println("Coffee powerup active - Client ID: " + clientID);

        coffeeFuture = coffeeScheduler.scheduleAtFixedRate(() -> {
            if (!running || simulationComplete()) {
                stopCoffeeMode();
                return;
            }

            ClientState state = clientStateMap.get(clientID);
            if (state == null || !state.hasPowerup("coffee")) {
                stopCoffeeMode();
                return;
            }

            sendTick(socket, stockList, COFFEE_STEP);

        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    private void stopCoffeeMode() {
        coffeeActive = false;
        if (coffeeFuture != null)    coffeeFuture.cancel(false);
        if (coffeeScheduler != null) {
            coffeeScheduler.shutdown();
            coffeeScheduler = null;
        }
        System.out.println("Coffee mode ended, resuming default.");
    }

    public void run(Socket socket, int clientID, List<String> stockList) {
        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            if (!running || simulationComplete()) {
                stop();
                return;
            }

            System.out.println("Cycle1 running... Client ID: " + clientID);

            ClientState state     = clientStateMap.get(clientID);
            boolean     hasCoffee = state != null && state.hasPowerup("coffee");

            if (hasCoffee && !coffeeActive) {
                // Powerup just activated - hand off to coffee sub-scheduler
                startCoffeeMode(socket, clientID, stockList);

            } else if (!hasCoffee && coffeeActive) {
                // Powerup expired - stop coffee sub-scheduler, resume default
                stopCoffeeMode();

            } else if (!coffeeActive) {
                // Normal mode: step 5 every second
                // TIME 5 -> 10 -> 15 ... mirrors CSV Time column
                sendTick(socket, stockList, DEFAULT_STEP);
            }
            // If coffeeActive the sub-scheduler is driving - skip this tick

        }, 0, 1, TimeUnit.SECONDS);
        /**
         * TODO:
         * Possible add a sync here for to ensure the client list is receiving ticks equally and fairly.
         */
    }

    public void stop() {
        running = false;
        stopCoffeeMode();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Cycle1 stopped.");
    }
}

// The packet output now looks like:
// // Default (step 5, 1s intervals)
// t=1s → {"TICKER": "AAPL", "PRICE": 490.92, "TIME": 5}
// t=2s → {"TICKER": "AAPL", "PRICE": 478.69, "TIME": 10}
// t=3s → {"TICKER": "AAPL", "PRICE": 481.17, "TIME": 15}

// // Coffee active (step 1, 0.2s intervals - same 5 points/sec)
// t=4.0s → {"TICKER": "AAPL", "PRICE": 484.62, "TIME": 16}
// t=4.2s → {"TICKER": "AAPL", "PRICE": 497.74, "TIME": 17}
// t=4.4s → {"TICKER": "AAPL", "PRICE": 489.88, "TIME": 18}
// t=4.6s → {"TICKER": "AAPL", "PRICE": 493.09, "TIME": 19}
// t=4.8s → {"TICKER": "AAPL", "PRICE": 495.21, "TIME": 20}

// // Coffee expired - back to default
// t=5s → {"TICKER": "AAPL", "PRICE": 478.10, "TIME": 25}