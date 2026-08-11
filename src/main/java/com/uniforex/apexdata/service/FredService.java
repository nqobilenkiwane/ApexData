package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.FredResponse;
import com.uniforex.apexdata.model.Observation;

import java.util.Comparator;
import java.util.List;

public class FredService {

    private final MarketDataClient client;
    private final ObjectMapper mapper;
    private final String apiKey;

    public FredService(MarketDataClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    // Immutable DTO holding macro data for downstream consumers
    public record FredMacroData(
            double interestRate,
            String interestRateDate,
            double yoyInflation,
            String cpiDate
    ) {}

    public FredMacroData fetchMacroData() throws Exception {
        String ratesEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json",
                apiKey
        );
        String cpiEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=CPIAUCSL&api_key=%s&file_type=json",
                apiKey
        );

        // Fetch & Parse Interest Rates
        String ratesJson = client.fetchRawJson(ratesEndpoint);
        FredResponse ratesResponse = mapper.readValue(ratesJson, FredResponse.class);
        Observation latestRate = ratesResponse.observations().stream()
                .max(Comparator.comparing(Observation::date))
                .orElseThrow(() -> new RuntimeException("No rate data found"));

        // Fetch & Parse CPI
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

        return new FredMacroData(
                latestRate.getRateAsDouble(),
                latestRate.date(),
                yoyInflation,
                latestCpi.date()
        );
    }
}