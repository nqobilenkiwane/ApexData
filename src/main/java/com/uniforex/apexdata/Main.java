package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.CotObservation;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.service.CftcService;
import com.uniforex.apexdata.service.FredService;
import com.uniforex.apexdata.service.TechnicalService;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        System.out.println("Initializing Apex Data Engine...");

        // 1. Load the .env file
        Dotenv dotenv = Dotenv.load();

        // 2. Fetch the keys from the loaded file
        String fredApiKey = dotenv.get("FRED_API_KEY");
        String alphaVantageApiKey = dotenv.get("ALPHA_VANTAGE_API_KEY");

        if (fredApiKey == null || fredApiKey.isBlank()) {
            System.err.println("CRITICAL ERROR: FRED_API_KEY is missing from the .env file.");
            System.exit(1);
        }
        if (alphaVantageApiKey == null || alphaVantageApiKey.isBlank()) {
            System.err.println("CRITICAL ERROR: ALPHA_VANTAGE_API_KEY is missing from the .env file.");
            System.exit(1);
        }

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        CompositeScoringEngine engine = new CompositeScoringEngine();

        // 3. Pass the loaded keys into your services
        FredService fredService = new FredService(client, mapper, fredApiKey);
        CftcService cftcService = new CftcService(client, mapper);
        TechnicalService technicalService = new TechnicalService(client, mapper, alphaVantageApiKey);

        try {
            System.out.println("Fetching Macroeconomic, Institutional & Technical Data...\n");

            // --- 1. FETCH RAW DATA ---
            List<MarketMetric> rawFredMetrics = fredService.fetchMacroData();
            CotObservation cotData = cftcService.fetchLatestUsdCot();
            TechnicalService.TechnicalData techData = technicalService.fetchUsdTechnicals();

            // --- 2. APPLY SCORING LOGIC ---
            // Score the FRED macroeconomic metrics dynamically
            List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(rawFredMetrics));

            // Manually score Institutional and Technical data, then wrap them in our universal MarketMetric container
            int institutionalScore = engine.scoreInstitutionalPositioning(cotData.getNetPosition());
            int technicalScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());

            scoredMetrics.add(new MarketMetric(
                    "COT Net Positioning", cotData.getNetPosition(), 0.0, institutionalScore, MetricCategory.INSTITUTIONAL_ACTIVITY));
            scoredMetrics.add(new MarketMetric(
                    "Technical Momentum", techData.currentPrice(), 0.0, technicalScore, MetricCategory.TECHNICALS));

            // --- 3. AGGREGATE TOTALS ---
            Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);
            int totalScore = engine.calculateTotalScore(scoredMetrics);
            String overallBias = engine.getOverallBiasLabel(totalScore);

            // --- 4. DYNAMIC DASHBOARD OUTPUT ---
            System.out.println("==============================================================");
            System.out.println("                     APEX DATA DASHBOARD                      ");
            System.out.println("==============================================================");

            // Loop through our strict enum categories and print their contents automatically
            for (MetricCategory category : MetricCategory.values()) {
                System.out.printf("\n>> %s [CATEGORY SCORE: %+d]\n", category.name().replace("_", " "), categoryScores.getOrDefault(category, 0));
                System.out.println("--------------------------------------------------------------");

                scoredMetrics.stream()
                        .filter(m -> m.category() == category)
                        .forEach(m -> {
                            // A quick format switch to keep the UI looking clean based on the metric type
                            String valueStr;
                            if (m.name().contains("COT")) {
                                valueStr = String.format("%,.0f contracts", m.actualValue());
                            } else if (m.name().contains("Momentum")) {
                                valueStr = String.format("%,.4f (Price)", m.actualValue());
                            } else if (m.name().contains("NFP")) {
                                valueStr = String.format("%+.0fK", m.actualValue());
                            } else {
                                valueStr = String.format("%,.2f%%", m.actualValue());
                            }

                            System.out.printf("  %-25s | Actual: %-20s | Score: %+d\n", m.name(), valueStr, m.scoreDelta());
                        });
            }

            System.out.println("\n==============================================================");
            System.out.println(">> OVERALL USD COMPOSITE TALLY <<");
            System.out.printf("   TOTAL SCORE:  %+d\n", totalScore);
            System.out.println("   MARKET BIAS:  " + overallBias);
            System.out.println("==============================================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}