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
        ScoringEngine engine = new ScoringEngine();

        // Instantiate domain services
        FredService fredService = new FredService(client, mapper, FRED_API_KEY);
        CftcService cftcService = new CftcService(client, mapper);

        try {
            System.out.println("Fetching Macroeconomic & Institutional Data...\n");

            // Execute service calls
            FredService.FredMacroData macroData = fredService.fetchMacroData();
            CotObservation cotData = cftcService.fetchLatestUsdCot();

            // Run Scoring Logic
            String usdBias = engine.evaluateFundamentalBias(macroData.interestRate(), macroData.yoyInflation());
            double realYield = engine.calculateRealYield(macroData.interestRate(), macroData.yoyInflation());

            // Output Dashboard
            System.out.println("========================================");
            System.out.println("          APEX DATA DASHBOARD           ");
            System.out.println("========================================");

            System.out.println("PILLAR 1: MACRO FUNDAMENTALS");
            System.out.printf("  Interest Rate: %.2f%% (As of %s)\n", macroData.interestRate(), macroData.interestRateDate());
            System.out.printf("  YoY Inflation: %.2f%% (As of %s)\n", macroData.yoyInflation(), macroData.cpiDate());
            System.out.printf("  Real Yield:    %.2f%%\n", realYield);

            System.out.println("\nPILLAR 2: INSTITUTIONAL POSITIONING (HEDGE FUNDS)");
            System.out.printf("  Report Date:   %s\n", cotData.report_date_as_yyyy_mm_dd());
            System.out.printf("  Longs (Buys):  %,.0f contracts\n", cotData.getLongPositions());
            System.out.printf("  Shorts (Sells):%,.0f contracts\n", cotData.getShortPositions());
            System.out.printf("  Net Position:  %,.0f contracts\n", cotData.getNetPosition());

            System.out.println("----------------------------------------");
            System.out.println(">> OVERALL USD MACRO BIAS <<");
            System.out.println("   " + usdBias);

            String institutionalFlow = cotData.getNetPosition() > 0 ? "BULLISH (Net Long)" : "BEARISH (Net Short)";
            System.out.println("   Institutional Flow: " + institutionalFlow);
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}