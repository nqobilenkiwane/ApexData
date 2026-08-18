package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.FredResponse;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.Observation;

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

    public List<MarketMetric> fetchMacroData() throws Exception {
        String ratesEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=%s&file_type=json", apiKey);
        String cpiEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=CPIAUCSL&api_key=%s&file_type=json", apiKey);
        String unrateEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=UNRATE&api_key=%s&file_type=json", apiKey);
        String gdpEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=A191RL1Q225SBEA&api_key=%s&file_type=json", apiKey);
        String retailEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=RSAFS&api_key=%s&file_type=json", apiKey);
        String nfpEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=PAYEMS&api_key=%s&file_type=json", apiKey);
        String claimsEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=ICSA&api_key=%s&file_type=json", apiKey);
        String ppiEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=PPIFDG&api_key=%s&file_type=json", apiKey);
        String wagesEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=CES0500000003&api_key=%s&file_type=json", apiKey);
        String pceEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=PCEPILFE&api_key=%s&file_type=json", apiKey);
        String indproEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=INDPRO&api_key=%s&file_type=json", apiKey);
        String sentimentEndpoint = String.format("https://api.stlouisfed.org/fred/series/observations?series_id=UMCSENT&api_key=%s&file_type=json", apiKey);

        Observation latestRate = getLatestObservation(ratesEndpoint);
        List<Observation> sortedCpi = getSortedObservations(cpiEndpoint);
        double yoyInflation = ((sortedCpi.get(0).getRateAsDouble() - sortedCpi.get(12).getRateAsDouble()) / sortedCpi.get(12).getRateAsDouble()) * 100;

        Observation latestUnrate = getLatestObservation(unrateEndpoint);
        Observation latestGdp = getLatestObservation(gdpEndpoint);

        List<Observation> sortedRetail = getSortedObservations(retailEndpoint);
        double momRetailSales = ((sortedRetail.get(0).getRateAsDouble() - sortedRetail.get(1).getRateAsDouble()) / sortedRetail.get(1).getRateAsDouble()) * 100;

        List<Observation> sortedNfp = getSortedObservations(nfpEndpoint);
        double nfpChange = sortedNfp.get(0).getRateAsDouble() - sortedNfp.get(1).getRateAsDouble();

        Observation latestClaims = getLatestObservation(claimsEndpoint);

        List<Observation> sortedPpi = getSortedObservations(ppiEndpoint);
        double momPpi = ((sortedPpi.get(0).getRateAsDouble() - sortedPpi.get(1).getRateAsDouble()) / sortedPpi.get(1).getRateAsDouble()) * 100;

        List<Observation> sortedWages = getSortedObservations(wagesEndpoint);
        double momWages = ((sortedWages.get(0).getRateAsDouble() - sortedWages.get(1).getRateAsDouble()) / sortedWages.get(1).getRateAsDouble()) * 100;

        List<Observation> sortedPce = getSortedObservations(pceEndpoint);
        double momPce = ((sortedPce.get(0).getRateAsDouble() - sortedPce.get(1).getRateAsDouble()) / sortedPce.get(1).getRateAsDouble()) * 100;

        List<Observation> sortedIndpro = getSortedObservations(indproEndpoint);
        double momIndpro = ((sortedIndpro.get(0).getRateAsDouble() - sortedIndpro.get(1).getRateAsDouble()) / sortedIndpro.get(1).getRateAsDouble()) * 100;

        Observation latestSentiment = getLatestObservation(sentimentEndpoint);

        return Arrays.asList(
                new MarketMetric("Interest Rate", latestRate.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("YoY Inflation", yoyInflation, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Unemployment Rate", latestUnrate.getRateAsDouble(), 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("NFP (Jobs)", nfpChange, 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("Real GDP", latestGdp.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Retail Sales (MoM)", momRetailSales, 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Initial Jobless Claims", latestClaims.getRateAsDouble(), 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("PPI (MoM)", momPpi, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Wage Growth (MoM)", momWages, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Core PCE (MoM)", momPce, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Industrial Production", momIndpro, 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Consumer Sentiment", latestSentiment.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH)
        );
    }

    private Observation getLatestObservation(String endpoint) throws Exception {
        String json = client.fetchRawJson(endpoint);
        FredResponse response = mapper.readValue(json, FredResponse.class);
        return response.observations().stream()
                .max(Comparator.comparing(Observation::date))
                .orElseThrow(() -> new RuntimeException("Data not found"));
    }

    private List<Observation> getSortedObservations(String endpoint) throws Exception {
        String json = client.fetchRawJson(endpoint);
        FredResponse response = mapper.readValue(json, FredResponse.class);
        return response.observations().stream()
                .sorted(Comparator.comparing(Observation::date).reversed())
                .toList();
    }
}