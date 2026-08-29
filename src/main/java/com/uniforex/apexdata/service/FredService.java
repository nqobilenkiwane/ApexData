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
        // --- EXISTING MACRO ENDPOINTS ---
        String cpiEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=CPIAUCSL&api_key=%s&file_type=json", apiKey);
        String unrateEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=UNRATE&api_key=%s&file_type=json", apiKey);
        String gdpEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=A191RL1Q225SBEA&api_key=%s&file_type=json", apiKey);
        String retailEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=RSAFS&api_key=%s&file_type=json", apiKey);
        String nfpEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=PAYEMS&api_key=%s&file_type=json", apiKey);
        String claimsEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=ICSA&api_key=%s&file_type=json", apiKey);
        String ppiEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=PPIFDG&api_key=%s&file_type=json", apiKey);
        String wagesEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=CES0500000003&api_key=%s&file_type=json", apiKey);
        String pceEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=PCEPILFE&api_key=%s&file_type=json", apiKey);
        String indproEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=INDPRO&api_key=%s&file_type=json", apiKey);
        String sentimentEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=UMCSENT&api_key=%s&file_type=json", apiKey);
        String joltsEndpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=JTSJOL&api_key=%s&file_type=json", apiKey);

        // --- NEW: BOND MARKET ENDPOINTS ---
        String dgs2Endpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=DGS2&api_key=%s&file_type=json", apiKey);
        String dgs10Endpoint = String.format("https://fred-proxy.nqobilenkiwane01.workers.dev/fred/series/observations?series_id=DGS10&api_key=%s&file_type=json", apiKey);

        // Fetch & Parse Base Macro Data
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
        Observation latestJolts = getLatestObservation(joltsEndpoint);
        double joltsMillions = latestJolts.getRateAsDouble() / 1000.0;

        // --- FETCH & CALCULATE BOND MARKET METRICS ---
        List<Observation> sorted2Y = getSortedObservations(dgs2Endpoint);
        List<Observation> sorted10Y = getSortedObservations(dgs10Endpoint);

        double latest2Y = sorted2Y.get(0).getRateAsDouble();
        double latest10Y = sorted10Y.get(0).getRateAsDouble();

        // Calculate 2Y Moving Average (approx 22 trading days in a month)
        double sum2Y = 0;
        int limit = Math.min(22, sorted2Y.size());
        for (int i = 0; i < limit; i++) {
            sum2Y += sorted2Y.get(i).getRateAsDouble();
        }
        double ma2Y = sum2Y / limit;

        // Calculate Yield Derivatives
        double realYield = latest10Y - yoyInflation;
        double yieldCurve = latest10Y - latest2Y;

        return Arrays.asList(
                // JOB MARKET
                new MarketMetric("Unemployment Rate", latestUnrate.getRateAsDouble(), 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("NFP (Jobs)", nfpChange, 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("Initial Jobless Claims", latestClaims.getRateAsDouble(), 0.0, 0, MetricCategory.JOB_MARKET),
                new MarketMetric("JOLTS Job Openings", joltsMillions, 0.0, 0, MetricCategory.JOB_MARKET),

                // INFLATION
                new MarketMetric("YoY Inflation", yoyInflation, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("PPI (MoM)", momPpi, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Wage Growth (MoM)", momWages, 0.0, 0, MetricCategory.INFLATION),
                new MarketMetric("Core PCE (MoM)", momPce, 0.0, 0, MetricCategory.INFLATION),

                // ECONOMIC GROWTH
                new MarketMetric("Real GDP", latestGdp.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Retail Sales (MoM)", momRetailSales, 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Industrial Production", momIndpro, 0.0, 0, MetricCategory.ECONOMIC_GROWTH),
                new MarketMetric("Consumer Sentiment", latestSentiment.getRateAsDouble(), 0.0, 0, MetricCategory.ECONOMIC_GROWTH),

                // CAPITAL FLOWS (New Bond Market Engine)
                // We pass the MA into the "forecast" field so the Surprise Factor scores momentum automatically!
                new MarketMetric("2Y Yield Momentum", latest2Y, ma2Y, 0, MetricCategory.CAPITAL_FLOWS),
                new MarketMetric("10Y Real Yield", realYield, 0.0, 0, MetricCategory.CAPITAL_FLOWS),
                new MarketMetric("2s10s Yield Curve", yieldCurve, 0.0, 0, MetricCategory.CAPITAL_FLOWS)
        );
    }

    private Observation getLatestObservation(String endpoint) throws Exception {
        String json = client.fetchRawJson(endpoint);
        FredResponse response = mapper.readValue(json, FredResponse.class);
        return response.observations().stream()
                .filter(obs -> !obs.value().equals(".")) // Filter out bank holidays
                .max(Comparator.comparing(Observation::date))
                .orElseThrow(() -> new RuntimeException("Data not found"));
    }

    private List<Observation> getSortedObservations(String endpoint) throws Exception {
        String json = client.fetchRawJson(endpoint);
        FredResponse response = mapper.readValue(json, FredResponse.class);
        return response.observations().stream()
                .filter(obs -> !obs.value().equals(".")) // Filter out bank holidays
                .sorted(Comparator.comparing(Observation::date).reversed())
                .toList();
    }
}