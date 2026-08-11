package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.CotObservation;
import com.uniforex.apexdata.service.CftcService;
import com.uniforex.apexdata.service.FredService;

public class Main {

    private static final String FRED_API_KEY = System.getenv("FRED_API_KEY");

    public static void main(String[] args) {
        System.out.println("Initializing Apex Data Engine...");

        if (FRED_API_KEY == null || FRED_API_KEY.isBlank()) {
            System.err.println("CRITICAL ERROR: FRED_API_KEY environment variable is missing.");
            System.exit(1);
        }

        // Shared dependencies
        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        CompositeScoringEngine engine = new CompositeScoringEngine();

        // Instantiate domain services
        FredService fredService = new FredService(client, mapper, FRED_API_KEY);
        CftcService cftcService = new CftcService(client, mapper);

        try {
            System.out.println("Fetching Macroeconomic & Institutional Data...\n");

            // Execute service calls
            FredService.FredMacroData macroData = fredService.fetchMacroData();
            CotObservation cotData = cftcService.fetchLatestUsdCot();

            // Run Tally Scoring Logic
            int macroScore = engine.scoreMacroFundamentals(macroData.interestRate(), macroData.yoyInflation());
            int institutionalScore = engine.scoreInstitutionalPositioning(cotData.getNetPosition());
            int totalScore = macroScore + institutionalScore;
            String overallBias = engine.getOverallBiasLabel(totalScore);

            // Output Dashboard
            System.out.println("========================================");
            System.out.println("          APEX DATA DASHBOARD           ");
            System.out.println("========================================");

            System.out.println("PILLAR 1: MACRO FUNDAMENTALS");
            System.out.printf("  Interest Rate: %.2f%%\n", macroData.interestRate());
            System.out.printf("  YoY Inflation: %.2f%%\n", macroData.yoyInflation());
            System.out.printf("  Real Yield:    %.2f%%\n", (macroData.interestRate() - macroData.yoyInflation()));
            System.out.printf("  [SCORE: %+d]\n", macroScore);

            System.out.println("\nPILLAR 2: INSTITUTIONAL POSITIONING");
            System.out.printf("  Longs (Buys):  %,.0f contracts\n", cotData.getLongPositions());
            System.out.printf("  Shorts (Sells):%,.0f contracts\n", cotData.getShortPositions());
            System.out.printf("  Net Position:  %,.0f contracts\n", cotData.getNetPosition());
            System.out.printf("  [SCORE: %+d]\n", institutionalScore);

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