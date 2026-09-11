package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomicCalendarService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final String apiKey;

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        // Allows fallback to system environments like Railway
        this.apiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : System.getenv("FINNHUB_API_KEY");
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        System.out.println("[SYSTEM] Attempting calendar fetch via Finnhub API...");

        // Fetch a rolling 7-day window to capture recently passed and upcoming events
        String from = LocalDate.now().minusDays(2).format(DateTimeFormatter.ISO_DATE);
        String to = LocalDate.now().plusDays(5).format(DateTimeFormatter.ISO_DATE);

        String url = String.format("https://finnhub.io/api/v1/calendar/economic?from=%s&to=%s&token=%s", from, to, this.apiKey);

        String response = client.fetchRawJson(url);
        JsonNode root = mapper.readTree(response);
        JsonNode economicCalendar = root.path("economicCalendar");

        if (economicCalendar.isMissingNode() || !economicCalendar.isArray()) {
            throw new Exception("Finnhub failed to load the calendar or timed out.");
        }

        for (JsonNode node : economicCalendar) {
            String eventCountry = node.path("country").asText("");

            // Replicates your original USD logic
            if (!"US".equalsIgnoreCase(eventCountry)) {
                continue;
            }

            String eventTitle = node.path("event").asText("").toLowerCase();

            // Finnhub returns strict JSON numbers, completely replacing your custom parseValue() method
            double actual = node.path("actual").asDouble(0.0);
            double estimate = node.path("estimate").asDouble(0.0);

            // Skip zeroed-out lines if an event is blank
            if (actual == 0.0 && estimate == 0.0) {
                continue;
            }

            // 1. Check for ADP FIRST to prevent shadowing
            if (eventTitle.contains("adp")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));

                // Expanded string matching to catch Finnhub's specific terminology
            } else if (eventTitle.contains("nonfarm") || eventTitle.contains("non-farm employment") || eventTitle.contains("nfp")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("retail sales") || eventTitle.contains("core retail sales")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("jobless claims") || eventTitle.contains("unemployment claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("ppi") || eventTitle.contains("core ppi")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("average hourly earnings") || eventTitle.contains("wage")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("core pce")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("industrial production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("consumer sentiment") || eventTitle.contains("consumer confidence")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("services pmi")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("jolts") || eventTitle.contains("job openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("cpi")) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("gdp")) {
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }

        System.out.println("[SYSTEM] Successfully connected and retrieved JSON via Finnhub API.");
        return new ArrayList<>(uniqueMetrics.values());
    }
}