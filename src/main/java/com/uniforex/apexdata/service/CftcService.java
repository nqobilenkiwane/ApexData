package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

@Service
public class CftcService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public CftcService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Fetches COT positioning for USD Index (098662).
     */
    public List<MarketMetric> fetchInstitutionalData() throws Exception {
        return fetchCotMetrics("098662");
    }

    /**
     * Fetches COT positioning for COMEX Gold Futures (088691) as MarketMetrics.
     * Uses standard metric names so CompositeScoringEngine.applyScores() works seamlessly.
     */
    public List<MarketMetric> fetchGoldInstitutionalData() throws Exception {
        return fetchCotMetrics("088691");
    }

    /**
     * Reusable COT fetcher. Leaves scoring to CompositeScoringEngine.
     */
    private List<MarketMetric> fetchCotMetrics(String contractCode) throws Exception {
        String url = "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code="
                + contractCode + "&$order=report_date_as_yyyy_mm_dd%20DESC&$limit=2";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        if (root.isEmpty() || root.size() < 2) {
            throw new RuntimeException("Insufficient CFTC data returned for code " + contractCode);
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
     * Preserved for CompositeScoringEngine.scoreGoldCot(...) in the top scorecard banner.
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

    public record GoldCotData(long nonCommercialLongs, long nonCommercialShorts, long previousNetPosition) {}
}