package com.uniforex.apexdata.service;

import com.uniforex.apexdata.CompositeScoringEngine;
import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DashboardStateService {

    private DashboardSummaryResponse latestSummary;
    private AssetScoreResponse latestGoldSummary;

    @Autowired
    private HistoricalScoreRepository historicalScoreRepository;

    // REMOVED THE @AUTOWIRED FIELDS HERE

    public DashboardSummaryResponse getLatestSummary() { return latestSummary; }
    public void setLatestSummary(DashboardSummaryResponse latestSummary) { this.latestSummary = latestSummary; }
    public AssetScoreResponse getLatestGoldSummary() { return latestGoldSummary; }
    public void setLatestGoldSummary(AssetScoreResponse latestGoldSummary) { this.latestGoldSummary = latestGoldSummary; }

    /**
     * Orchestrates the Gold fetching and scoring pipeline.
     * Now accepts the services directly from the EngineScheduler.
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

            this.latestGoldSummary = new AssetScoreResponse(
                    "XAUUSD", finalScore, bias, usdMacroSubtotal * -1, cotScore, techScore
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

    public record AssetScoreResponse(
            String asset, int totalScore, String biasLabel, int invertedMacroBaseline, int cotScore, int technicalScore
    ) {}
}