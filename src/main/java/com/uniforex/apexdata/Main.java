package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.CotObservation;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.service.CftcService;
import com.uniforex.apexdata.service.EconomicCalendarService;
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

        String fredApiKey = dotenv.get("FRED_API_KEY");
        String alphaVantageApiKey = dotenv.get("ALPHA_VANTAGE_API_KEY");
        String fmpApiKey = dotenv.get("FMP_API_KEY");

        if (fredApiKey == null || alphaVantageApiKey == null || fmpApiKey == null) {
            System.err.println("CRITICAL ERROR: API keys are missing from the .env file.");
            System.exit(1);
        }

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        CompositeScoringEngine engine = new CompositeScoringEngine();

        FredService fredService = new FredService(client, mapper, fredApiKey);
        CftcService cftcService = new CftcService(client, mapper);
        TechnicalService technicalService = new TechnicalService(client, mapper, alphaVantageApiKey);
        EconomicCalendarService calendarService = new EconomicCalendarService(client, mapper, fmpApiKey);

        try {
            System.out.println("Fetching Macroeconomic, Institutional & Technical Data...\n");

            // --- 1. FETCH RAW DATA ---
            List<MarketMetric> rawFredMetrics = fredService.fetchMacroData();
            CotObservation cotData = cftcService.fetchLatestUsdCot();
            TechnicalService.TechnicalData techData = technicalService.fetchUsdTechnicals();

            // Fetch live calendar estimates from FMP
            List<MarketMetric> fmpCalendarMetrics = calendarService.fetchLiveCalendarEvents();

            // --- 2. ENRICH FRED DATA WITH FMP ESTIMATES ---
            List<MarketMetric> enrichedMetrics = new ArrayList<>();
            for (MarketMetric fred : rawFredMetrics) {
                double estimate = 0.0;
                for (MarketMetric fmp : fmpCalendarMetrics) {
                    if (fmp.name().equals(fred.name())) {
                        estimate = fmp.forecastValue();
                        break;
                    }
                }
                enrichedMetrics.add(new MarketMetric(fred.name(), fred.actualValue(), estimate, 0, fred.category()));
            }

            // --- 3. APPLY SCORING LOGIC ---
            List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(enrichedMetrics));

            int institutionalScore = engine.scoreInstitutionalPositioning(cotData.getNetPosition());
            int technicalScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());

            scoredMetrics.add(new MarketMetric("COT Net Positioning", cotData.getNetPosition(), 0.0, institutionalScore, MetricCategory.INSTITUTIONAL_ACTIVITY));
            scoredMetrics.add(new MarketMetric("Technical Momentum", techData.currentPrice(), 0.0, technicalScore, MetricCategory.TECHNICALS));

            // --- 4. AGGREGATE TOTALS ---
            Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);
            int totalScore = engine.calculateTotalScore(scoredMetrics);
            String overallBias = engine.getOverallBiasLabel(totalScore);

            // --- 5. DYNAMIC DASHBOARD OUTPUT ---
            System.out.println("==========================================================================");
            System.out.println("                           APEX DATA DASHBOARD                            ");
            System.out.println("==========================================================================");

            for (MetricCategory category : MetricCategory.values()) {
                System.out.printf("\n>> %s [CATEGORY SCORE: %+d]\n", category.name().replace("_", " "), categoryScores.getOrDefault(category, 0));
                System.out.println("--------------------------------------------------------------------------");

                scoredMetrics.stream()
                        .filter(m -> m.category() == category)
                        .forEach(m -> {
                            String valueStr;
                            String estStr = "";
                            String surpriseStr = "";

                            if (m.name().contains("COT")) {
                                valueStr = String.format("%,.0f contracts", m.actualValue());
                            } else if (m.name().contains("Momentum")) {
                                valueStr = String.format("%,.4f", m.actualValue());
                            } else if (m.name().contains("NFP")) {
                                valueStr = String.format("%+.0fK", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %+.0fK", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+.0fK", (m.actualValue() - m.forecastValue()));
                                }
                            } else {
                                valueStr = String.format("%,.2f%%", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %,.2f%%", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+.2f%%", (m.actualValue() - m.forecastValue()));
                                }
                            }

                            // Build the final display line dynamically based on whether estimates exist
                            String displayLine = String.format("  %-20s | Act: %-16s", m.name(), valueStr);
                            if (!estStr.isEmpty()) {
                                displayLine += String.format("%-16s %-16s", estStr, surpriseStr);
                            } else {
                                // Add blank spacing to keep columns aligned if no estimate exists
                                displayLine += String.format("%-33s", "");
                            }
                            displayLine += String.format(" | Score: %+d", m.scoreDelta());

                            System.out.println(displayLine);
                        });
            }

            System.out.println("\n==========================================================================");
            System.out.println(">> OVERALL USD COMPOSITE TALLY <<");
            System.out.printf("   TOTAL SCORE:  %+d\n", totalScore);
            System.out.println("   MARKET BIAS:  " + overallBias);
            System.out.println("==========================================================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}