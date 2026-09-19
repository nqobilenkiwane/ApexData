package com.uniforex.apexdata.service;

import com.uniforex.apexdata.CompositeScoringEngine;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.dto.GoldSummaryResponse;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardStateService {

    private DashboardSummaryResponse latestSummary;
    private GoldSummaryResponse latestGoldSummary;

    @Autowired
    private HistoricalScoreRepository historicalScoreRepository;

    public DashboardSummaryResponse getLatestSummary() { return latestSummary; }
    public void setLatestSummary(DashboardSummaryResponse latestSummary) { this.latestSummary = latestSummary; }
    public GoldSummaryResponse getLatestGoldSummary() { return latestGoldSummary; }
    public void setLatestGoldSummary(GoldSummaryResponse latestGoldSummary) { this.latestGoldSummary = latestGoldSummary; }

    /**
     * Orchestrates the Gold fetching and scoring pipeline using a hybrid approach:
     * Inverted USD Macro/Yields + Dedicated Gold Institutional & Technical Data.
     */
    public void updateGoldPipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing Gold (XAUUSD) Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update Gold: USD Macro Summary is null.");
                return;
            }

            List<MarketMetric> finalGoldMetrics = new ArrayList<>();

            // --- 1. INVERT USD MACRO & YIELDS (Strict Whitelist) ---
            for (MarketMetric usd : latestSummary.metrics()) {
                MetricCategory cat = usd.category();
                if (cat == MetricCategory.ECONOMIC_GROWTH ||
                        cat == MetricCategory.JOB_MARKET ||
                        cat == MetricCategory.INFLATION ||
                        cat == MetricCategory.CAPITAL_FLOWS) {

                    finalGoldMetrics.add(new MarketMetric(
                            usd.name(),
                            usd.actualValue(),
                            usd.forecastValue(),
                            usd.scoreDelta() * -1, // Flip the score for Gold
                            usd.category()
                    ));
                }
            }

            // --- 2. ADD REAL GOLD INSTITUTIONAL DATA (COMEX 088691) ---
            List<MarketMetric> goldCotMetrics = cftcService.fetchGoldInstitutionalData();
            List<MarketMetric> scoredGoldCot = scoringEngine.applyScores(goldCotMetrics);
            finalGoldMetrics.addAll(scoredGoldCot);

            // --- 3. ADD REAL GOLD TECHNICALS (GC=F) ---
            TechnicalService.AssetTechnicalData goldTechs = technicalService.fetchGoldTechnicals();
            int goldTechScore = scoringEngine.scoreTechnicals(goldTechs.currentPrice(), goldTechs.sma200(), goldTechs.rsi14());
            finalGoldMetrics.add(new MarketMetric("Technical Momentum", goldTechs.currentPrice(), 0.0, goldTechScore, MetricCategory.TECHNICALS));

            // --- 4. CALCULATE TOP COMPOSITE HEADER SCORES ---
            CftcService.GoldCotData topLevelCot = cftcService.fetchGoldCotData();
            int cotScore = scoringEngine.scoreGoldCot(
                    topLevelCot.nonCommercialLongs(),
                    topLevelCot.nonCommercialShorts(),
                    topLevelCot.previousNetPosition()
            );

            int usdMacroSubtotal = scoringEngine.calculateMacroSubtotal(latestSummary.metrics());
            int finalScore = scoringEngine.calculateGoldCompositeScore(usdMacroSubtotal, cotScore, goldTechScore);
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            // --- 5. RECALCULATE CATEGORY TOTALS FOR GOLD ---
            // Build fresh category sums using the newly assembled Gold metrics list
            Map<String, Integer> goldCategoryScores = new HashMap<>();
            for (MarketMetric m : finalGoldMetrics) {
                String catName = m.category().name();
                goldCategoryScores.put(catName, goldCategoryScores.getOrDefault(catName, 0) + m.scoreDelta());
            }

            // --- 6. SAVE TO THE GOLD DTO ---
            this.latestGoldSummary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    usdMacroSubtotal * -1,
                    cotScore,
                    goldTechScore,
                    goldCategoryScores,
                    finalGoldMetrics
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