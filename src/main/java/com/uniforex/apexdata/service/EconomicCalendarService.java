package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EconomicCalendarService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        System.out.println("[SYSTEM] Attempting calendar fetch via ForexFactory Public JSON CDN...");

        String url = "https://nfs.faireconomy.media/ff_calendar_thisweek.json";
        String response = client.fetchRawJson(url);
        JsonNode events = mapper.readTree(response);

        if (events == null || !events.isArray()) {
            throw new Exception("ForexFactory CDN returned invalid or empty data.");
        }

        for (JsonNode node : events) {
            String country = node.path("country").asText("");
            if (!"USD".equalsIgnoreCase(country)) {
                continue;
            }

            String title = node.path("title").asText("").toLowerCase().trim();
            String actualText = cleanString(node.path("actual").asText(""));
            String forecastText = cleanString(node.path("forecast").asText(""));

            // 1. Skip if the event hasn't actually happened yet (actual is empty or dash)
            if (actualText.isEmpty() || actualText.equals("-")) {
                continue;
            }

            double actual = parseValue(actualText);
            // Default estimate to actual if no consensus was published, preventing 0.0 skew
            double estimate = (forecastText.isEmpty() || forecastText.equals("-")) ? actual : parseValue(forecastText);

            // 2. Strict Title Matching to prevent Core / MoM / YoY collisions
            if (title.contains("adp") && title.contains("employment")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if ((title.contains("non-farm") || title.contains("nonfarm")) && !title.contains("payroll")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("retail sales") && !title.contains("core")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("unemployment claims") || title.contains("jobless claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("ppi") && !title.contains("core") && (title.contains("m/m") || !title.contains("y/y"))) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("average hourly earnings") || (title.contains("wage") && title.contains("m/m"))) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("core pce price index") && title.contains("m/m")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("industrial production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("consumer sentiment") || title.contains("uom consumer sentiment")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("ism manufacturing pmi") || title.contains("manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("ism services pmi") || title.contains("services pmi")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("jolts")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("cpi") && !title.contains("core") && (title.contains("y/y") || title.contains("annual"))) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("gdp") && (title.contains("advance") || title.contains("final") || title.contains("preliminary") || title.contains("q/q"))) {
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }

        System.out.println("[SYSTEM] Calendar parsed successfully. USD metrics captured: " + uniqueMetrics.size());
        return new ArrayList<>(uniqueMetrics.values());
    }

    private String cleanString(String input) {
        if (input == null) return "";
        return input.replace('\u00A0', ' ') // Replace non-breaking space
                .replace('\u2013', '-') // Replace en-dash
                .replace('\u2014', '-') // Replace em-dash
                .replace('\u2212', '-') // Replace math minus sign
                .trim();
    }

    private double parseValue(String val) {
        if (val == null || val.isEmpty()) return 0.0;

        // Clean HTML artifacts, percentage signs, and commas
        val = cleanString(val).replaceAll("<[^>]*>", "").replaceAll("[,%]", "").trim();
        double multiplier = 1.0;
        String lowerVal = val.toLowerCase();

        if (lowerVal.endsWith("k")) {
            multiplier = 1_000.0;
            val = lowerVal.replace("k", "");
        } else if (lowerVal.endsWith("m")) {
            multiplier = 1_000_000.0;
            val = lowerVal.replace("m", "");
        } else if (lowerVal.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            val = lowerVal.replace("b", "");
        }

        try {
            return Double.parseDouble(val.trim()) * multiplier;
        } catch (NumberFormatException e) {
            System.err.println("[CALENDAR WARN] Could not parse numerical value from string: '" + val + "'");
            return 0.0;
        }
    }
}