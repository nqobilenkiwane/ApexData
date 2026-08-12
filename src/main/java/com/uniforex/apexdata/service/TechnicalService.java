package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TechnicalService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final String apiKey;

    public TechnicalService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    // DTO to hold the final technical output
    public record TechnicalData(double currentPrice, double sma200, double rsi14) {}

    public TechnicalData fetchUsdTechnicals() throws Exception {
        // Fetch USD/EUR daily prices
        String url = String.format(
                "https://www.alphavantage.co/query?function=FX_DAILY&from_symbol=USD&to_symbol=EUR&outputsize=full&apikey=%s",
                apiKey
        );

        String json = client.fetchRawJson(url);
        JsonNode rootNode = mapper.readTree(json);
        JsonNode timeSeriesNode = rootNode.get("Time Series FX (Daily)");

        if (timeSeriesNode == null) {
            throw new RuntimeException("Failed to fetch Alpha Vantage data. Check your API key or rate limits.");
        }

        // ta4j requires time series data to be added chronologically (oldest to newest)
        List<String> dates = new ArrayList<>();
        timeSeriesNode.fieldNames().forEachRemaining(dates::add);
        Collections.sort(dates);

        // Build the ta4j BarSeries
        BarSeries series = new BaseBarSeriesBuilder().withName("USD_EUR").build();

        for (String dateString : dates) {
            JsonNode dailyNode = timeSeriesNode.get(dateString);
            double open = dailyNode.get("1. open").asDouble();
            double high = dailyNode.get("2. high").asDouble();
            double low = dailyNode.get("3. low").asDouble();
            double close = dailyNode.get("4. close").asDouble();

            // Convert string date to ZonedDateTime for ta4j
            LocalDate date = LocalDate.parse(dateString);
            ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

            // Add the daily candle to our series (Volume is 0 for forex)
            series.addBar(zdt, open, high, low, close, 0.0);
        }

        // Initialize Indicators
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma200 = new SMAIndicator(closePrice, 200);
        RSIIndicator rsi14 = new RSIIndicator(closePrice, 14);

        // Extract the values from the very last (most recent) bar in the series
        int endIndex = series.getEndIndex();

        return new TechnicalData(
                closePrice.getValue(endIndex).doubleValue(),
                sma200.getValue(endIndex).doubleValue(),
                rsi14.getValue(endIndex).doubleValue()
        );
    }
}