import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Cycle 1
public class CycleOneEngine implements CycleEngine {
    //Constants
    private static final int MAX_INDEX = 509;
    private static final int DEFAULT_STEP = 5;

    //Data structures
    private final Map<String, double[]> priceData = new HashMap<>();
    private final Map<String, double[]> aiPriceData = new HashMap<>();
    private final Map<Integer, Integer> perClientIndex = new HashMap<>();
    private final String csvPath;
    private boolean running;

    // single ticker path constructor
    public CycleOneEngine(String csvPath) {
        this.csvPath = csvPath;
        loadCSV(csvPath);
        loadAICSV(deriveAICsvPath(csvPath));
    }

    // multi ticker day constructor
    public CycleOneEngine(List<String> csvPaths) {
        this.csvPath = csvPaths.isEmpty() ? "" : csvPaths.get(0);
        for (String path : csvPaths) {
            loadCSV(path);
            loadAICSV(deriveAICsvPath(path));
        }
    }

    //Getters and setters for cycle name, start, stop, isComplete
    @Override
    public String getName() {
        return "CYCLE1";
    }

    @Override
    public void start() {
        running = true;
        perClientIndex.clear();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isComplete() {
        if (!running || perClientIndex.isEmpty()) {
            return false;
        }
        for (int idx : perClientIndex.values()) {
            if (idx < MAX_INDEX) {
                return false;
            }
        }
        return true;
    }

    //Granularity is impacted by powerups like coffee
    public String nextPacketForClient(int clientId, String ticker, int granularityStep) {
        if (!running) {
            return null;
        }

        double[] prices = priceData.get(ticker);
        if (prices == null || prices.length == 0) {
            return null;
        }

        // each client tracks its own timeline speed
        int current = perClientIndex.getOrDefault(clientId, 0);
        int step = granularityStep > 0 ? granularityStep : DEFAULT_STEP;
        int next = Math.min(current + step, Math.min(MAX_INDEX, prices.length - 1));
        perClientIndex.put(clientId, next);

        double price = prices[next];
        return String.format("{\"TYPE\":\"PRICE\",\"CYCLE\":\"CYCLE1\",\"TICKER\":\"%s\",\"PRICE\":%.2f,\"TIME\":%d}", ticker, price, next);
    }

    public int advanceAndGetIndexForClient(int clientId, int granularityStep) {
        if (!running) {
            return -1;
        }
        // step forward but clamp inside day window
        int current = perClientIndex.getOrDefault(clientId, 0);
        int step = granularityStep > 0 ? granularityStep : DEFAULT_STEP;
        int next = Math.min(current + step, MAX_INDEX);
        perClientIndex.put(clientId, next);
        return next;
    }

    public String pricePacketAtIndex(String ticker, int index) {
        if (!running) {
            return null;
        }
        double[] prices = priceData.get(ticker);
        if (prices == null || prices.length == 0) {
            return null;
        }
        int safeIndex = Math.min(index, Math.min(MAX_INDEX, prices.length - 1));
        double price = prices[safeIndex];
        return String.format("{\"TYPE\":\"PRICE\",\"CYCLE\":\"CYCLE1\",\"TICKER\":\"%s\",\"PRICE\":%.2f,\"TIME\":%d}", ticker, price, safeIndex);
    }

    public String aiPacketAtIndex(String ticker, int index) {
        if (!running) {
            return null;
        }
        double[] aiPrices = aiPriceData.get(ticker);
        if (aiPrices == null || aiPrices.length == 0) {
            return null;
        }
        int safeIndex = Math.min(index, Math.min(MAX_INDEX, aiPrices.length - 1));
        double aiPrice = aiPrices[safeIndex];
        return String.format("{\"TYPE\":\"AI_PRICE\",\"CYCLE\":\"CYCLE1\",\"TICKER\":\"%s\",\"PRICE\":%.2f,\"TIME\":%d}", ticker, aiPrice, safeIndex);
    }

    public String aiPacketForClientAtCurrentIndex(int clientId, String ticker) {
        if (!running) {
            return null;
        }

        double[] aiPrices = aiPriceData.get(ticker);
        if (aiPrices == null || aiPrices.length == 0) {
            return null;
        }

        int current = perClientIndex.getOrDefault(clientId, 0);
        int index = Math.min(current, Math.min(MAX_INDEX, aiPrices.length - 1));
        double aiPrice = aiPrices[index];
        return String.format("{\"TYPE\":\"AI_PRICE\",\"CYCLE\":\"CYCLE1\",\"TICKER\":\"%s\",\"PRICE\":%.2f,\"TIME\":%d}", ticker, aiPrice, index);
    }

    private String deriveAICsvPath(String stockCsvPath) {
        if (stockCsvPath == null) {
            return "AAPL_predicted_prices.csv";
        }
        if (stockCsvPath.endsWith("_stock_prices.csv")) {
            return stockCsvPath.replace("_stock_prices.csv", "_predicted_prices.csv");
        }
        return stockCsvPath.replace(".csv", "_predicted_prices.csv");
    }

    private void loadCSV(String path) {
        Map<String, List<Double>> temp = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(Path.of(path))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    continue;
                }
                String ticker = parts[1].trim();
                double price = Double.parseDouble(parts[2].trim());
                temp.computeIfAbsent(ticker, key -> new ArrayList<>(510)).add(price);
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to load cycle1 CSV (" + path + "): " + e.getMessage());
        }

        for (Map.Entry<String, List<Double>> entry : temp.entrySet()) {
            List<Double> values = entry.getValue();
            double[] arr = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                arr[i] = values.get(i);
            }
            priceData.put(entry.getKey(), arr);
        }

        System.out.println("Cycle1 loaded tickers: " + priceData.keySet());
    }

    private void loadAICSV(String path) {
        Map<String, List<Double>> temp = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(Path.of(path))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    continue;
                }
                String ticker = parts[1].trim();
                double price = Double.parseDouble(parts[2].trim());
                temp.computeIfAbsent(ticker, key -> new ArrayList<>(510)).add(price);
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to load AI CSV (" + path + "): " + e.getMessage());
            return;
        }

        for (Map.Entry<String, List<Double>> entry : temp.entrySet()) {
            List<Double> values = entry.getValue();
            double[] arr = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                arr[i] = values.get(i);
            }
            aiPriceData.put(entry.getKey(), arr);
        }

        System.out.println("Cycle1 loaded AI tickers: " + aiPriceData.keySet());
    }
}
