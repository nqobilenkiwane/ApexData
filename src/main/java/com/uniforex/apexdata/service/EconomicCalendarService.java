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

    // The API key is no longer needed for this free public feed!
    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {

        // 1. Fetch the live, public JSON calendar feed from Forex Factory
        String endpoint = "https://nfs.faireconomy.media/ff_calendar_thisweek.json";
        String jsonResponse = client.fetchRawJson(endpoint);

        // Map it to a generic list of maps since the Forex Factory structure is flat
        List<Map<String, String>> events = mapper.readValue(jsonResponse, new TypeReference<List<Map<String, String>>>() {});
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        for (Map<String, String> e : events) {

            // 2. Filter for US Dollar events only
            String country = e.get("country");
            if (!"USD".equalsIgnoreCase(country)) {
                continue;
            }

            String eventName = e.get("title");
            String actualStr = e.get("actual");
            String forecastStr = e.get("forecast");

            // Skip events that haven't happened yet or have no estimate
            if (actualStr == null || actualStr.isEmpty() || forecastStr == null || forecastStr.isEmpty()) {
                continue;
            }

            double actual = parseValue(actualStr);
            double estimate = parseValue(forecastStr);

            // 3. Map Forex Factory titles to your ApexData Engine naming conventions
            if (eventName.contains("Non-Farm Employment")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("Unemployment Rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("Retail Sales m/m") || eventName.contains("Core Retail Sales m/m")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("Unemployment Claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("PPI m/m") || eventName.contains("Core PPI m/m")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Average Hourly Earnings")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Core PCE")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Industrial Production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("CB Consumer Confidence") || eventName.contains("UoM Consumer Sentiment")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("ISM Manufacturing PMI")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("ISM Services PMI")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("JOLTS Job Openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("ADP Non-Farm")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("CPI y/y") || eventName.contains("CPI m/m")) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Advance GDP q/q")) {
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }
        return new ArrayList<>(uniqueMetrics.values());
    }

    // Helper method to strip strings (e.g. "4.1%", "85K", "-0.1M") into pure doubles
    private double parseValue(String val) {
        val = val.replaceAll("[,%KMB]", "").trim();

        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}