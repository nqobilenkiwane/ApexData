package com.uniforex.apexdata.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.Observation;

import java.util.ArrayList;
import java.util.Arrays;
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

    // Maps Alpha Vantage's JSON array "data" to your existing Observation class
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlphaResponse(List<Observation> data) {}

    public List<MarketMetric> fetchMacroData() throws Exception {

        // Alpha Vantage Core Economic Endpoints
        String cpiEndpoint = String.format("https://www.alphavantage.co/query?function=CPI&interval=monthly&apikey=%s", apiKey);
        String unrateEndpoint = String.format("https://www.alphavantage.co/query?function=UNEMPLOYMENT&apikey=%s", apiKey);
        String gdpEndpoint = String.format("https://www.alphavantage.co/query?function=REAL_GDP&interval=quarterly&apikey=%s", apiKey);
        String retailEndpoint = String.format("https://www.alphavantage.co/query?function=RETAIL_SALES&apikey=%s", apiKey);
        String nfpEndpoint = String.format("https://www.alphavantage.co/query?function=NONFARM_PAYROLL&apikey=%s", apiKey);
        String sentimentEndpoint = String.format("https://www.alphavantage.co/query?function=CONSUMER_SENTIMENT&apikey=%s", apiKey);

        // Treasury Yield Curve Endpoints
        String dgs2Endpoint = String.format("https://www.alphavantage.co/query?function=TREASURY_YIELD&interval=daily&maturity=2year&apikey=%s", apiKey);
        String dgs10Endpoint = String.format("https://www.alphavantage.co/query?function=TREASURY_YIELD&interval=daily&maturity=10year&apikey=%s", apiKey);

        // Fetch & Parse Data
        List<Observation> sortedCpi = getSortedObservations(cpiEndpoint);
        double yoyInflation = ((sortedCpi.get(0).getRateAsDouble() - sortedCpi.get(12).getRateAsDouble()) / sortedCpi.get(12).getRateAsDouble()) * 100;

        Observation latestUnrate = getLatestObservation(unrateEndpoint);
        Observation latestGdp = getLatestObservation(gdpEndpoint);

        List<Observation> sortedRetail = getSortedObservations(retailEndpoint);
        double momRetailSales = ((sortedRetail.get(0).getRateAsDouble() - sortedRetail.get(1).getRateAsDouble()) / sortedRetail.get(1).getRateAsDouble()) * 100;

        List<Observation> sortedNfp = getSortedObservations(nfpEndpoint);
        double nfpChange = sortedNfp.get(0).getRateAsDouble() - sortedNfp.get(1).getRateAsDouble();

        Observation latestSentiment = getLatestObservation(sentimentEndpoint);

        // Bond Market Calculations
        List<Observation> sorted2Y = getSortedObservations(dgs2Endpoint);
        List<Observation> sorted10Y = getSortedObservations(dgs10Endpoint);

        double latest2Y = sorted2Y.get(0).getRateAsDouble();
        double latest10Y = sorted10Y.get(0).getRateAsDouble();

        double sum2Y = 0;
        int limit = Math.min(22, sorted2Y.size());
        for (int i = 0; i < limit; i++) {
            sum2Y += sorted2Y.get(i).getRateAsDouble();
        }
        double ma2Y = sum2Y / limit;

        double realYield = latest10Y - yoyInflation;
        double yieldCurve = latest10Y - latest2Y;

        return Arrays.asList(
                new MarketMetric("Unemployment Rate", latestUnrate.getRateAsDouble(), 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("NFP (Jobs)", nfpChange, 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("YoY Inflation", yoyInflation, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Real GDP", latestGdp.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Retail Sales (MoM)", momRetailSales, 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Consumer Sentiment", latestSentiment.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("2Y Yield Momentum", latest2Y, ma2Y, 0, MetricCategory.CAPITAL_FLOWS),
                new MarketMetric("10Y Real Yield", realYield, 0.0, 0, MetricCategory.CAPITAL_FLOWS),
                new MarketMetric("2s10s Yield Curve", yieldCurve, 0.0, 0, MetricCategory.CAPITAL_FLOWS)
        );
    }

    private Observation getLatestObservation(String endpoint) throws Exception {
        String json = client.fetchRawJson(endpoint);
        AlphaResponse response = mapper.readValue(json, AlphaResponse.class);
        return response.data().stream()
                .filter(obs -> !obs.value().equals("."))
                .max(Comparator.comparing(Observation::date))
                .orElseThrow(() -> new RuntimeException("Data not found"));
    }

    private List<Observation> getSortedObservations(String endpoint) throws Exception {
        String json = client.fetchRawJson(endpoint);
        AlphaResponse response = mapper.readValue(json, AlphaResponse.class);
        return response.data().stream()
                .filter(obs -> !obs.value().equals("."))
                .sorted(Comparator.comparing(Observation::date).reversed())
                .toList();
    }
}