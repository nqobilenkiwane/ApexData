package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TechnicalService {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public TechnicalService(ObjectMapper mapper) {
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = mapper;
    }

    /**
     * Fetches historical daily bars for US Dollar Index (DX-Y.NYB)
     * and computes 200 SMA and 14 RSI natively via ta4j.
     */
    public AssetTechnicalData fetchUsdTechnicals() {
        try {
            return fetchSeriesAndCalculateMetrics("DX-Y.NYB");
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

        // Use native HttpClient to append the required User-Agent header
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Yahoo Finance returned status: " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
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