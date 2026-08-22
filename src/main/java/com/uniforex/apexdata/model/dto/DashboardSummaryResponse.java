package com.uniforex.apexdata.model.dto;

import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        int totalScore,
        String overallBias,
        Map<MetricCategory, Integer> categoryScores,
        List<MarketMetric> metrics
) {}