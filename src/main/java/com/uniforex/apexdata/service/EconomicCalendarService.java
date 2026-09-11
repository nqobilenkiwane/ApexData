package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomicCalendarService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        System.out.println("[SYSTEM] Attempting calendar fetch via ForexFactory Public JSON CDN...");

        String url = "https://nfs.faireconomy.media/ff_calendar_thisweek.json";

        String response = client.fetchRawJson(url);
        JsonNode events = mapper.readTree(response);

        if (events.isMissingNode() || !events.isArray()) {
            throw new Exception("ForexFactory CDN returned invalid data.");
        }

        for (JsonNode node : events) {
            String country = node.path("country").asText("");

            if (!"USD".equalsIgnoreCase(country)) {
                continue;
            }

            String title = node.path("title").asText("").toLowerCase();

            String actualText = node.path("actual").asText("");
            String forecastText = node.path("forecast").asText("");

            // CRITICAL FIX: Reverted to || to prevent upcoming events from overwriting the DB with 0.0
            if (actualText.isEmpty() || forecastText.isEmpty()) {
                continue;
            }

            double actual = parseValue(actualText);
            double estimate = parseValue(forecastText);

            if (title.contains("adp")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("nonfarm") || title.contains("non-farm employment") || title.contains("nfp")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("retail sales") || title.contains("core retail sales")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("jobless claims") || title.contains("unemployment claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("ppi") || title.contains("core ppi")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("average hourly earnings") || title.contains("wage")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("core pce")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("industrial production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("consumer sentiment") || title.contains("consumer confidence")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("services pmi")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("jolts") || title.contains("job openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("cpi")) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("gdp")) {
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }

        System.out.println("[SYSTEM] Successfully connected and retrieved JSON via ForexFactory CDN.");
        return new ArrayList<>(uniqueMetrics.values());
    }

    private double parseValue(String val) {
        if (val == null || val.isEmpty()) return 0.0;

        val = val.replaceAll("<[^>]*>", "").replaceAll("[,%]", "").trim();
        double multiplier = 1.0;
        String lowerVal = val.toLowerCase();

        if (lowerVal.endsWith("k")) {
            multiplier = 1000.0;
            val = lowerVal.replace("k", "");
        } else if (lowerVal.endsWith("m")) {
            multiplier = 1000000.0;
            val = lowerVal.replace("m", "");
        } else if (lowerVal.endsWith("b")) {
            multiplier = 1000000000.0;
            val = lowerVal.replace("b", "");
        }

        try {
            return Double.parseDouble(val.trim()) * multiplier;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}