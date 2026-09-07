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
 * <p>
 * This class specifically targets the U.S. Dollar Index (Contract Code: 098662) to track
 * "smart money" sentiment by extracting the positions of non-commercial speculators
 * (e.g., hedge funds, CTAs) who drive overarching market momentum.
 */
public class CftcService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    /**
     * Initializes the CFTC service with necessary network dependencies.
     *
     * @param client Internal standard market data client.
     * @param mapper Jackson object mapper for parsing raw JSON payloads.
     */
    public CftcService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        // Native HTTP client handles the raw Socrata fetch perfectly
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Fetches the latest two weeks of Commitments of Traders (COT) data for the USD Index
     * to evaluate institutional market bias, momentum, and trade crowdedness.
     * <p>
     * <b>Metric Methodology:</b>
     * <ul>
     *   <li><b>Net Positioning:</b> Calculated as (Longs - Shorts). Dictates the absolute directional
     *       bias of institutional capital (Bullish vs. Bearish).</li>
     *   <li><b>WoW Delta (Momentum):</b> Compares current net positioning to the previous week's net.
     *       Tracks velocity to see if institutions are aggressively adding to or unwinding their bets.</li>
     *   <li><b>Long Percentage (Sentiment Extreme):</b> Calculated as (Longs / (Longs + Shorts)).
     *       Highlights extreme sentiment. If a trade becomes too heavily skewed (e.g., >80% Long),
     *       it becomes vulnerable to sharp contrarian reversals.</li>
     * </ul>
     *
     * @return A list of initialized {@link MarketMetric} objects representing Institutional Activity.
     * @throws Exception if the CFTC Socrata endpoint fails or returns an empty/malformed dataset.
     */
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