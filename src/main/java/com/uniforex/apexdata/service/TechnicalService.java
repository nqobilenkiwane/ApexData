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

    public TechnicalService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public TechnicalData fetchUsdTechnicals() {
        try {
            // Swap to DX=F (US Dollar Index Futures) to avoid Yahoo's DX-Y.NYB 500 errors
            AssetTechnicalData dxy = fetchSeriesAndCalculateMetrics("DX=F");

            // Removed the / 10.0 division since Yahoo now quotes exact percentages
            double yield10Y = fetchCurrentPrice("^TNX");
            double yield2Y = fetchCurrentPrice("^IRX");

            return new TechnicalData(dxy.currentPrice(), dxy.sma200(), dxy.rsi14(), yield10Y, yield2Y);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch USD technicals from Yahoo Finance.", e);
        }
    }

    public AssetTechnicalData fetchGoldTechnicals() {
        try {
            return fetchSeriesAndCalculateMetrics("GC=F");
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch XAU/USD data from Yahoo Finance.", e);
        }
    }

    private AssetTechnicalData fetchSeriesAndCalculateMetrics(String ticker) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + ticker + "?range=1y&interval=1d";
        String response = client.fetchRawJson(url);
        JsonNode root = mapper.readTree(response);
        JsonNode result = root.path("chart").path("result").get(0);

        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").get(0);
        JsonNode closes = quote.path("close");

        BarSeries series = new BaseBarSeriesBuilder().withName(ticker).build();

        for (int i = 0; i < timestamps.size(); i++) {
            if (!closes.get(i).isNull()) {
                long time = timestamps.get(i).asLong();
                ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("UTC"));
                double close = closes.get(i).asDouble();
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

    private double fetchCurrentPrice(String ticker) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + ticker + "?range=1d&interval=1d";
        String response = client.fetchRawJson(url);
        JsonNode root = mapper.readTree(response);
        JsonNode closes = root.path("chart").path("result").get(0).path("indicators").path("quote").get(0).path("close");

        for (int i = closes.size() - 1; i >= 0; i--) {
            if (!closes.get(i).isNull()) return closes.get(i).asDouble();
        }
        return 0.0;
    }

    public record TechnicalData(double currentPrice, double sma200, double rsi14, double yield10Y, double yield2Y) {}
    public record AssetTechnicalData(double currentPrice, double sma200, double rsi14) {}
}