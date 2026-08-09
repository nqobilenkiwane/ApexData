package com.uniforex.apexdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.model.FredResponse;
import com.uniforex.apexdata.model.Observation;

import java.util.Comparator;

public class Main {

    private static final String FRED_API_KEY = "eda0aae5142807f2f5fc1fe5b71873c7";

    public static void main(String[] args) {
        System.out.println("Initializing Apex Data Engine...");

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();

        String fredEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json",
                FRED_API_KEY
        );

        try {
            System.out.println("Fetching Macroeconomic Data from FRED...");
            String jsonResponse = client.fetchRawJson(fredEndpoint);

            // Map JSON string into our FredResponse Record
            FredResponse parsedResponse = mapper.readValue(jsonResponse, FredResponse.class);

            // Find the most recent date entry using Java Streams
            Observation latestRate = parsedResponse.observations().stream()
                    .max(Comparator.comparing(Observation::date))
                    .orElseThrow(() -> new RuntimeException("No observations found!"));

            System.out.println("----------------------------------------");
            System.out.println("SUCCESS! Latest US Interest Rate:");
            System.out.println("Date: " + latestRate.date());
            System.out.println("Rate: " + latestRate.getRateAsDouble() + "%");
            System.out.println("----------------------------------------");

        } catch (Exception e) {
            System.err.println("Failed to fetch or parse market data: " + e.getMessage());
        }
    }
}