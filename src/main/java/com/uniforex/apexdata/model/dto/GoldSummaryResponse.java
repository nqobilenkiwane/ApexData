package com.uniforex.apexdata.model.dto; // Ensure this matches your actual package path

import com.uniforex.apexdata.model.MarketMetric;
import java.util.List;
import java.util.Map;

public record GoldSummaryResponse(
        int totalScore,
        String biasLabel,
        int invertedMacroBaseline,
        int cotScore,
        int technicalScore,
        Map<String, Integer> categoryScores,
        List<MarketMetric> metrics
) {}