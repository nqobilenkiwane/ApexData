package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.CotObservation;
import com.uniforex.apexdata.model.FredResponse;
import com.uniforex.apexdata.model.Observation;

import java.util.Comparator;
import java.util.List;

public class Main {

    private static final String FRED_API_KEY = System.getenv("FRED_API_KEY");

    public static void main(String[] args) {
        System.out.println("Initializing Apex Data Engine...");

        if (FRED_API_KEY == null || FRED_API_KEY.isBlank()) {
            System.err.println("CRITICAL ERROR: FRED_API_KEY environment variable is missing.");
            System.exit(1);
        }

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        ScoringEngine engine = new ScoringEngine();

        // --- Endpoints ---
        String ratesEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json",
                FRED_API_KEY
        );
        String cpiEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=CPIAUCSL&api_key=%s&file_type=json",
                FRED_API_KEY
        );

        // CFTC Socrata API for Traders in Financial Futures (Leveraged Money)
        String cftcEndpoint = "https://publicreporting.cftc.gov/resource/gpe5-46if.json" +
                "?cftc_contract_market_code=098662" +
                "&$order=report_date_as_yyyy_mm_dd%20DESC" +
                "&$limit=1";

        try {
            System.out.println("Fetching Macroeconomic & Institutional Data...\n");

            // --- 1. Fetch & Parse Interest Rates ---
            String ratesJson = client.fetchRawJson(ratesEndpoint);
            FredResponse ratesResponse = mapper.readValue(ratesJson, FredResponse.class);

            Observation latestRate = ratesResponse.observations().stream()
                    .max(Comparator.comparing(Observation::date))
                    .orElseThrow(() -> new RuntimeException("No rate data found"));
            double currentInterestRate = latestRate.getRateAsDouble();

            // --- 2. Fetch & Parse CPI (Inflation) ---
            String cpiJson = client.fetchRawJson(cpiEndpoint);
            FredResponse cpiResponse = mapper.readValue(cpiJson, FredResponse.class);

            List<Observation> sortedCpi = cpiResponse.observations().stream()
                    .sorted(Comparator.comparing(Observation::date).reversed())
                    .toList();

            Observation latestCpi = sortedCpi.get(0);
            Observation yearAgoCpi = sortedCpi.get(12);

            double currentCpiVal = latestCpi.getRateAsDouble();
            double yearAgoCpiVal = yearAgoCpi.getRateAsDouble();
            double yoyInflation = ((currentCpiVal - yearAgoCpiVal) / yearAgoCpiVal) * 100;

            // --- 3. Fetch & Parse CFTC Institutional Positioning ---
            String cftcJson = client.fetchRawJson(cftcEndpoint);
            // Socrata returns a JSON array, so we map it to an array of CotObservation
            CotObservation[] cotData = mapper.readValue(cftcJson, CotObservation[].class);

            if (cotData.length == 0) {
                throw new RuntimeException("No COT data found for the specified contract.");
            }
            CotObservation latestCot = cotData[0];

            // --- 4. Run the Scoring Engine ---
            String usdBias = engine.evaluateFundamentalBias(currentInterestRate, yoyInflation);
            double realYield = engine.calculateRealYield(currentInterestRate, yoyInflation);

            // --- 5. Output Dashboard ---
            System.out.println("========================================");
            System.out.println("          APEX DATA DASHBOARD           ");
            System.out.println("========================================");

            System.out.println("PILLAR 1: MACRO FUNDAMENTALS");
            System.out.printf("  Interest Rate: %.2f%% (As of %s)\n", currentInterestRate, latestRate.date());
            System.out.printf("  YoY Inflation: %.2f%% (As of %s)\n", yoyInflation, latestCpi.date());
            System.out.printf("  Real Yield:    %.2f%%\n", realYield);

            System.out.println("\nPILLAR 2: INSTITUTIONAL POSITIONING (HEDGE FUNDS)");
            System.out.printf("  Report Date:   %s\n", latestCot.report_date_as_yyyy_mm_dd());
            System.out.printf("  Longs (Buys):  %,.0f contracts\n", latestCot.getLongPositions());
            System.out.printf("  Shorts (Sells):%,.0f contracts\n", latestCot.getShortPositions());
            System.out.printf("  Net Position:  %,.0f contracts\n", latestCot.getNetPosition());

            System.out.println("----------------------------------------");
            System.out.println(">> OVERALL USD MACRO BIAS <<");
            System.out.println("   " + usdBias);

            // Add a quick visual indicator for Institutional flow
            String institutionalFlow = latestCot.getNetPosition() > 0 ? "BULLISH (Net Long)" : "BEARISH (Net Short)";
            System.out.println("   Institutional Flow: " + institutionalFlow);
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}