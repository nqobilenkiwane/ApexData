package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomicCalendarService {

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        // Dependencies maintained for constructor compatibility in EngineScheduler
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        System.out.println("[SYSTEM] Attempting calendar fetch via Myfxbook XML Feed...");

        // 1. Establish a browser-like connection to bypass 403 blocks
        URL url = new URL("https://www.myfxbook.com/rss/forex-economic-calendar-events");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setRequestMethod("GET");

        // 2. Parse the XML stream using native Java libraries
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(connection.getInputStream());
        doc.getDocumentElement().normalize();

        NodeList itemList = doc.getElementsByTagName("item");

        if (itemList.getLength() == 0) {
            throw new Exception("Myfxbook feed returned no events.");
        }

        for (int i = 0; i < itemList.getLength(); i++) {
            Element item = (Element) itemList.item(i);

            String title = item.getElementsByTagName("title").item(0).getTextContent().toLowerCase();
            String description = item.getElementsByTagName("description").item(0).getTextContent().toLowerCase();

            // Filter for USD events only
            if (!title.contains("usd")) {
                continue;
            }

            // Extract values using basic string parsing since RSS descriptions are plain text
            double actual = extractValueFromDescription(description, "actual:");
            double estimate = extractValueFromDescription(description, "consensus:");

            // Skip zeroed-out lines for events that haven't happened and lack estimates
            if (actual == 0.0 && estimate == 0.0) {
                continue;
            }

            // Route to specific scorecards
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

        System.out.println("[SYSTEM] Successfully connected and retrieved XML via Myfxbook RSS.");
        return new ArrayList<>(uniqueMetrics.values());
    }

    private double extractValueFromDescription(String description, String key) {
        int index = description.indexOf(key);
        if (index == -1) return 0.0;

        String sub = description.substring(index + key.length());
        String[] parts = sub.split("[,|<]"); // Handles commas, pipes, or HTML tags if present
        String rawVal = parts[0].toLowerCase();
        String valStr = rawVal.replaceAll("[^0-9.-]", "").trim();

        // Reintroduce your multiplier logic for K, M, and B
        double multiplier = 1.0;
        if (rawVal.contains("k")) multiplier = 1000.0;
        else if (rawVal.contains("m")) multiplier = 1000000.0;
        else if (rawVal.contains("b")) multiplier = 1000000000.0;

        try {
            return valStr.isEmpty() ? 0.0 : Double.parseDouble(valStr) * multiplier;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}