package com.uniforex.apexdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomicCalendarService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final String fmpApiKey;

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        // Inject your FMP Key here. If passed from EngineScheduler, update the constructor there.
        this.fmpApiKey = "Zbp00DnAZP6aZILfRCRAOpz7eeZ1Mvnj";
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        // 1. Calculate the current week's Monday and Friday for the API parameters
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate friday = today.with(DayOfWeek.FRIDAY);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String fromDate = monday.format(dtf);
        String toDate = friday.format(dtf);

        // 2. Call the FMP Economic Calendar Endpoint
        String endpoint = String.format(
                "https://financialmodelingprep.com/api/v3/economic_calendar?from=%s&to=%s&apikey=%s",
                fromDate, toDate, fmpApiKey
        );

        String jsonResponse = client.fetchRawJson(endpoint);

        List<Map<String, Object>> events = mapper.readValue(jsonResponse, new TypeReference<List<Map<String, Object>>>() {});
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        for (Map<String, Object> e : events) {
            String country = (String) e.get("country");
            if (!"US".equalsIgnoreCase(country)) {
                continue;
            }

            // FMP returns actual and estimate as numbers, not strings
            Object actualObj = e.get("actual");
            Object estimateObj = e.get("estimate");

            if (actualObj == null || estimateObj == null) {
                continue; // Skip if the event hasn't happened or lacks a forecast
            }

            double actual = Double.parseDouble(actualObj.toString());
            double estimate = Double.parseDouble(estimateObj.toString());

            String eventName = ((String) e.get("event")).toLowerCase();

            // 3. Map FMP titles to ApexData Engine metrics
            if (eventName.contains("non farm payrolls") || eventName.contains("nonfarm payrolls")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("retail sales mom")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("initial jobless claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("ppi mom") || eventName.contains("producer price index")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("average hourly earnings")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("core pce price index")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("industrial production mom")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("cb consumer confidence") || eventName.contains("michigan consumer sentiment")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("ism manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("ism non-manufacturing pmi") || eventName.contains("ism services pmi")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("jolts job openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("adp employment change")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("inflation rate yoy")) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("gdp growth rate")) {
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


