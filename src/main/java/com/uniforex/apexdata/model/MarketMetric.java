package com.uniforex.apexdata.model;

import java.time.LocalDateTime;

public record MarketMetric(
        String name,
        double actualValue,
        double forecastValue,
        int scoreDelta,
        MetricCategory category,
        LocalDateTime lastUpdated
) {
    // Keeps all existing 5-argument calls (like EconomicCalendarService) compiling cleanly
    public MarketMetric(String name, double actualValue, double forecastValue, int scoreDelta, MetricCategory category) {
        this(name, actualValue, forecastValue, scoreDelta, category, null);
    }
}