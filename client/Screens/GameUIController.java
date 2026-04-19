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
    @FXML private Label lblAICash;
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
    @FXML private TableView<PortfolioItem> tableAIHoldings;
    @FXML private TableColumn<PortfolioItem, String> colAITicker;
    @FXML private TableColumn<PortfolioItem, Integer> colAIQty;
    @FXML private TableColumn<PortfolioItem, String> colAIValue;
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
    
    // Trade markers (green for buy, red for sell)
    private final Map<String, List<XYChart.Series<Number, Number>>> buyMarkers = new HashMap<>();
    private final Map<String, List<XYChart.Series<Number, Number>>> sellMarkers = new HashMap<>();
    private final Map<String, List<XYChart.Series<Number, Number>>> aiBuyMarkers = new HashMap<>();
    private final Map<String, List<XYChart.Series<Number, Number>>> aiSellMarkers = new HashMap<>();
    
    // Portfolio tracking for trade detection
    private final Map<String, Integer> previousPortfolio = new HashMap<>();
    private final Map<String, Integer> previousAIPortfolio = new HashMap<>();
    private int currentPointIndex = 0;  // Current point in price stream

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
        if (lblAICash != null) {
            lblAICash.setText("AI Cash: £0.00");
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

        // Initialize AI portfolio table
        if (tableAIHoldings != null) {
            if (colAITicker != null) {
                colAITicker.setCellValueFactory(new PropertyValueFactory<>("ticker"));
            }
            if (colAIQty != null) {
                colAIQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            }
            if (colAIValue != null) {
                colAIValue.setCellValueFactory(new PropertyValueFactory<>("value"));
            }
            tableAIHoldings.setItems(FXCollections.observableArrayList());
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
        
        String[] stocks = {"TULA", "PEARS", "CORN", "RICE", "GRAIN"};
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
                    
                    // Add trade markers
                    if (buyMarkers.containsKey(displayTicker)) {
                        for (XYChart.Series<Number, Number> ms : buyMarkers.get(displayTicker)) {
                            ChartUser.getData().add(ms);
                        }
                    }
                    if (sellMarkers.containsKey(displayTicker)) {
                        for (XYChart.Series<Number, Number> ms : sellMarkers.get(displayTicker)) {
                            ChartUser.getData().add(ms);
                        }
                    }
                    
                    ChartUser.setTitle("Live Price Feed - " + displayTicker);
                }
                
                // Mirror the same data to AI chart
                if (ChartAI != null) {
                    XYChart.Series<Number, Number> aiRenderSeries = new XYChart.Series<>();
                    aiRenderSeries.setName(displayTicker);
                    aiRenderSeries.getData().addAll(series.getData());
                    ChartAI.getData().clear();
                    ChartAI.getData().add(aiRenderSeries);
                    
                    // Add AI trade markers
                    if (aiBuyMarkers.containsKey(displayTicker)) {
                        for (XYChart.Series<Number, Number> ms : aiBuyMarkers.get(displayTicker)) {
                            ChartAI.getData().add(ms);
                        }
                    }
                    if (aiSellMarkers.containsKey(displayTicker)) {
                        for (XYChart.Series<Number, Number> ms : aiSellMarkers.get(displayTicker)) {
                            ChartAI.getData().add(ms);
                        }
                    }
                    
                    ChartAI.setTitle("Live Price Feed - " + displayTicker);
                    updateAIYAxis(price);
                }
                
                currentPrice = price;  // Store current price for buy/sell
                currentPointIndex = time;  // Track current point index
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
                
                // Add trade markers for this ticker
                if (buyMarkers.containsKey(ticker)) {
                    for (XYChart.Series<Number, Number> ms : buyMarkers.get(ticker)) {
                        ChartUser.getData().add(ms);
                    }
                }
                if (sellMarkers.containsKey(ticker)) {
                    for (XYChart.Series<Number, Number> ms : sellMarkers.get(ticker)) {
                        ChartUser.getData().add(ms);
                    }
                }
                
                ChartUser.setTitle("Live Price Feed - " + ticker);
                
                // Reset highest price for this ticker
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    highestPriceSeen = Math.max(highestPriceSeen, data.getYValue().doubleValue());
                }
                updateYAxis(highestPriceSeen);
            }
        }

        // Mirror player chart to AI chart with same data
        if (ChartAI != null) {
            ChartAI.getData().clear();
            XYChart.Series<Number, Number> series = tickerSeries.get(ticker);
            if (series != null) {
                ChartAI.getData().add(series);
                
                // Add AI trade markers for this ticker
                if (aiBuyMarkers.containsKey(ticker)) {
                    for (XYChart.Series<Number, Number> ms : aiBuyMarkers.get(ticker)) {
                        ChartAI.getData().add(ms);
                    }
                }
                if (aiSellMarkers.containsKey(ticker)) {
                    for (XYChart.Series<Number, Number> ms : aiSellMarkers.get(ticker)) {
                        ChartAI.getData().add(ms);
                    }
                }
                
                ChartAI.setTitle("Live Price Feed - " + ticker);
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    highestAIPriceSeen = Math.max(highestAIPriceSeen, data.getYValue().doubleValue());
                }
                updateAIYAxis(highestAIPriceSeen);
            } else {
                ChartAI.setTitle("Live Price Feed");
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

    /**
     * Add a trade marker to the player chart
     * @param ticker Stock ticker
     * @param pointIndex Time index of the trade
     * @param price Price at which trade occurred
     * @param isBuy true for buy (green), false for sell (red)
     */
    private void addPlayerTradeMarker(String ticker, int pointIndex, double price, boolean isBuy) {
        Map<String, List<XYChart.Series<Number, Number>>> markerMap = isBuy ? buyMarkers : sellMarkers;

        XYChart.Data<Number, Number> markerPoint = new XYChart.Data<>(pointIndex, price);

        // Style the node the instant JavaFX assigns it — this survives chart redraws
        markerPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                String color = isBuy ? "#00CC44" : "#FF3333";
                newNode.setStyle(
                    "-fx-background-color: " + color + ", white;" +
                    "-fx-background-insets: 0, 2;" +
                    "-fx-background-radius: 8px;" +
                    "-fx-padding: 8px;"
                );
                newNode.toFront();
            }
        });

        XYChart.Series<Number, Number> markerSeries = new XYChart.Series<>();
        markerSeries.setName(ticker + (isBuy ? "_BUY_" : "_SELL_") + pointIndex);
        markerSeries.getData().add(markerPoint);

        markerMap.computeIfAbsent(ticker, k -> new ArrayList<>()).add(markerSeries);

        // Trigger chart refresh so the new marker series gets added
        if (ChartUser != null && ticker.equals(currentTicker)) {
            Platform.runLater(() -> refreshUserChart(ticker));
        }
    }

    /**
     * Add a trade marker to the AI chart
     */
    private void addAITradeMarker(String ticker, int pointIndex, double price, boolean isBuy) {
        Map<String, List<XYChart.Series<Number, Number>>> markerMap = isBuy ? aiBuyMarkers : aiSellMarkers;

        XYChart.Data<Number, Number> markerPoint = new XYChart.Data<>(pointIndex, price);

        markerPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                String color = isBuy ? "#00CC44" : "#FF3333";
                newNode.setStyle(
                    "-fx-background-color: " + color + ", white;" +
                    "-fx-background-insets: 0, 2;" +
                    "-fx-background-radius: 8px;" +
                    "-fx-padding: 8px;"
                );
                newNode.toFront();
            }
        });

        XYChart.Series<Number, Number> markerSeries = new XYChart.Series<>();
        markerSeries.setName(ticker + (isBuy ? "_AIBUY_" : "_AISELL_") + pointIndex);
        markerSeries.getData().add(markerPoint);

        markerMap.computeIfAbsent(ticker, k -> new ArrayList<>()).add(markerSeries);

        if (ChartAI != null && ticker.equals(currentTicker)) {
            Platform.runLater(() -> refreshAIChart(ticker));
        }
    }

    /**
     * Rebuild user chart with price series and all marker series for current ticker
     */
    private void refreshUserChart(String ticker) {
        if (ChartUser == null) return;
        ChartUser.getData().clear();
        XYChart.Series<Number, Number> series = tickerSeries.get(ticker);
        if (series != null) ChartUser.getData().add(series);
        if (buyMarkers.containsKey(ticker))  ChartUser.getData().addAll(buyMarkers.get(ticker));
        if (sellMarkers.containsKey(ticker)) ChartUser.getData().addAll(sellMarkers.get(ticker));
        ChartUser.setTitle("Live Price Feed - " + ticker);
    }

    /**
     * Rebuild AI chart with price series and all marker series for current ticker
     */
    private void refreshAIChart(String ticker) {
        if (ChartAI == null) return;
        ChartAI.getData().clear();
        XYChart.Series<Number, Number> series = tickerSeries.get(ticker);
        if (series != null) ChartAI.getData().add(series);
        if (aiBuyMarkers.containsKey(ticker))  ChartAI.getData().addAll(aiBuyMarkers.get(ticker));
        if (aiSellMarkers.containsKey(ticker)) ChartAI.getData().addAll(aiSellMarkers.get(ticker));
        ChartAI.setTitle("Live Price Feed - " + ticker);
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

            // Detect trades by comparing holdings
            if (holdings != null) {
                for (PortfolioEntry entry : holdings) {
                    int previousQty = previousPortfolio.getOrDefault(entry.ticker, 0);
                    int currentQty = entry.quantity;
                    
                    if (currentQty != previousQty) {
                        double price = entry.currentPrice > 0 ? entry.currentPrice : lastPricePerTicker.getOrDefault(entry.ticker, 0.0);
                        
                        if (currentQty > previousQty) {
                            // BUY detected
                            System.out.println("TRADE DETECTED: BUY " + entry.ticker + " (+" + (currentQty - previousQty) + ") at £" + price);
                            addPlayerTradeMarker(entry.ticker, currentPointIndex, price, true);
                        } else if (currentQty < previousQty) {
                            // SELL detected
                            System.out.println("TRADE DETECTED: SELL " + entry.ticker + " (-" + (previousQty - currentQty) + ") at £" + price);
                            addPlayerTradeMarker(entry.ticker, currentPointIndex, price, false);
                        }
                        
                        // Update previous portfolio
                        previousPortfolio.put(entry.ticker, currentQty);
                    }
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

    public void updateAIPortfolio(String aiCash, List<PortfolioEntry> aiHoldings) {
        runOnFxThread(() -> {
            // Update AI cash label
            if (lblAICash != null) {
                try {
                    double cashAmount = Double.parseDouble(aiCash);
                    lblAICash.setText("AI Cash: £" + String.format("%.2f", cashAmount));
                } catch (NumberFormatException e) {
                    lblAICash.setText("AI Cash: £" + aiCash);
                }
            }

            // Detect AI trades by comparing holdings
            if (aiHoldings != null) {
                for (PortfolioEntry entry : aiHoldings) {
                    int previousQty = previousAIPortfolio.getOrDefault(entry.ticker, 0);
                    int currentQty = entry.quantity;
                    
                    if (currentQty != previousQty) {
                        double price = entry.currentPrice > 0 ? entry.currentPrice : lastPricePerTicker.getOrDefault(entry.ticker, 0.0);
                        
                        if (currentQty > previousQty) {
                            // AI BUY detected
                            System.out.println("AI TRADE DETECTED: BUY " + entry.ticker + " (+" + (currentQty - previousQty) + ") at £" + price);
                            addAITradeMarker(entry.ticker, currentPointIndex, price, true);
                        } else if (currentQty < previousQty) {
                            // AI SELL detected
                            System.out.println("AI TRADE DETECTED: SELL " + entry.ticker + " (-" + (previousQty - currentQty) + ") at £" + price);
                            addAITradeMarker(entry.ticker, currentPointIndex, price, false);
                        }
                        
                        // Update previous AI portfolio
                        previousAIPortfolio.put(entry.ticker, currentQty);
                    }
                }
            }

            // Update AI holdings table
            if (tableAIHoldings != null) {
                ObservableList<PortfolioItem> items = FXCollections.observableArrayList();

                if (aiHoldings != null) {
                    for (PortfolioEntry entry : aiHoldings) {
                        // Use server's price if available, otherwise use last streamed price
                        double price = entry.currentPrice > 0 ? entry.currentPrice : lastPricePerTicker.getOrDefault(entry.ticker, 0.0);
                        double value = entry.quantity * price;
                        items.add(new PortfolioItem(
                            entry.ticker,
                            entry.quantity,
                            String.format("£%.2f", value)
                        ));
                    }
                }

                tableAIHoldings.setItems(items);
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