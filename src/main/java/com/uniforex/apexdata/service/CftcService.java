package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.util.Arrays;
import java.util.List;

public class CftcService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;

    public CftcService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public List<MarketMetric> fetchInstitutionalData() throws Exception {
        // Mocking the historical API payload.
        // In production, parse your CFTC JSON to get Current and Previous week data.
        double currentNet = 5772.0;
        double previousNet = 20000.0;
        double longPercentage = 82.5;

        return Arrays.asList(
                // 1. Absolute Bias
                new MarketMetric("COT Net Positioning", currentNet, 0.0, 0, MetricCategory.INSTITUTIONAL_ACTIVITY),
                // 2. Momentum (We pass the 'previousNet' into the forecast field so it calculates the Diff automatically)
                new MarketMetric("COT WoW Delta", currentNet, previousNet, 0, MetricCategory.INSTITUTIONAL_ACTIVITY),
                // 3. Sentiment / Crowdedness
                new MarketMetric("COT Long Percentage", longPercentage, 0.0, 0, MetricCategory.INSTITUTIONAL_ACTIVITY)
        );
    }
}