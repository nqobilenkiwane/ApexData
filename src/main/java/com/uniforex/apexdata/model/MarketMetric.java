package com.uniforex.apexdata.model;

public record MarketMetric(
        String name,
        double actualValue,
        double forecastValue, // Ready for when we add the Alpha Vantage Calendar!
        int scoreDelta,       // Your existing calculated score (+1, 0, -1)
        MetricCategory category
) {}