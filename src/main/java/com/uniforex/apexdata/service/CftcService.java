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

public class CftcService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public CftcService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        // Native HTTP client handles the raw Socrata fetch perfectly
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<MarketMetric> fetchInstitutionalData() throws Exception {
        // CFTC API: U.S. Dollar Index (098662). Ordered by date DESC, limited to 2 to get WoW data.
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

        // 1. Current Week Data (Index 0)
        JsonNode currentWeek = root.get(0);
        double currentLong = currentWeek.get("noncomm_positions_long_all").asDouble();
        double currentShort = currentWeek.get("noncomm_positions_short_all").asDouble();

        // Net Positioning = Longs minus Shorts
        double currentNet = currentLong - currentShort;

        // Sentiment = % of total non-commercial volume that is Long
        double currentLongPercentage = (currentLong / (currentLong + currentShort)) * 100.0;

        // 2. Previous Week Data (Index 1)
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
}