package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.FredResponse;
import com.uniforex.apexdata.model.Observation;

import java.util.Comparator;
import java.util.List;

public class Main {

    // Keep your actual API key here
    private static final String FRED_API_KEY = "eda0aae5142807f2f5fc1fe5b71873c7";

    public static void main(String[] args) {
        System.out.println("Initializing Apex Data Engine...");

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        ScoringEngine engine = new ScoringEngine();

        // Endpoints
        String ratesEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json",
                FRED_API_KEY
        );
        String cpiEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=CPIAUCSL&api_key=%s&file_type=json",
                FRED_API_KEY
        );

        try {
            System.out.println("Fetching Macroeconomic Data from FRED...\n");

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

            // --- 3. Run the Scoring Engine ---
            String usdBias = engine.evaluateFundamentalBias(currentInterestRate, yoyInflation);
            double realYield = engine.calculateRealYield(currentInterestRate, yoyInflation);

            // --- 4. Output Dashboard ---
            System.out.println("========================================");
            System.out.println("          APEX DATA DASHBOARD           ");
            System.out.println("========================================");

            System.out.println("1. US Interest Rate (FEDFUNDS)");
            System.out.printf("   Rate: %.2f%% (As of %s)\n\n", currentInterestRate, latestRate.date());

            System.out.println("2. US YoY Inflation (CPI)");
            System.out.printf("   Rate: %.2f%% (As of %s)\n\n", yoyInflation, latestCpi.date());

            System.out.println("----------------------------------------");
            System.out.println(">> FUNDAMENTAL SCORING <<");
            System.out.printf("Real Yield: %.2f%%\n", realYield);
            System.out.println("USD Bias:   " + usdBias);
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}