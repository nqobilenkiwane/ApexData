package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomicCalendarService {

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        // Dependencies maintained for constructor compatibility
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        String scraperApiKey = "b7948d09ce26ef33bd811331e4bffbbc";
        String targetUrl = "https://www.forexfactory.com/calendar";
        String proxyUrl = "http://api.scraperapi.com?api_key=" + scraperApiKey + "&url=" + targetUrl;

        System.out.println("[SYSTEM] Attempting calendar fetch via ScraperAPI...");

        // ScraperAPI handles proxy rotation internally, so we only need one connection attempt
        Document doc = Jsoup.connect(proxyUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(60000) // 60-second timeout to give the residential proxy time to solve Cloudflare
                .ignoreContentType(true)
                .get();

        // Throw an exception if the payload is empty so the scheduler handles it gracefully
        if (doc == null || doc.selectFirst("tr.calendar__row") == null) {
            throw new Exception("ScraperAPI failed to load the calendar or timed out.");
        }

        System.out.println("[SYSTEM] Successfully connected and retrieved HTML via ScraperAPI.");

        // Scrape the HTML payload
        Elements rows = doc.select("tr.calendar__row");

        for (Element row : rows) {
            Element currencyElem = row.selectFirst("td.calendar__currency");
            if (currencyElem == null || !"USD".equalsIgnoreCase(currencyElem.text().trim())) {
                continue;
            }

            Element eventElem = row.selectFirst("td.calendar__event span");
            Element actualElem = row.selectFirst("td.calendar__actual");
            Element forecastElem = row.selectFirst("td.calendar__forecast");

            if (eventElem == null || actualElem == null || forecastElem == null) {
                continue;
            }

            String eventTitle = eventElem.text().trim().toLowerCase();
            String actualText = actualElem.text().trim();
            String forecastText = forecastElem.text().trim();

            if (actualText.isEmpty() || forecastText.isEmpty()) {
                continue;
            }

            double actual = parseValue(actualText);
            double estimate = parseValue(forecastText);

            if (eventTitle.contains("non-farm employment") || eventTitle.contains("nfp")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("unemployment rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("retail sales") || eventTitle.contains("core retail sales")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("unemployment claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("ppi") || eventTitle.contains("core ppi")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("average hourly earnings")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("core pce")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("industrial production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("consumer sentiment") || eventTitle.contains("consumer confidence")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("manufacturing pmi")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("services pmi")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventTitle.contains("jolts job openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("adp non-farm")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", actual, estimate, 0, MetricCategory.JOB_MARKET));
            } else if (eventTitle.contains("cpi")) {
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", actual, estimate, 0, MetricCategory.INFLATION));
            } else if (eventTitle.contains("gdp")) {
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", actual, estimate, 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }

        return new ArrayList<>(uniqueMetrics.values());
    }

    private double parseValue(String val) {
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