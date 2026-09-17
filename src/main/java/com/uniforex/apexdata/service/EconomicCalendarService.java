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
    private final String rapidApiKey;
    private final String rapidApiHost = "ultimate-economic-calendar.p.rapidapi.com";

    public EconomicCalendarService(ObjectMapper mapper, @Value("${rapidapi.key}") String rapidApiKey) {
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = mapper;
        this.rapidApiKey = rapidApiKey;
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();
        System.out.println("[SYSTEM] Attempting calendar fetch via RapidAPI...");

        String url = "https://" + rapidApiHost + "/economic-events";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("RapidAPI returned error code: " + response.statusCode() + " - " + response.body());
        }

        JsonNode events = mapper.readTree(response.body());

        if (events == null || !events.isArray()) {
            throw new Exception("RapidAPI returned invalid or empty data.");
        }

        for (JsonNode node : events) {
            String currency = node.path("currency").asText("");
            if (!"USD".equalsIgnoreCase(currency)) {
                continue;
            }

            String title = node.path("title").asText("").toLowerCase().trim();
            String actualText = node.path("actual").asText("");
            String forecastText = node.path("forecast").asText("");

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

        System.out.println("[SYSTEM] Calendar parsed successfully. USD metrics captured: " + uniqueMetrics.size());
        return new ArrayList<>(uniqueMetrics.values());
    }

    private double parseValue(String val) {
        if (val == null || val.isEmpty()) return 0.0;

        // Clean non-numeric characters while preserving negatives and decimals
        val = val.replaceAll("<[^>]*>", "").replaceAll("[^\\d.-]", "").trim();
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}