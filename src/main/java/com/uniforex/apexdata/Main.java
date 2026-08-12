package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.CotObservation;
import com.uniforex.apexdata.service.CftcService;
import com.uniforex.apexdata.service.FredService;
import com.uniforex.apexdata.service.TechnicalService;
import io.github.cdimascio.dotenv.Dotenv; // ADD THIS IMPORT

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

        // ... [The rest of your try-catch block and printing logic remains exactly the same] ...

        try {
            System.out.println("Fetching Macroeconomic, Institutional & Technical Data...\n");

            FredService.FredMacroData macroData = fredService.fetchMacroData();
            CotObservation cotData = cftcService.fetchLatestUsdCot();
            TechnicalService.TechnicalData techData = technicalService.fetchUsdTechnicals();

            // Run Tally Scoring Logic
            int macroScore = engine.scoreMacroFundamentals(macroData.interestRate(), macroData.yoyInflation());
            int laborScore = engine.scoreLaborMarket(macroData.unemploymentRate());
            int gdpScore = engine.scoreGdp(macroData.gdp());
            int retailScore = engine.scoreRetailSales(macroData.momRetailSales());
            int nfpScore = engine.scoreNfp(macroData.nfpChange());
            int institutionalScore = engine.scoreInstitutionalPositioning(cotData.getNetPosition());
            int technicalScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());

            // Unweighted Tally Across All 3 Pillars
            int totalScore = macroScore + laborScore + gdpScore + retailScore + nfpScore + institutionalScore + technicalScore;
            String overallBias = engine.getOverallBiasLabel(totalScore);

            // Output Dashboard
            System.out.println("========================================");
            System.out.println("          APEX DATA DASHBOARD           ");
            System.out.println("========================================");

            System.out.println("PILLAR 1: MACRO FUNDAMENTALS");
            System.out.printf("  Interest Rate: %.2f%%\n", macroData.interestRate());
            System.out.printf("  YoY Inflation: %.2f%%\n", macroData.yoyInflation());
            System.out.printf("  Real Yield:    %.2f%%\n", (macroData.interestRate() - macroData.yoyInflation()));
            System.out.printf("  [RATES SCORE: %+d]\n", macroScore);
            System.out.println("  ----------------");
            System.out.printf("  Unemployment:  %.1f%%\n", macroData.unemploymentRate());
            System.out.printf("  [LABOR SCORE: %+d]\n", laborScore);
            System.out.println("  ----------------");
            System.out.printf("  NFP (Jobs):    %+.0fK\n", macroData.nfpChange());
            System.out.printf("  [NFP SCORE:   %+d]\n", nfpScore);
            System.out.println("  ----------------");
            System.out.printf("  Real GDP:      %.1f%%\n", macroData.gdp());
            System.out.printf("  [GDP SCORE:   %+d]\n", gdpScore);
            System.out.println("  ----------------");
            System.out.printf("  Retail Sales:  %.2f%% (MoM)\n", macroData.momRetailSales());
            System.out.printf("  [RETAIL SCORE:%+d]\n", retailScore);

            System.out.println("\nPILLAR 2: INSTITUTIONAL POSITIONING");
            System.out.printf("  Longs (Buys):  %,.0f contracts\n", cotData.getLongPositions());
            System.out.printf("  Shorts (Sells):%,.0f contracts\n", cotData.getShortPositions());
            System.out.printf("  Net Position:  %,.0f contracts\n", cotData.getNetPosition());
            System.out.printf("  [INST. SCORE: %+d]\n", institutionalScore);

            System.out.println("\nPILLAR 3: TECHNICAL ANALYSIS (TA4J)");
            System.out.printf("  USD/EUR Price: %.4f\n", techData.currentPrice());
            System.out.printf("  USD/EUR 200 SMA:%.4f\n", techData.sma200());
            System.out.printf("  14-Day RSI:    %.1f\n", techData.rsi14());
            System.out.printf("  [TECH SCORE:  %+d]\n", technicalScore);

            System.out.println("----------------------------------------");
            System.out.println(">> OVERALL USD COMPOSITE TALLY <<");
            System.out.printf("   TOTAL SCORE:  %+d\n", totalScore);
            System.out.println("   MARKET BIAS:  " + overallBias);
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}