package com.uniforex.apexdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.CalendarEvent;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomicCalendarService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final String apiKey;

    public EconomicCalendarService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    public List<MarketMetric> fetchLiveCalendarEvents() throws Exception {

        String jsonResponse = """
        [
          { "event": "Non Farm Payrolls", "currency": "USD", "actual": -23.0, "estimate": 85.0 },
          { "event": "Unemployment Rate", "currency": "USD", "actual": 4.10, "estimate": 4.20 },
          { "event": "Retail Sales m/m", "currency": "USD", "actual": -0.58, "estimate": 0.10 },
          { "event": "Initial Jobless Claims", "currency": "USD", "actual": 209000.0, "estimate": 202000.0 },
          { "event": "Producer Price Index m/m", "currency": "USD", "actual": -0.61, "estimate": 0.20 },
          { "event": "Average Hourly Earnings m/m", "currency": "USD", "actual": 0.05, "estimate": 0.30 },
          { "event": "Core PCE Price Index m/m", "currency": "USD", "actual": 0.13, "estimate": 0.20 },
          { "event": "Industrial Production m/m", "currency": "USD", "actual": 0.20, "estimate": 0.10 },
          { "event": "Michigan Consumer Sentiment", "currency": "USD", "actual": 49.5, "estimate": 69.0 },
          { "event": "ISM Manufacturing PMI", "currency": "USD", "actual": 48.5, "estimate": 49.0 },
          { "event": "ISM Services PMI", "currency": "USD", "actual": 52.4, "estimate": 51.5 },
          { "event": "JOLTs Job Openings", "currency": "USD", "actual": 7.36, "estimate": 7.80 },
          { "event": "ADP Nonfarm Employment Change", "currency": "USD", "actual": 44.0, "estimate": 95.0 },
          
          { "event": "US Inflation Rate YoY", "currency": "USD", "actual": 3.30, "estimate": 3.10 },
          { "event": "US GDP Growth Rate", "currency": "USD", "actual": 1.50, "estimate": 1.80 }
        ]
        """;

        List<CalendarEvent> events = mapper.readValue(jsonResponse, new TypeReference<List<CalendarEvent>>() {});
        Map<String, MarketMetric> uniqueMetrics = new HashMap<>();

        for (CalendarEvent e : events) {
            if (!"USD".equalsIgnoreCase(e.currency()) || e.actual() == null || e.estimate() == null) {
                continue;
            }

            String eventName = e.event();

            if (eventName.contains("Non Farm Payrolls")) {
                uniqueMetrics.put("NFP (Jobs)", new MarketMetric("NFP (Jobs)", e.actual(), e.estimate(), 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("Unemployment Rate")) {
                uniqueMetrics.put("Unemployment Rate", new MarketMetric("Unemployment Rate", e.actual(), e.estimate(), 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("Retail Sales m/m")) {
                uniqueMetrics.put("Retail Sales (MoM)", new MarketMetric("Retail Sales (MoM)", e.actual(), e.estimate(), 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("Initial Jobless Claims")) {
                uniqueMetrics.put("Initial Jobless Claims", new MarketMetric("Initial Jobless Claims", e.actual(), e.estimate(), 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("Producer Price Index")) {
                uniqueMetrics.put("PPI (MoM)", new MarketMetric("PPI (MoM)", e.actual(), e.estimate(), 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Average Hourly Earnings")) {
                uniqueMetrics.put("Wage Growth (MoM)", new MarketMetric("Wage Growth (MoM)", e.actual(), e.estimate(), 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Core PCE")) {
                uniqueMetrics.put("Core PCE (MoM)", new MarketMetric("Core PCE (MoM)", e.actual(), e.estimate(), 0, MetricCategory.INFLATION));
            } else if (eventName.contains("Industrial Production")) {
                uniqueMetrics.put("Industrial Production", new MarketMetric("Industrial Production", e.actual(), e.estimate(), 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("Consumer Sentiment")) {
                uniqueMetrics.put("Consumer Sentiment", new MarketMetric("Consumer Sentiment", e.actual(), e.estimate(), 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("Manufacturing PMI")) {
                uniqueMetrics.put("Manufacturing PMI", new MarketMetric("Manufacturing PMI", e.actual(), e.estimate(), 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("Services PMI") || eventName.contains("Non-Manufacturing PMI")) {
                uniqueMetrics.put("Services PMI", new MarketMetric("Services PMI", e.actual(), e.estimate(), 0, MetricCategory.ECONOMIC_GROWTH));
            } else if (eventName.contains("JOLT") || eventName.contains("Job Openings")) {
                uniqueMetrics.put("JOLTS Job Openings", new MarketMetric("JOLTS Job Openings", e.actual(), e.estimate(), 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("ADP")) {
                uniqueMetrics.put("ADP Private Employment", new MarketMetric("ADP Private Employment", e.actual(), e.estimate(), 0, MetricCategory.JOB_MARKET));
            } else if (eventName.contains("Inflation Rate YoY") || eventName.contains("CPI YoY")) {
                // NEW: Map the CPI mock event
                uniqueMetrics.put("YoY Inflation", new MarketMetric("YoY Inflation", e.actual(), e.estimate(), 0, MetricCategory.INFLATION));
            } else if (eventName.contains("GDP")) {
                // NEW: Map the GDP mock event
                uniqueMetrics.put("Real GDP", new MarketMetric("Real GDP", e.actual(), e.estimate(), 0, MetricCategory.ECONOMIC_GROWTH));
            }
        }
        return new ArrayList<>(uniqueMetrics.values());
    }
}