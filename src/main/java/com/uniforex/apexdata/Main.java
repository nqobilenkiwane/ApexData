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

            // --- 2. Fetch & Parse CPI (Inflation) ---
            String cpiJson = client.fetchRawJson(cpiEndpoint);
            FredResponse cpiResponse = mapper.readValue(cpiJson, FredResponse.class);

            // Sort CPI observations by date descending (newest first)
            List<Observation> sortedCpi = cpiResponse.observations().stream()
                    .sorted(Comparator.comparing(Observation::date).reversed())
                    .toList();

            Observation latestCpi = sortedCpi.get(0);
            // Grab the CPI reading from exactly 12 months prior to calculate YoY inflation
            Observation yearAgoCpi = sortedCpi.get(12);

            // Calculate YoY Inflation Percentage: ((Current - Previous) / Previous) * 100
            double currentCpiVal = latestCpi.getRateAsDouble();
            double yearAgoCpiVal = yearAgoCpi.getRateAsDouble();
            double yoyInflation = ((currentCpiVal - yearAgoCpiVal) / yearAgoCpiVal) * 100;

            // --- 3. Output Dashboard ---
            System.out.println("========================================");
            System.out.println("          APEX DATA DASHBOARD           ");
            System.out.println("========================================");

            System.out.println("1. US Interest Rate (FEDFUNDS)");
            System.out.println("   Date: " + latestRate.date());
            System.out.println("   Rate: " + latestRate.getRateAsDouble() + "%\n");

            System.out.println("2. US YoY Inflation (CPI)");
            System.out.println("   Date: " + latestCpi.date());
            System.out.printf("   Rate: %.2f%%\n", yoyInflation);
            System.out.println("   (Raw Index: " + currentCpiVal + ")");

            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}