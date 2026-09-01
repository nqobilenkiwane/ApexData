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
    private final String finnhubApiKey;

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        // PASTE YOUR FREE FINNHUB TOKEN HERE
        this.finnhubApiKey = "dabjfm1r01qvvgl5eb00dabjfm1r01qvvgl5eb0g";
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate friday = today.with(DayOfWeek.FRIDAY);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String fromDate = monday.format(dtf);
        String toDate = friday.format(dtf);

        // Fetch Finnhub Economic Calendar endpoint
        String endpoint = String.format(
                "https://finnhub.io/api/v1/calendar/economic?from=%s&to=%s&token=%s",
                fromDate, toDate, finnhubApiKey
        );

        String jsonResponse = client.fetchRawJson(endpoint);

        // Finnhub wraps the data inside an "economicCalendar" array
        Map<String, Object> root = mapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) root.get("economicCalendar");

        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        if (events == null || events.isEmpty()) {
            System.out.println("[WARNING] Finnhub returned an empty calendar array.");
            return new ArrayList<>();
        }

        for (Map<String, Object> e : events) {
            String country = (String) e.get("country");

            // Finnhub uses "US" instead of "USD"
            if (!"US".equalsIgnoreCase(country)) {
                continue;
            }

            Object actualObj = e.get("actual");
            Object estimateObj = e.get("estimate");

            if (actualObj == null || estimateObj == null) {
                continue;
            }

            double actual = Double.parseDouble(actualObj.toString());
            double estimate = Double.parseDouble(estimateObj.toString());

            String eventName = ((String) e.get("event")).toLowerCase();

            // Keyword mapping for ApexData Engine metrics
            if (eventName.contains("nonfarm payrolls")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("retail sales")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("initial jobless claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("producer price index") || eventName.contains("ppi mom")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("average hourly earnings")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("core pce")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventName.contains("industrial production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("consumer confidence") || eventName.contains("consumer sentiment")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("ism manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("ism non-manufacturing") || eventName.contains("ism services")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("jolts")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("adp employment")) {
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
        // Strip HTML tags and commas/percentages
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