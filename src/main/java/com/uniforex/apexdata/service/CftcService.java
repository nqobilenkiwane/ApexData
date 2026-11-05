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
import java.util.Collections;
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

    public List<MarketMetric> fetchInstitutionalData() {
        return fetchCotMetrics("098662");
    }

    public List<MarketMetric> fetchGoldInstitutionalData() {
        return fetchCotMetrics("088691");
    }

    public List<MarketMetric> fetchNasdaqInstitutionalData() {
        return fetchCotMetrics("20974%2B");
    }

    public List<MarketMetric> fetchDowInstitutionalData() {
        return fetchCotMetrics("12460%2B");
    }

    public List<MarketMetric> fetchSilverInstitutionalData() {
        return fetchCotMetrics("084691");
    }

    public List<MarketMetric> fetchSp500InstitutionalData() {
        return fetchCotMetrics("13874A");
    }

    public List<MarketMetric> fetchRussell2000InstitutionalData() {
        return fetchCotMetrics("239742");
    }

    private List<MarketMetric> fetchCotMetrics(String contractCode) {
        try {
            String url = "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code="
                    + contractCode + "&$order=report_date_as_yyyy_mm_dd%20DESC&$limit=2";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            // Null check and ensure it is a valid array returned from the CFTC API
            if (root == null || !root.isArray() || root.size() < 2) {
                System.err.println("[WARNING] fetchCotMetrics: Invalid or insufficient data returned for code " + contractCode + ". Returning empty list to prevent pipeline crash.");
                return Collections.emptyList();
            }

            JsonNode currentWeek = root.get(0);
            if (currentWeek == null || !currentWeek.has("noncomm_positions_long_all")) {
                System.err.println("[WARNING] fetchCotMetrics: Missing expected fields for code " + contractCode);
                return Collections.emptyList();
            }

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

        } catch (Exception e) {
            System.err.println("[ERROR] fetchCotMetrics failed for code " + contractCode + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public GoldCotData fetchGoldCotData() {
        try {
            String url = "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code=088691&$order=report_date_as_yyyy_mm_dd%20DESC&$limit=2";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            if (root == null || !root.isArray() || root.size() < 2) {
                System.err.println("[WARNING] fetchGoldCotData: Invalid CFTC data returned from Socrata API. Returning zero-value fallback data.");
                return new GoldCotData(0, 0, 0);
            }

            JsonNode currentWeek = root.get(0);
            long currentLong = currentWeek.get("noncomm_positions_long_all").asLong();
            long currentShort = currentWeek.get("noncomm_positions_short_all").asLong();

            JsonNode previousWeek = root.get(1);
            long prevLong = previousWeek.get("noncomm_positions_long_all").asLong();
            long prevShort = previousWeek.get("noncomm_positions_short_all").asLong();
            long previousNet = prevLong - prevShort;

            return new GoldCotData(currentLong, currentShort, previousNet);

        } catch (Exception e) {
            System.err.println("[ERROR] fetchGoldCotData failed: " + e.getMessage());
            return new GoldCotData(0, 0, 0);
        }
    }

    public record GoldCotData(long nonCommercialLongs, long nonCommercialShorts, long previousNetPosition) {}
}