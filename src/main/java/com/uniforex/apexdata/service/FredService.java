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

    // Expanded DTO holding the new GDP data
    public record FredMacroData(
            double interestRate,
            String interestRateDate,
            double yoyInflation,
            String cpiDate,
            double unemploymentRate,
            String unemploymentDate,
            double gdp,
            String gdpDate
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
        String unrateEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=UNRATE&api_key=%s&file_type=json",
                apiKey
        );
        String gdpEndpoint = String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=A191RL1Q225SBEA&api_key=%s&file_type=json",
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

        // Fetch & Parse Unemployment Rate
        String unrateJson = client.fetchRawJson(unrateEndpoint);
        FredResponse unrateResponse = mapper.readValue(unrateJson, FredResponse.class);
        Observation latestUnrate = unrateResponse.observations().stream()
                .max(Comparator.comparing(Observation::date))
                .orElseThrow(() -> new RuntimeException("No unemployment data found"));

        // Fetch & Parse Real GDP
        String gdpJson = client.fetchRawJson(gdpEndpoint);
        FredResponse gdpResponse = mapper.readValue(gdpJson, FredResponse.class);
        Observation latestGdp = gdpResponse.observations().stream()
                .max(Comparator.comparing(Observation::date))
                .orElseThrow(() -> new RuntimeException("No GDP data found"));

        return new FredMacroData(
                latestRate.getRateAsDouble(),
                latestRate.date(),
                yoyInflation,
                latestCpi.date(),
                latestUnrate.getRateAsDouble(),
                latestUnrate.date(),
                latestGdp.getRateAsDouble(),
                latestGdp.date()
        );
    }
}