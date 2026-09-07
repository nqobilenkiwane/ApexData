package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

/**
 * Service responsible for fetching and analyzing institutional market positioning data
 * from the Commodity Futures Trading Commission (CFTC).
 */
public class CftcService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    /**
     * Initializes the CFTC service with necessary network dependencies.
     */
    public CftcService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Fetches the latest two weeks of Commitments of Traders (COT) data for the USD Index (098662).
     */
    public List<MarketMetric> fetchInstitutionalData() throws Exception {
        String url = "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code=098662&$order=report_date_as_yyyy_mm_dd%20DESC&$limit=2";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        if (root.isEmpty() || root.size() < 2) {
            throw new RuntimeException("Insufficient CFTC data returned from Socrata API");
        }

        JsonNode currentWeek = root.get(0);
        double currentLong = currentWeek.get("noncomm_positions_long_all").asDouble();
        double currentShort = currentWeek.get("noncomm_positions_short_all").asDouble();

        double currentNet = currentLong - currentShort;
        double currentLongPercentage = (currentLong / (currentLong + currentShort)) * 100.0;

        JsonNode previousWeek = root.get(1);
        double prevLong = previousWeek.get("noncomm_positions_long_all").asDouble();
        double prevShort = previousWeek.get("noncomm_positions_short_all").asDouble();
        double previousNet = prevLong - prevShort;

        return Arrays.asList(
                new MarketMetric("COT Net Positioning", currentNet, 0.0, 0, MetricCategory.INSTITUTIONAL_ACTIVITY),
                new MarketMetric("COT WoW Delta", currentNet, previousNet, 0, MetricCategory.INSTITUTIONAL_ACTIVITY),
                new MarketMetric("COT Long Percentage", currentLongPercentage, 0.0, 0, MetricCategory.INSTITUTIONAL_ACTIVITY)
        );
    }

    /**
     * Fetches COT positioning specifically for COMEX Gold Futures (088691).
     * Extracts raw long/short contracts to feed into the Gold composite scoring engine.
     */
    public GoldCotData fetchGoldCotData() throws Exception {
        String url = "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code=088691&$order=report_date_as_yyyy_mm_dd%20DESC&$limit=2";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        if (root.isEmpty() || root.size() < 2) {
            throw new RuntimeException("Insufficient CFTC data returned for Gold from Socrata API");
        }

        JsonNode currentWeek = root.get(0);
        long currentLong = currentWeek.get("noncomm_positions_long_all").asLong();
        long currentShort = currentWeek.get("noncomm_positions_short_all").asLong();

        JsonNode previousWeek = root.get(1);
        long prevLong = previousWeek.get("noncomm_positions_long_all").asLong();
        long prevShort = previousWeek.get("noncomm_positions_short_all").asLong();
        long previousNet = prevLong - prevShort;

        return new GoldCotData(currentLong, currentShort, previousNet);
    }

    /**
     * Immutable data carrier for Gold institutional positioning.
     */
    public record GoldCotData(long nonCommercialLongs, long nonCommercialShorts, long previousNetPosition) {}
}