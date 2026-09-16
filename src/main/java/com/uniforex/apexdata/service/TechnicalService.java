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

    /**
     * Fetches historical daily bars for US Dollar Index Futures (DX=F)
     * and computes 200 SMA and 14 RSI natively via ta4j.
     */
    public AssetTechnicalData fetchUsdTechnicals() {
        try {
            return fetchSeriesAndCalculateMetrics("DX=F");
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch USD technicals from Yahoo Finance: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches historical daily bars for COMEX Gold Futures (GC=F)
     * and computes 200 SMA and 14 RSI natively via ta4j.
     */
    public AssetTechnicalData fetchGoldTechnicals() {
        try {
            return fetchSeriesAndCalculateMetrics("GC=F");
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch XAU/USD technicals from Yahoo Finance: " + e.getMessage(), e);
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

    /**
     * Immutable data carrier for asset technical momentum indicators.
     */
    public record AssetTechnicalData(double currentPrice, double sma200, double rsi14) {}
}