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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        System.out.println("[SYSTEM] Attempting calendar fetch via Store Actor (Investing.com)...");

        // The exact Investing.com actor hash ID.
        String apifyUrl = "https://api.apify.com/v2/actors/aaSpP7M39TUNh1Xto/run-sync-get-dataset-items?token=" + apifyToken;

        // Generate a rolling 14-day window dynamically so the data never goes stale
        LocalDate today = LocalDate.now();
        String fromDate = today.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toDate = today.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // The precise JSON payload required by the Investing.com actor
        String jsonInputBody = "{\n" +
                "  \"country\": \"united states\",\n" +
                "  \"fromDate\": \"" + fromDate + "\",\n" +
                "  \"importances\": \"\",\n" +
                "  \"timeFilter\": \"time_only\",\n" +
                "  \"toDate\": \"" + toDate + "\"\n" +
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
            String currency = node.path("currency").asText("");
            if (!"USD".equalsIgnoreCase(currency)) {
                continue;
            }

            // CRITICAL UPDATE: Investing.com stores the metric name in "event", not "title".
            String title = node.path("event").asText("").toLowerCase().trim();
            String actualText = cleanString(node.path("actual").asText(""));
            String forecastText = cleanString(node.path("forecast").asText(""));

            if (actualText.isEmpty() || actualText.equals("-") || actualText.equals("null")) {
                continue;
            }

            double actual = parseValue(actualText);
            double estimate = (forecastText.isEmpty() || forecastText.equals("-") || forecastText.equals("null")) ? actual : parseValue(forecastText);

            //Edited the metric names to cater to new metrics we measure.

                // ADP Private Employment — ignore weekly.
            if (title.contains("adp") && title.contains("employment") && !title.contains("weekly")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if ((title.contains("nonfarm payrolls") || title.contains("non-farm employment change")) && !title.contains("private")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (title.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
                // Retail Sales (MoM) — ignore Core, Ex Gas/Autos, Control, YoY
            } else if (title.contains("retail sales") && !title.contains("core") && !title.contains("ex ") && !title.contains("control") && !title.contains("(yoy)")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
                // Initial Jobless Claims — ignore 4-Week Avg and Continuing
            } else if ((title.contains("unemployment claims") || title.contains("jobless claims")) && !title.contains("4-week") && !title.contains("continuing")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
                // PPI (MoM) — ignore Core and Ex Food/Energy
            } else if (title.contains("ppi") && !title.contains("core") && !title.contains("ex") && (title.contains("(mom)") || title.contains("m/m"))) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("average hourly earnings") || (title.contains("wage") && title.contains("m/m"))) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
                // Core PCE (MoM) — match "(mom)" as well as "m/m"
            } else if (title.contains("core pce price index") && (title.contains("(mom)") || title.contains("m/m"))) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (title.contains("industrial production") && (title.contains("(mom") || title.contains("m/m"))) {
                uniqueMetrics.put("Industrial Production (MoM)", new MarketMetric("Industrial Production (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if ((title.contains("michigan consumer sentiment") || title.contains("uom consumer sentiment")) && !title.contains("final")) {

                // Investing.com strips the 'P' icon, so isolate Prelim by checking if it's in the first half of the month
                String eventDateStr = node.path("date").asText("");
                int dayOfMonth = today.getDayOfMonth();
                try {
                    if (!eventDateStr.isEmpty()) {
                        // Handles ISO dates like "2026-11-06" or "2026-11-06T15:00:00Z"
                        dayOfMonth = LocalDate.parse(eventDateStr.substring(0, 10)).getDayOfMonth();
                    }
                } catch (Exception ignored) {
                    // Falls back to today's day of month
                }

                if (dayOfMonth <= 16) {
                    uniqueMetrics.put("Prelim UoM Consumer Sentiment",
                            new MarketMetric("Prelim UoM Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
                }
            } else if (title.contains("ism manufacturing pmi")) {
                uniqueMetrics.put("ISM Manufacturing PMI", new MarketMetric("ISM Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("ism services pmi") || title.contains("ism non-manufacturing pmi")) {
                uniqueMetrics.put("ISM Services PMI", new MarketMetric("ISM Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (title.contains("jolts")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
                // CPI (YoY) — match "(yoy)" as well as "y/y"
            } else if (title.contains("cpi") && !title.contains("core") && (title.contains("(yoy)") || title.contains("y/y") || title.contains("annual"))) {
                uniqueMetrics.put("CPI (YoY)", new MarketMetric("CPI (YoY)", actual, estimate, 0, MetricCategory.INFLATION));
                // GDP (QoQ) — match "gdp (qoq)" while excluding GDP Price Index / GDPNow
            } else if ((title.contains("gdp (qoq)") || title.contains("gdp q/q"))
                    && !title.contains("price index")
                    && !title.contains("sales")
                    && !title.contains("gdpnow")) {
                uniqueMetrics.put("GDP (QoQ)", new MarketMetric("GDP (QoQ)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }

        System.out.println("[SYSTEM] Store Actor calendar parsed successfully. USD metrics captured: " + uniqueMetrics.size());
        return new ArrayList<>(uniqueMetrics.values());
    }

    // This is meant to clean up the strings
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