import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class GameUIController {
    @FXML private Label labelDay;
    @FXML private Label labelTime;
    @FXML private Label lblCash;
    @FXML private Label labelNetWorth;
    @FXML private LineChart<Number, Number> ChartUser;
    @FXML private LineChart<Number, Number> ChartAI;
    @FXML private NumberAxis ChartUserXAxis;
    @FXML private NumberAxis ChartAIXAxis;
    @FXML private NumberAxis ChartUserYAxis;
    @FXML private NumberAxis ChartAIYAxis;
    @FXML private ComboBox<String> comboTicker;
    @FXML private ComboBox<String> comboQty;
    @FXML private TableView<PortfolioItem> tableHoldings;
    @FXML private TableColumn<PortfolioItem, String> colTicker;
    @FXML private TableColumn<PortfolioItem, Integer> colQty;
    @FXML private TableColumn<PortfolioItem, String> colValue;
    @FXML private javafx.scene.control.Button btnBuy;
    @FXML private javafx.scene.control.Button btnSell;
    @FXML private javafx.scene.control.Button btnSellAll;
    @FXML private Label lblDebugInfo;

    // Support multiple tickers
    private final Map<String, XYChart.Series<Number, Number>> tickerSeries = new HashMap<>();
    private final Map<String, XYChart.Series<Number, Number>> aiTickerSeries = new HashMap<>();
    private final Map<String, Double> lastPricePerTicker = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> tickerOrder = new ArrayList<>();
    private String currentTicker = null;
    private double highestPriceSeen = 0.0;
    private double highestAIPriceSeen = 0.0;
    private static final int MAX_POINTS = 510;  // Full trading day
    private double currentPrice = 0.0;  // Track current price for buy/sell
    private double playerCash = 10000.0;  // Track player cash for max calculation

    //Inits the cycle1 screen and chart data
    @FXML
    private void initialize() {
        if (ChartUser != null) {
            ChartUser.setTitle("Live Price Feed");
            ChartUser.setAnimated(false);
            ChartUser.setLegendVisible(false);
            ChartUser.setCreateSymbols(false);
        }

        if (ChartUserXAxis != null) {
            ChartUserXAxis.setAutoRanging(false);
            ChartUserXAxis.setLowerBound(0.0);
            ChartUserXAxis.setUpperBound(550.0);
            ChartUserXAxis.setTickUnit(100.0);
            ChartUserXAxis.setForceZeroInRange(true);
        }

        if (ChartUserYAxis != null) {
            ChartUserYAxis.setAutoRanging(false);
            ChartUserYAxis.setLowerBound(0.0);
            ChartUserYAxis.setUpperBound(1.0);
            ChartUserYAxis.setTickUnit(1.0);
        }

        if (ChartAI != null) {
            ChartAI.getData().clear();
            ChartAI.setTitle("AI Prediction");
            ChartAI.setAnimated(false);
            ChartAI.setLegendVisible(false);
            ChartAI.setCreateSymbols(false);
        }

        if (ChartAIXAxis != null) {
            ChartAIXAxis.setAutoRanging(false);
            ChartAIXAxis.setLowerBound(0.0);
            ChartAIXAxis.setUpperBound(550.0);
            ChartAIXAxis.setTickUnit(100.0);
            ChartAIXAxis.setForceZeroInRange(true);
        }

        if (ChartAIYAxis != null) {
            ChartAIYAxis.setAutoRanging(false);
            ChartAIYAxis.setLowerBound(0.0);
            ChartAIYAxis.setUpperBound(1.0);
            ChartAIYAxis.setTickUnit(1.0);
        }

        if (comboTicker != null) {
            comboTicker.setOnAction(e -> onTickerSelected());
        }

        if (labelDay != null) {
            labelDay.setText("Waiting for data...");
        }
        if (labelTime != null) {
            labelTime.setText("Connected view ready");
        }
        if (lblCash != null) {
            lblCash.setText("Cash: £0.00");
        }
        if (labelNetWorth != null) {
            labelNetWorth.setText("Net Worth: £0.00");
        }

        // Initialize portfolio table
        if (tableHoldings != null) {
            if (colTicker != null) {
                colTicker.setCellValueFactory(new PropertyValueFactory<>("ticker"));
            }
            if (colQty != null) {
                colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            }
            if (colValue != null) {
                colValue.setCellValueFactory(new PropertyValueFactory<>("value"));
            }
            tableHoldings.setItems(FXCollections.observableArrayList());
        }

        // Initialize buy/sell buttons
        if (btnBuy != null) {
            btnBuy.setOnAction(e -> onBuyClicked());
        }
        if (btnSell != null) {
            btnSell.setOnAction(e -> onSellClicked());
        }
        if (btnSellAll != null) {
            btnSellAll.setOnAction(e -> onSellAllClicked());
        }

        // Initialize quantity combo box with options: 1, 5, 10, max
        if (comboQty != null) {
            comboQty.setItems(FXCollections.observableArrayList("1", "5", "10", "max"));
            comboQty.setValue("1");  // Default to 1
        }

        // Initialize table with all available stocks at 0 quantity
        initializeStocksTable();
    }

    private void initializeStocksTable() {
        if (tableHoldings == null) return;
        
        String[] stocks = {"AAPL", "TSLA", "GOOGL", "NVDA", "AMZN"};
        ObservableList<PortfolioItem> items = FXCollections.observableArrayList();
        
        for (String ticker : stocks) {
            items.add(new PortfolioItem(ticker, 0, "£0.00"));
        }
        
        tableHoldings.setItems(items);
    }

    public void setConnectionStatus(String text) {
        runOnFxThread(() -> {
            if (labelTime != null) {
                labelTime.setText(text);
            }
        });
    }

    /**
     * Update price data from server - called when PRICE_UPDATE packet arrives
     * Formats: ticker (e.g., "AAPL"), price (double), point (int from 5 to 510)
     */
    public void updatePriceData(String ticker, double price, int point) {
        handlePricePacket(ticker, price, point, "PRICE_UPDATE|" + ticker + "|" + price + "|" + point);
    }

    public void handlePricePacket(String ticker, double price, int time, String rawPacket) {
        final String displayTicker = (ticker == null || ticker.isEmpty()) ? "UNKNOWN" : ticker.toUpperCase();
        
        runOnFxThread(() -> {
            // Ensure this ticker has a series
            if (!tickerSeries.containsKey(displayTicker)) {
                XYChart.Series<Number, Number> newSeries = new XYChart.Series<>();
                newSeries.setName(displayTicker);
                tickerSeries.put(displayTicker, newSeries);

                XYChart.Series<Number, Number> newAiSeries = new XYChart.Series<>();
                newAiSeries.setName(displayTicker + " AI");
                aiTickerSeries.put(displayTicker, newAiSeries);

                tickerOrder.add(displayTicker);
                
                // Add to combo box
                if (comboTicker != null && !comboTicker.getItems().contains(displayTicker)) {
                    comboTicker.getItems().add(displayTicker);
                }
                
                // Auto-select first ticker
                if (currentTicker == null) {
                    currentTicker = displayTicker;
                    if (comboTicker != null) {
                        comboTicker.setValue(displayTicker);
                    }
                    displayTickerChart(displayTicker);
                }
            }

            // Add data point to this ticker's series
            XYChart.Series<Number, Number> series = tickerSeries.get(displayTicker);
            series.getData().add(new XYChart.Data<>((double) time, price));
            trimSeries(series);
            
            // Track latest price for this ticker (used in portfolio display)
            lastPricePerTicker.put(displayTicker, price);
            updatePortfolioWithLatestPrices();

            // Update chart if this is the current ticker
            if (displayTicker.equals(currentTicker)) {
                if (ChartUser != null) {
                    // rebind and refresh the visible user chart every packet so it always grows live.
                    //This is costly so im thinkign of optimising later
                    XYChart.Series<Number, Number> renderSeries = new XYChart.Series<>();
                    renderSeries.setName(displayTicker);
                    renderSeries.getData().addAll(series.getData());
                    ChartUser.getData().clear();
                    ChartUser.getData().add(renderSeries);
                    ChartUser.setTitle("Live Price Feed - " + displayTicker);
                }
                currentPrice = price;  // Store current price for buy/sell
                updateYAxis(price);
            }

            // Update labels
            if (labelDay != null) {
                labelDay.setText("Tickers: " + String.join(" | ", tickerOrder));
            }
            if (labelTime != null) {
                labelTime.setText("Current ticker: " + displayTicker + " price: £" + String.format("%.2f", price));
            }
            // Update debug info at bottom right (not cash/networth)
            if (lblDebugInfo != null) {
                lblDebugInfo.setText("Price: £" + String.format("%.2f", price) + " | Points: " + series.getData().size());
            }
        });
    }

    //Mirror of User packet, will be replaced later as the AI screen recieves the same TICKER,
    //This is purley to see the AIs reconstruction of the GBM and thn i can add the tarding algo
    public void handleAIPacket(String ticker, double price, int time, String rawPacket) {
        final String displayTicker = (ticker == null || ticker.isEmpty()) ? "UNKNOWN" : ticker.toUpperCase();

        runOnFxThread(() -> {
            if (!aiTickerSeries.containsKey(displayTicker)) {
                XYChart.Series<Number, Number> newAiSeries = new XYChart.Series<>();
                newAiSeries.setName(displayTicker + " AI");
                aiTickerSeries.put(displayTicker, newAiSeries);
            }

            XYChart.Series<Number, Number> aiSeries = aiTickerSeries.get(displayTicker);
            aiSeries.getData().add(new XYChart.Data<>((double) time, price));

            if (displayTicker.equals(currentTicker) && ChartAI != null) {
                // This avoids stale chart references after screen/ticker switches.
                XYChart.Series<Number, Number> renderSeries = new XYChart.Series<>();
                renderSeries.setName(displayTicker + " AI");
                renderSeries.getData().addAll(aiSeries.getData());
                ChartAI.getData().clear();
                ChartAI.getData().add(renderSeries);
                ChartAI.setTitle("AI Prediction - " + displayTicker);
                updateAIYAxis(price);

                if (labelNetWorth != null) {
                    labelNetWorth.setText("Points: " + tickerSeries.getOrDefault(displayTicker, new XYChart.Series<>()).getData().size()
                        + " | AI: " + aiSeries.getData().size());
                }
            }
        });
    }

    // Handle comboTicker selection change
    private void onTickerSelected() {
        if (comboTicker == null || comboTicker.getValue() == null) return;
        String selected = comboTicker.getValue();
        displayTickerChart(selected);
    }

    private void displayTickerChart(String ticker) {
        currentTicker = ticker;
        highestPriceSeen = 0.0;
        highestAIPriceSeen = 0.0;
        
        if (ChartUser != null) {
            ChartUser.getData().clear();
            XYChart.Series<Number, Number> series = tickerSeries.get(ticker);
            if (series != null) {
                ChartUser.getData().add(series);
                ChartUser.setTitle("Live Price Feed - " + ticker);
                
                // Reset highest price for this ticker
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    highestPriceSeen = Math.max(highestPriceSeen, data.getYValue().doubleValue());
                }
                updateYAxis(highestPriceSeen);
            }
        }

        if (ChartAI != null) {
            ChartAI.getData().clear();
            XYChart.Series<Number, Number> aiSeries = aiTickerSeries.get(ticker);
            if (aiSeries != null) {
                ChartAI.getData().add(aiSeries);
                ChartAI.setTitle("AI Prediction - " + ticker);
                for (XYChart.Data<Number, Number> data : aiSeries.getData()) {
                    highestAIPriceSeen = Math.max(highestAIPriceSeen, data.getYValue().doubleValue());
                }
                updateAIYAxis(highestAIPriceSeen);
            } else {
                ChartAI.setTitle("AI Prediction");
                updateAIYAxis(1.0);
            }
        }

        if (labelDay != null) {
            labelDay.setText("Viewing: " + ticker);
        }
    }

    private void updateYAxis(double latestPrice) {
        highestPriceSeen = Math.max(highestPriceSeen, latestPrice);
        if (ChartUserYAxis != null) {
            int maxAxisInt = Math.max(1, (int) Math.ceil(highestPriceSeen * 1.5));
            int tickUnitInt = Math.max(1, (int) Math.ceil(maxAxisInt / 10.0));
            ChartUserYAxis.setLowerBound(0.0);
            ChartUserYAxis.setUpperBound(maxAxisInt);
            ChartUserYAxis.setTickUnit(tickUnitInt);
        }
    }

    private void updateAIYAxis(double latestPrice) {
        highestAIPriceSeen = Math.max(highestAIPriceSeen, latestPrice);
        if (ChartAIYAxis != null) {
            int maxAxisInt = Math.max(1, (int) Math.ceil(highestAIPriceSeen * 1.5));
            int tickUnitInt = Math.max(1, (int) Math.ceil(maxAxisInt / 10.0));
            ChartAIYAxis.setLowerBound(0.0);
            ChartAIYAxis.setUpperBound(maxAxisInt);
            ChartAIYAxis.setTickUnit(tickUnitInt);
        }
    }

    //Handle FX thread, try to run now or add action to runlater depending on current thread
    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Trim a data series to prevent unbounded memory growth.
     * 
     * Removes oldest data points from the series when it exceeds MAX_POINTS.
     * This keeps the chart responsive and memory usage bounded during long play sessions.
     * 
     * @param series The XYChart.Series to trim
     */
    private void trimSeries(XYChart.Series<Number, Number> series) {
        while (series.getData().size() > MAX_POINTS) {
            series.getData().remove(0);
        }
    }

    // show coffee powerup countdown timer in UI
    public void showPowerupTimer(String name, long durationMs) {
        long endTime = System.currentTimeMillis() + durationMs;
        Thread timerThread = new Thread(() -> {
            while (System.currentTimeMillis() < endTime) {
                long remaining = Math.max(0, endTime - System.currentTimeMillis());
                long seconds = remaining / 1000;
                Platform.runLater(() -> {
                    if (labelTime != null) {
                        labelTime.setText("Coffee: " + seconds + "s");
                    }
                });
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            // timer expired, reset label
            Platform.runLater(() -> {
                if (labelTime != null) {
                    labelTime.setText("");
                }
            });
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    /**
     * Update portfolio display with cash and holdings
     * Format: PORTFOLIO_UPDATE|cash|ticker1:qty1:price1|ticker2:qty2:price2|...
     */
    /**
     * Update portfolio with latest prices from price updates
     */
    private void updatePortfolioWithLatestPrices() {
        runOnFxThread(() -> {
            if (tableHoldings == null) return;
            
            double totalValue = 0.0;
            double cashAmount = playerCash;
            
            // Update each row with latest price
            for (PortfolioItem item : tableHoldings.getItems()) {
                double latestPrice = lastPricePerTicker.getOrDefault(item.ticker, 0.0);
                double itemValue = item.quantity * latestPrice;
                totalValue += itemValue;
                item.value = String.format("£%.2f", itemValue);
            }
            
            tableHoldings.refresh();
            
            // Update net worth with latest prices
            if (labelNetWorth != null) {
                double netWorth = cashAmount + totalValue;
                labelNetWorth.setText("Net Worth: £" + String.format("%.2f", netWorth));
            }
        });
    }

    public void updatePortfolio(String cash, List<PortfolioEntry> holdings) {
        runOnFxThread(() -> {
            // Update cash label and track player cash
            if (lblCash != null) {
                try {
                    double cashAmount = Double.parseDouble(cash);
                    this.playerCash = cashAmount;  // Store for max calculation
                    lblCash.setText("Cash: £" + String.format("%.2f", cashAmount));
                } catch (NumberFormatException e) {
                    lblCash.setText("Cash: £" + cash);
                }
            }

            // Update holdings table
            if (tableHoldings != null) {
                ObservableList<PortfolioItem> items = FXCollections.observableArrayList();
                double totalValue = 0.0;

                if (holdings != null) {
                    for (PortfolioEntry entry : holdings) {
                        // Use server's price if available, otherwise use last streamed price
                        double price = entry.currentPrice > 0 ? entry.currentPrice : lastPricePerTicker.getOrDefault(entry.ticker, 0.0);
                        lastPricePerTicker.put(entry.ticker, price);  // Store for future updates
                        double value = entry.quantity * price;
                        totalValue += value;
                        items.add(new PortfolioItem(
                            entry.ticker,
                            entry.quantity,
                            String.format("£%.2f", value)
                        ));
                    }
                }

                tableHoldings.setItems(items);

                // Update net worth
                if (labelNetWorth != null) {
                    try {
                        double cashAmount = Double.parseDouble(cash);
                        double netWorth = cashAmount + totalValue;
                        labelNetWorth.setText("Net Worth: £" + String.format("%.2f", netWorth));
                    } catch (NumberFormatException e) {
                        labelNetWorth.setText("Net Worth: £" + String.format("%.2f", totalValue));
                    }
                }
            }
        });
    }

    private void onBuyClicked() {
        if (comboTicker == null || comboTicker.getValue() == null) {
            labelTime.setText("Please select a ticker");
            return;
        }
        if (comboQty == null || comboQty.getValue() == null) {
            labelTime.setText("Please select a quantity");
            return;
        }
        
        String ticker = comboTicker.getValue();
        String qtyStr = comboQty.getValue();
        int quantity;
        
        if ("max".equals(qtyStr)) {
            // Calculate max: floor(playerCash / currentPrice)
            if (currentPrice <= 0) {
                labelTime.setText("Invalid price");
                return;
            }
            quantity = (int) (playerCash / currentPrice);
        } else {
            quantity = Integer.parseInt(qtyStr);
        }
        
        if (quantity <= 0) {
            labelTime.setText("Insufficient cash to buy");
            return;
        }
        
        // Send BUY packet to server: BUY|ticker|quantity|price
        client.getInstance().requestBuy(ticker, quantity, currentPrice);
    }

    private void onSellClicked() {
        if (comboTicker == null || comboTicker.getValue() == null) {
            labelTime.setText("Please select a ticker");
            return;
        }
        if (comboQty == null || comboQty.getValue() == null) {
            labelTime.setText("Please select a quantity");
            return;
        }
        
        String ticker = comboTicker.getValue();
        String qtyStr = comboQty.getValue();
        int quantity;
        
        if ("max".equals(qtyStr)) {
            // Get current holding from table
            int maxHolding = 0;
            for (PortfolioItem item : tableHoldings.getItems()) {
                if (item.ticker.equals(ticker)) {
                    maxHolding = item.quantity;
                    break;
                }
            }
            quantity = maxHolding;
        } else {
            quantity = Integer.parseInt(qtyStr);
        }
        
        if (quantity <= 0) {
            labelTime.setText("No holdings to sell");
            return;
        }
        
        // Send SELL packet to server: SELL|ticker|quantity|price
        client.getInstance().requestSell(ticker, quantity, currentPrice);
    }

    private void onSellAllClicked() {
        if (comboTicker == null || comboTicker.getValue() == null) {
            labelTime.setText("Please select a ticker");
            return;
        }
        
        String ticker = comboTicker.getValue();
        
        // Send SELL_ALL packet to server: SELL_ALL|ticker|price
        client.getInstance().requestSellAll(ticker, currentPrice);
    }

    /**
     * Data class for portfolio entries sent from server
     */
    public static class PortfolioEntry {
        public String ticker;
        public int quantity;
        public double currentPrice;

        public PortfolioEntry(String ticker, int quantity, double currentPrice) {
            this.ticker = ticker;
            this.quantity = quantity;
            this.currentPrice = currentPrice;
        }
    }

    /**
     * Data class for table display
     */
    public static class PortfolioItem {
        private String ticker;
        private int quantity;
        private String value;

        public PortfolioItem(String ticker, int quantity, String value) {
            this.ticker = ticker;
            this.quantity = quantity;
            this.value = value;
        }

        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
