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

    // Expanded DTO to hold both FX technicals and Bond Yields
    public record TechnicalData(double currentPrice, double sma200, double rsi14, double yield2Y, double yield10Y) {}

    public TechnicalData fetchUsdTechnicals() throws Exception {
        // 1. Fetch Treasury Yields
        double yield2Y = fetchLatestYield("2year");
        double yield10Y = fetchLatestYield("10year");

        // 2. Fetch USD/EUR daily prices
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

        List<String> dates = new ArrayList<>();
        timeSeriesNode.fieldNames().forEachRemaining(dates::add);
        Collections.sort(dates);

        BarSeries series = new BaseBarSeriesBuilder().withName("USD_EUR").build();

        for (String dateString : dates) {
            JsonNode dailyNode = timeSeriesNode.get(dateString);
            double open = dailyNode.get("1. open").asDouble();
            double high = dailyNode.get("2. high").asDouble();
            double low = dailyNode.get("3. low").asDouble();
            double close = dailyNode.get("4. close").asDouble();

            LocalDate date = LocalDate.parse(dateString);
            ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));
            series.addBar(zdt, open, high, low, close, 0.0);
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma200 = new SMAIndicator(closePrice, 200);
        RSIIndicator rsi14 = new RSIIndicator(closePrice, 14);

        int endIndex = series.getEndIndex();

        return new TechnicalData(
                closePrice.getValue(endIndex).doubleValue(),
                sma200.getValue(endIndex).doubleValue(),
                rsi14.getValue(endIndex).doubleValue(),
                yield2Y,
                yield10Y
        );
    }

    // Helper method to extract Alpha Vantage's Treasury JSON structure
    private double fetchLatestYield(String maturity) {
        try {
            String url = String.format("https://www.alphavantage.co/query?function=TREASURY_YIELD&interval=daily&maturity=%s&apikey=%s", maturity, apiKey);
            String json = client.fetchRawJson(url);
            JsonNode rootNode = mapper.readTree(json);
            JsonNode dataNode = rootNode.get("data");

            if (dataNode != null && dataNode.isArray() && dataNode.size() > 0) {
                String val = dataNode.get(0).get("value").asText();
                // Alpha Vantage returns "." on bank holidays. Skip parsing if true.
                if (!".".equals(val)) {
                    return Double.parseDouble(val);
                }
            }
        } catch (Exception e) {
            System.err.println("[API Error] Failed to fetch " + maturity + " yield: " + e.getMessage());
        }
        return 0.0;
    }
}