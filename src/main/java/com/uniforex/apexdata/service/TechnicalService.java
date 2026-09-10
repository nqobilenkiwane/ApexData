package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TechnicalService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;

    // Removed the API key requirement completely
    public TechnicalService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public TechnicalData fetchUsdTechnicals() {
        try {
            // 1. Fetch DXY (US Dollar Index) Technicals
            AssetTechnicalData dxy = fetchSeriesAndCalculateMetrics("DX-Y.NYB");

            // 2. Fetch US Treasury Yields (Current price only needed for yields)
            // ^TNX is quoted as Yield * 10 (e.g., 43.9 = 4.39%)
            double yield10Y = fetchCurrentPrice("^TNX") / 10.0;

            // Using 13-week IRX as short-end proxy (can swap to ^FVX for 5-year)
            double yield2Y = fetchCurrentPrice("^IRX") / 10.0;

            return new TechnicalData(dxy.currentPrice(), dxy.sma200(), dxy.rsi14(), yield10Y, yield2Y);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch USD technicals from Yahoo Finance.", e);
        }
    }

    public AssetTechnicalData fetchGoldTechnicals() {
        try {
            return fetchSeriesAndCalculateMetrics("XAUUSD=X");
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch XAU/USD data from Yahoo Finance.", e);
        }
    }

    /**
     * Generic fetcher for any Yahoo Finance ticker.
     * Replaces the old Alpha Vantage daily FX fetcher.
     */
    private AssetTechnicalData fetchSeriesAndCalculateMetrics(String ticker) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + ticker + "?range=1y&interval=1d";
        String response = client.fetchRawJson(url);
        JsonNode root = mapper.readTree(response);
        JsonNode result = root.path("chart").path("result").get(0);

        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").get(0);
        JsonNode closes = quote.path("close");

        BarSeries series = new BaseBarSeriesBuilder().withName(ticker).build();

        // Yahoo Finance uses flat arrays. We iterate chronologically to build the ta4j series.
        for (int i = 0; i < timestamps.size(); i++) {
            // Yahoo occasionally returns null values for market half-days or glitches
            if (!closes.get(i).isNull()) {
                long time = timestamps.get(i).asLong();
                ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("UTC"));
                double close = closes.get(i).asDouble();

                // Using close price for all OHLC fields since we only calculate Close-based SMA/RSI
                series.addBar(zdt, close, close, close, close, 0);
            }
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma200 = new SMAIndicator(closePrice, 200);
        RSIIndicator rsi14 = new RSIIndicator(closePrice, 14);

        int lastIndex = series.getEndIndex();
        return new AssetTechnicalData(
                closePrice.getValue(lastIndex).doubleValue(),
                sma200.getValue(lastIndex).doubleValue(),
                rsi14.getValue(lastIndex).doubleValue()
        );
    }

    /**
     * Lightweight fetcher for yields where we don't need 200 days of history.
     */
    private double fetchCurrentPrice(String ticker) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + ticker + "?range=1d&interval=1d";
        String response = client.fetchRawJson(url);
        JsonNode root = mapper.readTree(response);
        JsonNode closes = root.path("chart").path("result").get(0).path("indicators").path("quote").get(0).path("close");

        // Return the most recent non-null close price
        for (int i = closes.size() - 1; i >= 0; i--) {
            if (!closes.get(i).isNull()) return closes.get(i).asDouble();
        }
        return 0.0;
    }

    public record TechnicalData(double currentPrice, double sma200, double rsi14, double yield10Y, double yield2Y) {}
    public record AssetTechnicalData(double currentPrice, double sma200, double rsi14) {}
}