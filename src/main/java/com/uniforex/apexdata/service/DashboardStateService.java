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
    private GoldSummaryResponse latestSilverSummary;
    private GoldSummaryResponse latestNasdaqSummary;
    private GoldSummaryResponse latestDowSummary;
    private GoldSummaryResponse latestSp500Summary;
    private GoldSummaryResponse latestRussell2000Summary;

    @Autowired
    private HistoricalScoreRepository historicalScoreRepository;

    public DashboardSummaryResponse getLatestSummary() { return latestSummary; }
    public void setLatestSummary(DashboardSummaryResponse latestSummary) { this.latestSummary = latestSummary; }

    public GoldSummaryResponse getLatestGoldSummary() { return latestGoldSummary; }
    public void setLatestGoldSummary(GoldSummaryResponse latestGoldSummary) { this.latestGoldSummary = latestGoldSummary; }

    public GoldSummaryResponse getLatestSilverSummary() { return latestSilverSummary; }
    public void setLatestSilverSummary(GoldSummaryResponse latestSilverSummary) { this.latestSilverSummary = latestSilverSummary; }

    public GoldSummaryResponse getLatestNasdaqSummary() { return latestNasdaqSummary; }
    public void setLatestNasdaqSummary(GoldSummaryResponse latestNasdaqSummary) { this.latestNasdaqSummary = latestNasdaqSummary; }

    public GoldSummaryResponse getLatestDowSummary() { return latestDowSummary; }
    public void setLatestDowSummary(GoldSummaryResponse latestDowSummary) { this.latestDowSummary = latestDowSummary; }

    public GoldSummaryResponse getLatestSp500Summary() { return latestSp500Summary; }
    public void setLatestSp500Summary(GoldSummaryResponse latestSp500Summary) { this.latestSp500Summary = latestSp500Summary; }

    public GoldSummaryResponse getLatestRussell2000Summary() { return latestRussell2000Summary; }
    public void setLatestRussell2000Summary(GoldSummaryResponse latestRussell2000Summary) { this.latestRussell2000Summary = latestRussell2000Summary; }

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
                            usd.scoreDelta() * -1, // Flip score for Gold
                            usd.category()
                    ));
                }
            }

            // --- 2. ADD REAL GOLD INSTITUTIONAL ACTIVITY (COMEX 088691) ---
            List<MarketMetric> goldCotMetrics = cftcService.fetchGoldInstitutionalData();
            List<MarketMetric> scoredGoldCot = scoringEngine.applyScores(goldCotMetrics);
            finalGoldMetrics.addAll(scoredGoldCot);

            // --- 3. ADD REAL GOLD TECHNICALS (GC=F) ---
            TechnicalService.AssetTechnicalData goldTechs = technicalService.fetchGoldTechnicals();
            int goldTechScore = scoringEngine.scoreTechnicals(goldTechs.currentPrice(), goldTechs.sma200(), goldTechs.rsi14());
            finalGoldMetrics.add(new MarketMetric("Technical Momentum", goldTechs.currentPrice(), 0.0, goldTechScore, MetricCategory.TECHNICALS));

            // --- 4. BASE CATEGORY TOTALS ---
            Map<String, Integer> goldCategoryScores = calculateCategoryTotals(finalGoldMetrics);

            // --- 5. AGGREGATE TOP COMPOSITE HEADER BUCKETS ---
            int macroHealth = goldCategoryScores.getOrDefault("ECONOMIC_GROWTH", 0)
                    + goldCategoryScores.getOrDefault("JOB_MARKET", 0)
                    + goldCategoryScores.getOrDefault("INFLATION", 0);

            int positioningAndFlows = goldCategoryScores.getOrDefault("INSTITUTIONAL_ACTIVITY", 0)
                    + goldCategoryScores.getOrDefault("CAPITAL_FLOWS", 0);

            int technicalMomentum = goldCategoryScores.getOrDefault("TECHNICALS", 0);

            int finalScore = macroHealth + positioningAndFlows + technicalMomentum;
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            // --- 6. SAVE STATE ---
            this.latestGoldSummary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    macroHealth,
                    positioningAndFlows,
                    technicalMomentum,
                    goldCategoryScores,
                    finalGoldMetrics
            );

            System.out.println("Gold Pipeline completed successfully: " + finalScore + " (" + bias + ")");
        } catch (Exception e) {
            System.err.println("Failed to update Gold pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Orchestrates the Silver fetching and scoring pipeline using a hybrid approach:
     * Inverted USD Macro/Yields + Dedicated Silver Institutional & Technical Data.
     */
    public void updateSilverPipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing Silver (XAGUSD) Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update Silver: USD Macro Summary is null.");
                return;
            }

            List<MarketMetric> finalSilverMetrics = new ArrayList<>();

            // --- 1. INVERT USD MACRO & YIELDS (Strict Whitelist) ---
            for (MarketMetric usd : latestSummary.metrics()) {
                MetricCategory cat = usd.category();
                if (cat == MetricCategory.ECONOMIC_GROWTH ||
                        cat == MetricCategory.JOB_MARKET ||
                        cat == MetricCategory.INFLATION ||
                        cat == MetricCategory.CAPITAL_FLOWS) {

                    finalSilverMetrics.add(new MarketMetric(
                            usd.name(),
                            usd.actualValue(),
                            usd.forecastValue(),
                            usd.scoreDelta() * -1, // Flip score for Silver
                            usd.category()
                    ));
                }
            }

            // --- 2. ADD REAL SILVER INSTITUTIONAL ACTIVITY (COMEX 084691) ---
            List<MarketMetric> silverCotMetrics = cftcService.fetchSilverInstitutionalData();
            List<MarketMetric> scoredSilverCot = scoringEngine.applyScores(silverCotMetrics);
            finalSilverMetrics.addAll(scoredSilverCot);

            // --- 3. ADD REAL SILVER TECHNICALS (SI=F) ---
            TechnicalService.AssetTechnicalData silverTechs = technicalService.fetchSilverTechnicals();
            int silverTechScore = scoringEngine.scoreTechnicals(silverTechs.currentPrice(), silverTechs.sma200(), silverTechs.rsi14());
            finalSilverMetrics.add(new MarketMetric("Technical Momentum", silverTechs.currentPrice(), 0.0, silverTechScore, MetricCategory.TECHNICALS));

            // --- 4. BASE CATEGORY TOTALS ---
            Map<String, Integer> silverCategoryScores = calculateCategoryTotals(finalSilverMetrics);

            // --- 5. AGGREGATE TOP COMPOSITE HEADER BUCKETS ---
            int macroHealth = silverCategoryScores.getOrDefault("ECONOMIC_GROWTH", 0)
                    + silverCategoryScores.getOrDefault("JOB_MARKET", 0)
                    + silverCategoryScores.getOrDefault("INFLATION", 0);

            int positioningAndFlows = silverCategoryScores.getOrDefault("INSTITUTIONAL_ACTIVITY", 0)
                    + silverCategoryScores.getOrDefault("CAPITAL_FLOWS", 0);

            int technicalMomentum = silverCategoryScores.getOrDefault("TECHNICALS", 0);

            int finalScore = macroHealth + positioningAndFlows + technicalMomentum;
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            // --- 6. SAVE STATE ---
            this.latestSilverSummary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    macroHealth,
                    positioningAndFlows,
                    technicalMomentum,
                    silverCategoryScores,
                    finalSilverMetrics
            );

            System.out.println("Silver Pipeline completed successfully: " + finalScore + " (" + bias + ")");
        } catch (Exception e) {
            System.err.println("Failed to update Silver pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Orchestrates the Nasdaq 100 (NQ=F) fetching and scoring pipeline.
     */
    public void updateNasdaqPipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing Nasdaq 100 (NAS100) Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update Nasdaq: USD Macro Summary is null.");
                return;
            }

            List<MarketMetric> finalNasdaqMetrics = buildEquityMacroBase(latestSummary.metrics());

            // Add real Nasdaq COT (20974+)
            List<MarketMetric> nasdaqCot = cftcService.fetchNasdaqInstitutionalData();
            finalNasdaqMetrics.addAll(scoringEngine.applyScores(nasdaqCot));

            // Add real Nasdaq Technicals (NQ=F)
            TechnicalService.AssetTechnicalData nasdaqTech = technicalService.fetchNasdaqTechnicals();
            int nasdaqTechScore = scoringEngine.scoreTechnicals(nasdaqTech.currentPrice(), nasdaqTech.sma200(), nasdaqTech.rsi14());
            finalNasdaqMetrics.add(new MarketMetric("Technical Momentum", nasdaqTech.currentPrice(), 0.0, nasdaqTechScore, MetricCategory.TECHNICALS));

            // Calculate category totals and composite buckets
            Map<String, Integer> nasdaqCategoryScores = calculateCategoryTotals(finalNasdaqMetrics);

            int macroHealth = nasdaqCategoryScores.getOrDefault("ECONOMIC_GROWTH", 0)
                    + nasdaqCategoryScores.getOrDefault("JOB_MARKET", 0)
                    + nasdaqCategoryScores.getOrDefault("INFLATION", 0);

            int positioningAndFlows = nasdaqCategoryScores.getOrDefault("INSTITUTIONAL_ACTIVITY", 0)
                    + nasdaqCategoryScores.getOrDefault("CAPITAL_FLOWS", 0);

            int technicalMomentum = nasdaqCategoryScores.getOrDefault("TECHNICALS", 0);

            int finalScore = macroHealth + positioningAndFlows + technicalMomentum;
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            this.latestNasdaqSummary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    macroHealth,
                    positioningAndFlows,
                    technicalMomentum,
                    nasdaqCategoryScores,
                    finalNasdaqMetrics
            );

            System.out.println("Nasdaq Pipeline completed successfully: " + finalScore + " (" + bias + ")");
        } catch (Exception e) {
            System.err.println("Failed to update Nasdaq pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Orchestrates the Dow Jones / US30 (YM=F) fetching and scoring pipeline.
     */
    public void updateDowPipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing US30 / Dow Jones Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update US30: USD Macro Summary is null.");
                return;
            }

            List<MarketMetric> finalDowMetrics = buildEquityMacroBase(latestSummary.metrics());

            // Add real Dow COT (12460+)
            List<MarketMetric> dowCot = cftcService.fetchDowInstitutionalData();
            finalDowMetrics.addAll(scoringEngine.applyScores(dowCot));

            // Add real Dow Technicals (YM=F)
            TechnicalService.AssetTechnicalData dowTech = technicalService.fetchDowTechnicals();
            int dowTechScore = scoringEngine.scoreTechnicals(dowTech.currentPrice(), dowTech.sma200(), dowTech.rsi14());
            finalDowMetrics.add(new MarketMetric("Technical Momentum", dowTech.currentPrice(), 0.0, dowTechScore, MetricCategory.TECHNICALS));

            // Calculate category totals and composite buckets
            Map<String, Integer> dowCategoryScores = calculateCategoryTotals(finalDowMetrics);

            int macroHealth = dowCategoryScores.getOrDefault("ECONOMIC_GROWTH", 0)
                    + dowCategoryScores.getOrDefault("JOB_MARKET", 0)
                    + dowCategoryScores.getOrDefault("INFLATION", 0);

            int positioningAndFlows = dowCategoryScores.getOrDefault("INSTITUTIONAL_ACTIVITY", 0)
                    + dowCategoryScores.getOrDefault("CAPITAL_FLOWS", 0);

            int technicalMomentum = dowCategoryScores.getOrDefault("TECHNICALS", 0);

            int finalScore = macroHealth + positioningAndFlows + technicalMomentum;
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            this.latestDowSummary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    macroHealth,
                    positioningAndFlows,
                    technicalMomentum,
                    dowCategoryScores,
                    finalDowMetrics
            );

            System.out.println("US30 Pipeline completed successfully: " + finalScore + " (" + bias + ")");
        } catch (Exception e) {
            System.err.println("Failed to update US30 pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Orchestrates the S&P 500 (ES=F) fetching and scoring pipeline.
     */
    public void updateSp500Pipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing S&P 500 (SPX) Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update S&P 500: USD Macro Summary is null.");
                return;
            }

            List<MarketMetric> finalSp500Metrics = buildEquityMacroBase(latestSummary.metrics());

            // Add real S&P 500 COT
            List<MarketMetric> sp500Cot = cftcService.fetchSp500InstitutionalData();
            finalSp500Metrics.addAll(scoringEngine.applyScores(sp500Cot));

            // Add real S&P 500 Technicals
            TechnicalService.AssetTechnicalData sp500Tech = technicalService.fetchSp500Technicals();
            int sp500TechScore = scoringEngine.scoreTechnicals(sp500Tech.currentPrice(), sp500Tech.sma200(), sp500Tech.rsi14());
            finalSp500Metrics.add(new MarketMetric("Technical Momentum", sp500Tech.currentPrice(), 0.0, sp500TechScore, MetricCategory.TECHNICALS));

            // Calculate category totals and composite buckets
            Map<String, Integer> sp500CategoryScores = calculateCategoryTotals(finalSp500Metrics);

            int macroHealth = sp500CategoryScores.getOrDefault("ECONOMIC_GROWTH", 0)
                    + sp500CategoryScores.getOrDefault("JOB_MARKET", 0)
                    + sp500CategoryScores.getOrDefault("INFLATION", 0);

            int positioningAndFlows = sp500CategoryScores.getOrDefault("INSTITUTIONAL_ACTIVITY", 0)
                    + sp500CategoryScores.getOrDefault("CAPITAL_FLOWS", 0);

            int technicalMomentum = sp500CategoryScores.getOrDefault("TECHNICALS", 0);

            int finalScore = macroHealth + positioningAndFlows + technicalMomentum;
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            this.latestSp500Summary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    macroHealth,
                    positioningAndFlows,
                    technicalMomentum,
                    sp500CategoryScores,
                    finalSp500Metrics
            );

            System.out.println("S&P 500 Pipeline completed successfully: " + finalScore + " (" + bias + ")");
        } catch (Exception e) {
            System.err.println("Failed to update S&P 500 pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Orchestrates the Russell 2000 (RTY=F) fetching and scoring pipeline.
     */
    public void updateRussell2000Pipeline(CftcService cftcService, TechnicalService technicalService, CompositeScoringEngine scoringEngine) {
        System.out.println("Executing Russell 2000 (RUT) Data Pipeline...");
        try {
            if (latestSummary == null) {
                System.out.println("Cannot update Russell 2000: USD Macro Summary is null.");
                return;
            }

            List<MarketMetric> finalRussellMetrics = buildEquityMacroBase(latestSummary.metrics());

            // Add real Russell 2000 COT
            List<MarketMetric> russellCot = cftcService.fetchRussell2000InstitutionalData();
            finalRussellMetrics.addAll(scoringEngine.applyScores(russellCot));

            // Add real Russell 2000 Technicals
            TechnicalService.AssetTechnicalData russellTech = technicalService.fetchRussell2000Technicals();
            int russellTechScore = scoringEngine.scoreTechnicals(russellTech.currentPrice(), russellTech.sma200(), russellTech.rsi14());
            finalRussellMetrics.add(new MarketMetric("Technical Momentum", russellTech.currentPrice(), 0.0, russellTechScore, MetricCategory.TECHNICALS));

            // Calculate category totals and composite buckets
            Map<String, Integer> russellCategoryScores = calculateCategoryTotals(finalRussellMetrics);

            int macroHealth = russellCategoryScores.getOrDefault("ECONOMIC_GROWTH", 0)
                    + russellCategoryScores.getOrDefault("JOB_MARKET", 0)
                    + russellCategoryScores.getOrDefault("INFLATION", 0);

            int positioningAndFlows = russellCategoryScores.getOrDefault("INSTITUTIONAL_ACTIVITY", 0)
                    + russellCategoryScores.getOrDefault("CAPITAL_FLOWS", 0);

            int technicalMomentum = russellCategoryScores.getOrDefault("TECHNICALS", 0);

            int finalScore = macroHealth + positioningAndFlows + technicalMomentum;
            String bias = scoringEngine.getOverallBiasLabel(finalScore);

            this.latestRussell2000Summary = new GoldSummaryResponse(
                    finalScore,
                    bias,
                    macroHealth,
                    positioningAndFlows,
                    technicalMomentum,
                    russellCategoryScores,
                    finalRussellMetrics
            );

            System.out.println("Russell 2000 Pipeline completed successfully: " + finalScore + " (" + bias + ")");
        } catch (Exception e) {
            System.err.println("Failed to update Russell 2000 pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shared helper to route USD fundamentals for US Equities:
     * Growth & Jobs stay direct (+1 stays +1); Inflation & Yields invert (+1 becomes -1).
     */
    private List<MarketMetric> buildEquityMacroBase(List<MarketMetric> usdMetrics) {
        List<MarketMetric> baseMetrics = new ArrayList<>();
        for (MarketMetric usd : usdMetrics) {
            MetricCategory cat = usd.category();
            if (cat == MetricCategory.ECONOMIC_GROWTH || cat == MetricCategory.JOB_MARKET) {
                baseMetrics.add(new MarketMetric(
                        usd.name(), usd.actualValue(), usd.forecastValue(), usd.scoreDelta(), cat
                ));
            } else if (cat == MetricCategory.INFLATION || cat == MetricCategory.CAPITAL_FLOWS) {
                baseMetrics.add(new MarketMetric(
                        usd.name(), usd.actualValue(), usd.forecastValue(), usd.scoreDelta() * -1, cat
                ));
            }
        }
        return baseMetrics;
    }

    private Map<String, Integer> calculateCategoryTotals(List<MarketMetric> metrics) {
        Map<String, Integer> categoryScores = new HashMap<>();
        for (MarketMetric m : metrics) {
            String catName = m.category().name();
            categoryScores.put(catName, categoryScores.getOrDefault(catName, 0) + m.scoreDelta());
        }
        return categoryScores;
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

        if (latestSilverSummary != null) {
            historicalScoreRepository.save(new HistoricalScoreEntity("XAGUSD", latestSilverSummary.totalScore(), latestSilverSummary.biasLabel()));
            System.out.println("Silver EOD Snapshot saved successfully: " + latestSilverSummary.totalScore() + " (" + latestSilverSummary.biasLabel() + ")");
        }

        if (latestNasdaqSummary != null) {
            historicalScoreRepository.save(new HistoricalScoreEntity("NAS100", latestNasdaqSummary.totalScore(), latestNasdaqSummary.biasLabel()));
            System.out.println("NAS100 EOD Snapshot saved successfully: " + latestNasdaqSummary.totalScore() + " (" + latestNasdaqSummary.biasLabel() + ")");
        }

        if (latestDowSummary != null) {
            historicalScoreRepository.save(new HistoricalScoreEntity("US30", latestDowSummary.totalScore(), latestDowSummary.biasLabel()));
            System.out.println("US30 EOD Snapshot saved successfully: " + latestDowSummary.totalScore() + " (" + latestDowSummary.biasLabel() + ")");
        }

        if (latestSp500Summary != null) {
            historicalScoreRepository.save(new HistoricalScoreEntity("SPX", latestSp500Summary.totalScore(), latestSp500Summary.biasLabel()));
            System.out.println("S&P 500 EOD Snapshot saved successfully: " + latestSp500Summary.totalScore() + " (" + latestSp500Summary.biasLabel() + ")");
        }

        if (latestRussell2000Summary != null) {
            historicalScoreRepository.save(new HistoricalScoreEntity("RUT", latestRussell2000Summary.totalScore(), latestRussell2000Summary.biasLabel()));
            System.out.println("Russell 2000 EOD Snapshot saved successfully: " + latestRussell2000Summary.totalScore() + " (" + latestRussell2000Summary.biasLabel() + ")");
        }
    }
}