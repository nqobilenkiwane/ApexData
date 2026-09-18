package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EconomicCalendarService {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apifyToken;

    public EconomicCalendarService(ObjectMapper mapper, @Value("${apify.token}") String apifyToken) {
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = mapper;
        this.apifyToken = apifyToken;
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        System.out.println("[SYSTEM] Attempting calendar fetch via Store Actor (xtracto~forex-factory-calendar)...");

        // Use the raw actor hash ID found in your Apify browser URL
        String apifyUrl = "https://api.apify.com/v2/actors/xtracto~forexfactory-calendar/run-sync-get-dataset-items?token=" + apifyToken;

        // Provide the input payload expected by the store actor
        String jsonInputBody = "{\n" +
                "  \"dateRange\": \"thisweek\",\n" +
                "  \"currencies\": [\"USD\"],\n" +
                "  \"minImpact\": \"medium\"\n" +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apifyUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonInputBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201 && response.statusCode() != 200) {
            throw new RuntimeException("Apify Store Actor returned error code: " + response.statusCode() + " - " + response.body());
        }

        JsonNode events = mapper.readTree(response.body());

        if (events == null || !events.isArray()) {
            throw new Exception("Apify Store Actor returned invalid or empty dataset.");
        }

        for (JsonNode node : events) {
            // The store actor uses 'currency' (e.g., "USD")
            String currency = node.path("currency").asText("");
            if (!"USD".equalsIgnoreCase(currency)) {
                continue;
            }

            String title = node.path("title").asText("").toLowerCase().trim();
            String actualText = cleanString(node.path("actual").asText(""));
            String forecastText = cleanString(node.path("forecast").asText(""));

            if (actualText.isEmpty() || actualText.equals("-") || actualText.equals("null")) {
                continue;
            }

            double actual = parseValue(actualText);
            double estimate = (forecastText.isEmpty() || forecastText.equals("-") || forecastText.equals("null")) ? actual : parseValue(forecastText);

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

        System.out.println("[SYSTEM] Store Actor calendar parsed successfully. USD metrics captured: " + uniqueMetrics.size());
        return new ArrayList<>(uniqueMetrics.values());
    }

    private String cleanString(String input) {
        if (input == null) return "";
        return input.replace('\u00A0', ' ')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-')
                .trim();
    }

    private double parseValue(String val) {
        if (val == null || val.isEmpty()) return 0.0;

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