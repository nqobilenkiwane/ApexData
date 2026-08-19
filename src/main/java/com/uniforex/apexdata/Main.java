package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
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

            // 1. FETCH ALL DATA
            List<MarketMetric> rawFredMetrics = fredService.fetchMacroData();
            List<MarketMetric> institutionalMetrics = cftcService.fetchInstitutionalData(); // Now returns a list
            TechnicalService.TechnicalData techData = technicalService.fetchUsdTechnicals();
            List<MarketMetric> fmpCalendarMetrics = calendarService.fetchLiveCalendarEvents();

            // 2. ENRICH & MERGE
            List<MarketMetric> combinedMetrics = new ArrayList<>();
            List<String> processedNames = new ArrayList<>();

            // A: Match FRED metrics with Calendar Estimates
            for (MarketMetric fred : rawFredMetrics) {
                double estimate = fred.forecastValue();
                for (MarketMetric fmp : fmpCalendarMetrics) {
                    if (fmp.name().equals(fred.name())) {
                        estimate = fmp.forecastValue();
                        break;
                    }
                }
                combinedMetrics.add(new MarketMetric(fred.name(), fred.actualValue(), estimate, 0, fred.category()));
                processedNames.add(fred.name());
            }

            // B: Add metrics that ONLY exist on the Calendar
            for (MarketMetric fmp : fmpCalendarMetrics) {
                if (!processedNames.contains(fmp.name())) {
                    combinedMetrics.add(fmp);
                }
            }

            // C: Inject Institutional Metrics into the master list
            combinedMetrics.addAll(institutionalMetrics);

            // 3. SCORE EVERYTHING IN ONE PASS
            List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(combinedMetrics));

            // Technicals remain standalone for now
            int technicalScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());
            scoredMetrics.add(new MarketMetric("Technical Momentum", techData.currentPrice(), 0.0, technicalScore, MetricCategory.TECHNICALS));

            // 4. AGGREGATE
            Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);
            int totalScore = engine.calculateTotalScore(scoredMetrics);
            String overallBias = engine.getOverallBiasLabel(totalScore);

            // 5. DASHBOARD OUTPUT
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

                            // --- NEW CUSTOM CFTC FORMATTING ---
                            if (m.name().equals("COT Net Positioning")) {
                                String bias = m.actualValue() >= 0 ? "Long" : "Short";
                                valueStr = String.format("%,.0f (%s) contracts", Math.abs(m.actualValue()), bias);
                            } else if (m.name().equals("COT WoW Delta")) {
                                String actBias = m.actualValue() >= 0 ? "Long" : "Short";
                                String prevBias = m.forecastValue() >= 0 ? "Long" : "Short";
                                double diff = m.actualValue() - m.forecastValue();
                                String diffAction = diff >= 0 ? "Bought" : "Sold";

                                valueStr = String.format("%,.0f (%s)", Math.abs(m.actualValue()), actBias);
                                estStr = String.format(" | Prev: %,.0f (%s)", Math.abs(m.forecastValue()), prevBias);
                                surpriseStr = String.format(" | Diff: %+,.0f (%s)", diff, diffAction);
                            } else if (m.name().equals("COT Long Percentage")) {
                                valueStr = String.format("%.1f%%", m.actualValue());
                                String signal = m.actualValue() >= 80 ? "OVERCROWDED" : m.actualValue() <= 20 ? "SHORT SQUEEZE" : m.actualValue() > 50 ? "BULLISH TREND" : "BEARISH TREND";
                                estStr = String.format(" | Signal: %-19s", signal); // Pad string to keep alignment clean
                                surpriseStr = "";
                            }
                            // --- EXISTING FORMATTING ---
                            else if (m.name().contains("Momentum") && m.category() == MetricCategory.CAPITAL_FLOWS) {
                                valueStr = String.format("%+.2f%%", m.actualValue());
                                estStr = String.format(" | MA: %+.2f%%", m.forecastValue());
                                surpriseStr = String.format(" | Diff: %+.2f%%", (m.actualValue() - m.forecastValue()));
                            } else if (m.name().contains("Momentum")) {
                                valueStr = String.format("%,.4f", m.actualValue());
                            } else if (m.name().contains("NFP") || m.name().contains("ADP")) {
                                valueStr = String.format("%+.0fK", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %+.0fK", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+.0fK", (m.actualValue() - m.forecastValue()));
                                }
                            } else if (m.name().contains("JOLTS")) {
                                valueStr = String.format("%.2fM", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %.2fM", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+.2fM", (m.actualValue() - m.forecastValue()));
                                }
                            } else if (m.name().contains("Claims")) {
                                valueStr = String.format("%,.0f", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %,.0f", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+,.0f", (m.actualValue() - m.forecastValue()));
                                }
                            } else if (m.name().contains("Sentiment") || m.name().contains("PMI")) {
                                valueStr = String.format("%.1f", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %.1f", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+.1f", (m.actualValue() - m.forecastValue()));
                                }
                            } else {
                                valueStr = String.format("%,.2f%%", m.actualValue());
                                if (m.forecastValue() != 0.0) {
                                    estStr = String.format(" | Est: %,.2f%%", m.forecastValue());
                                    surpriseStr = String.format(" | Surp: %+.2f%%", (m.actualValue() - m.forecastValue()));
                                }
                            }

                            // Dynamic Layout Assembly
                            String middlePart = "";
                            if (!estStr.isEmpty() || !surpriseStr.isEmpty()) {
                                middlePart = estStr + " " + surpriseStr;
                            }

                            String displayLine = String.format("  %-22s | Act: %-19s", m.name(), valueStr);

                            if (!middlePart.isEmpty()) {
                                displayLine += String.format("%-42s", middlePart);
                            } else {
                                displayLine += String.format("%-42s", "");
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