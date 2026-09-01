package com.uniforex.apexdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
        String endpoint = "https://nfs.faireconomy.media/ff_calendar_thisweek.json";
        String jsonResponse = client.fetchRawJson(endpoint);

        List<Map<String, String>> events = mapper.readValue(jsonResponse, new TypeReference<List<Map<String, String>>>() {});
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        for (Map<String, String> e : events) {
            String country = e.get("country");
            if (!"USD".equalsIgnoreCase(country)) {
                continue;
            }

            String actualStr = e.get("actual");
            String forecastStr = e.get("forecast");

            if (actualStr == null || actualStr.isEmpty() || forecastStr == null || forecastStr.isEmpty()) {
                continue;
            }

            double actual = parseValue(actualStr);
            double estimate = parseValue(forecastStr);

            // Standardize string to lowercase for bulletproof matching
            String eventName = e.get("title").toLowerCase();

            if (eventName.contains("non-farm employment") || eventName.contains("nfp")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("retail sales") || eventName.contains("core retail sales")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("jobless claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("ppi m/m") || eventName.contains("core ppi")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("average hourly earnings")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("core pce")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("industrial production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("consumer confidence") || eventName.contains("consumer sentiment")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("services pmi")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("jolts job openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("adp non-farm")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("cpi y/y") || eventName.contains("cpi m/m")) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("advance gdp") || eventName.contains("real gdp")) {
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }
        return new ArrayList<>(uniqueMetrics.values());
    }

    private double parseValue(String val) {
        // 1. Strip hidden HTML tags (e.g., <span class="better">54.6</span>)
        val = val.replaceAll("<[^>]*>", "");
        // 2. Strip standard financial characters
        val = val.replaceAll("[,%KMB]", "").trim();

        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            System.err.println("[WARNING] Failed to parse value: " + val);
            return 0.0;
        }
    }
}