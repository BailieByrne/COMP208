import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

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

    // Support multiple tickers
    private final Map<String, XYChart.Series<Number, Number>> tickerSeries = new HashMap<>();
    private final Map<String, XYChart.Series<Number, Number>> aiTickerSeries = new HashMap<>();
    private final List<String> tickerOrder = new ArrayList<>();
    private String currentTicker = null;
    private double highestPriceSeen = 0.0;
    private double highestAIPriceSeen = 0.0;
    private static final int MAX_POINTS = 510;  // Full trading day

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
    }

    public void setConnectionStatus(String text) {
        runOnFxThread(() -> {
            if (labelTime != null) {
                labelTime.setText(text);
            }
        });
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
                updateYAxis(price);
            }

            // Update labels
            if (labelDay != null) {
                labelDay.setText("Tickers: " + String.join(" | ", tickerOrder));
            }
            if (labelTime != null) {
                labelTime.setText("Current ticker: " + displayTicker + " price: £" + String.format("%.2f", price));
            }
            if (lblCash != null) {
                lblCash.setText("Price: £" + String.format("%.2f", price));
            }
            if (labelNetWorth != null) {
                labelNetWorth.setText("Points: " + series.getData().size());
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

    private void trimSeries(XYChart.Series<Number, Number> series) {
        if (series.getData().size() > MAX_POINTS) {
            series.getData().remove(0, series.getData().size() - MAX_POINTS);
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
}
