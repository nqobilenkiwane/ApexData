package com.uniforex.apexdata.service;

import com.uniforex.apexdata.CompositeScoringEngine;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.dto.GoldSummaryResponse; // Make sure you created this file
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardStateService {

    private DashboardSummaryResponse latestSummary;
    private GoldSummaryResponse latestGoldSummary; // Swapped to the new DTO

    @Autowired
    private HistoricalScoreRepository historicalScoreRepository;

    public DashboardSummaryResponse getLatestSummary() { return latestSummary; }
    public void setLatestSummary(DashboardSummaryResponse latestSummary) { this.latestSummary = latestSummary; }
    public GoldSummaryResponse getLatestGoldSummary() { return latestGoldSummary; }
    public void setLatestGoldSummary(GoldSummaryResponse latestGoldSummary) { this.latestGoldSummary = latestGoldSummary; }

    /**
     * Orchestrates the Gold fetching and scoring pipeline.
     */
    public void updateGoldPipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing Gold (XAUUSD) Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update Gold: USD Macro Summary is null.");
                return;
            }

            CftcService.GoldCotData goldCot = cftcService.fetchGoldCotData();
            TechnicalService.AssetTechnicalData goldTech = technicalService.fetchGoldTechnicals();

            int usdMacroSubtotal = scoringEngine.calculateMacroSubtotal(latestSummary.metrics());

            int cotScore = scoringEngine.scoreGoldCot(
                    goldCot.nonCommercialLongs(),
                    goldCot.nonCommercialShorts(),
                    goldCot.previousNetPosition()
            );

            int techScore = scoringEngine.scoreTechnicals(
                    goldTech.currentPrice(),
                    goldTech.sma200(),
                    goldTech.rsi14()
            );

            int finalScore = scoringEngine.calculateGoldCompositeScore(usdMacroSubtotal, cotScore, techScore);
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            // --- 1. INVERT THE RAW METRICS ---
            List<MarketMetric> invertedMetrics = latestSummary.metrics().stream()
                    .map(m -> new MarketMetric(
                            m.name(),
                            m.actualValue(),
                            m.forecastValue(),
                            m.scoreDelta() * -1, // Flips +1 to -1, and -1 to +1
                            m.category()

                    ))
                    .collect(Collectors.toList());

//            // --- 2. INVERT THE CATEGORY TOTALS ---
//            Map<String, Integer> invertedCategories = new HashMap<>();
//            if (latestSummary.categoryScores() != null) {
//                for (Map.Entry<MetricCategory, Integer> entry : latestSummary.categoryScores().entrySet()) {
//                    invertedCategories.put(entry.getKey(), entry.getValue() * -1);
//                }
//            }

            // --- 2. INVERT THE CATEGORY TOTALS ---
            Map<String, Integer> invertedCategories = new HashMap<>();
            if (latestSummary.categoryScores() != null) {
                for (Map.Entry<MetricCategory, Integer> entry : latestSummary.categoryScores().entrySet()) {
                    // Convert the MetricCategory enum to a String using .name()
                    invertedCategories.put(entry.getKey().name(), entry.getValue() * -1);
                }
            }

            // --- 3. SAVE TO THE NEW DTO ---
            this.latestGoldSummary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    usdMacroSubtotal * -1,
                    cotScore,
                    techScore,
                    invertedCategories,
                    invertedMetrics
            );

            System.out.println("Gold Pipeline completed successfully: " + finalScore + " (" + bias + ")");

        } catch (Exception e) {
            System.err.println("Failed to update Gold pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Scheduled(cron = "0 0 22 * * *", zone = "Africa/Johannesburg")
    public void captureDailySnapshot() {
        System.out.println("Executing EOD Snapshot at 10:00 PM SAST...");
        if (latestSummary == null) {
            System.out.println("EOD Snapshot aborted: No summary data was generated today.");
            return;
        }

        int currentScore = latestSummary.totalScore();
        String currentBias = latestSummary.overallBias();
        historicalScoreRepository.save(new HistoricalScoreEntity("USD", currentScore, currentBias));
        System.out.println("USD EOD Snapshot saved successfully: " + currentScore + " (" + currentBias + ")");

        if (latestGoldSummary != null) {
            historicalScoreRepository.save(new HistoricalScoreEntity("XAUUSD", latestGoldSummary.totalScore(), latestGoldSummary.biasLabel()));
            System.out.println("Gold EOD Snapshot saved successfully: " + latestGoldSummary.totalScore() + " (" + latestGoldSummary.biasLabel() + ")");
        }
    }
}