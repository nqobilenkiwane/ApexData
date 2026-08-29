package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.CompositeScoringEngine;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.CalendarEventEntity;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.CalendarEventRepository;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EngineScheduler {

    private final CalendarEventRepository calendarRepo;
    private final HistoricalScoreRepository historyRepo;
    private final DashboardStateService stateService;

    private final FredService fredService;
    private final CftcService cftcService;
    private final TechnicalService technicalService;
    private final EconomicCalendarService calendarService;
    private final CompositeScoringEngine engine;

    public EngineScheduler(
            CalendarEventRepository calendarRepo,
            HistoricalScoreRepository historyRepo,
            DashboardStateService stateService,
            @Value("${FRED_API_KEY}") String fredApiKey,
            @Value("${ALPHA_VANTAGE_API_KEY}") String alphaApiKey) {

        this.calendarRepo = calendarRepo;
        this.historyRepo = historyRepo;
        this.stateService = stateService;

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        this.engine = new CompositeScoringEngine();

        // Change this line in EngineScheduler.java:
        this.fredService = new FredService(client, mapper, fredApiKey);
        this.cftcService = new CftcService(client, mapper);
        this.technicalService = new TechnicalService(client, mapper, alphaApiKey);
        this.calendarService = new EconomicCalendarService(client, mapper, null);
    }

    @Scheduled(fixedRate = 3600000)
    public void executeMarketAnalysis() {
        System.out.println("\n[SYSTEM] Executing Automated Market Analysis Cycle...");
        try {
            // 1. Load historical state
            Map<String, MarketMetric> persistedState = calendarRepo.findAll().stream()
                    .collect(Collectors.toMap(
                            CalendarEventEntity::getMetricName,
                            e -> new MarketMetric(e.getMetricName(), e.getActualValue(), e.getEstimateValue(), 0, e.getCategory())
                    ));

// 2. Fetch live data with trace logging
            System.out.println("[SYSTEM] Fetching FRED data...");
            List<MarketMetric> rawFredMetrics = new ArrayList<>();
            try {
                rawFredMetrics = fredService.fetchMacroData();
                Thread.sleep(1000);
            } catch (Exception e) { System.err.println("[API Error] FRED: " + e.getMessage()); }

            System.out.println("[SYSTEM] Fetching CFTC data...");
            List<MarketMetric> institutionalMetrics = new ArrayList<>();
            try {
                institutionalMetrics = cftcService.fetchInstitutionalData();
                Thread.sleep(1000);
            } catch (Exception e) { System.err.println("[API Error] CFTC: " + e.getMessage()); }

            System.out.println("[SYSTEM] Fetching Technicals...");
            TechnicalService.TechnicalData techData = new TechnicalService.TechnicalData(0.0, 0.0, 0.0);
            try {
                techData = technicalService.fetchUsdTechnicals();
                Thread.sleep(1000);
            } catch (Exception e) { System.err.println("[API Error] Technicals: " + e.getMessage()); }

            System.out.println("[SYSTEM] Fetching Economic Calendar...");
            List<MarketMetric> liveCalendarEvents = new ArrayList<>();
            try {
                liveCalendarEvents = calendarService.fetchLiveCalendarEvents();
            } catch (Exception e) { System.err.println("[API Error] Calendar: " + e.getMessage()); }

            // 3. Upsert live calendar prints
            for (MarketMetric event : liveCalendarEvents) {
                calendarRepo.save(new CalendarEventEntity(event.name(), event.actualValue(), event.forecastValue(), event.category()));
                persistedState.put(event.name(), event);
            }

            // 4. Merge FRED spot actuals with persisted estimates
            List<MarketMetric> combinedMetrics = new ArrayList<>();
            List<String> processedNames = new ArrayList<>();

            for (MarketMetric fred : rawFredMetrics) {
                double estimate = persistedState.containsKey(fred.name()) ? persistedState.get(fred.name()).forecastValue() : fred.forecastValue();
                combinedMetrics.add(new MarketMetric(fred.name(), fred.actualValue(), estimate, 0, fred.category()));
                processedNames.add(fred.name());
            }

            for (MarketMetric persisted : persistedState.values()) {
                if (!processedNames.contains(persisted.name())) {
                    combinedMetrics.add(persisted);
                    processedNames.add(persisted.name());
                }
            }

            combinedMetrics.addAll(institutionalMetrics);

            // 5. Score engine pass
            List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(combinedMetrics));

            // Only score technicals if the API call was successful
            if (techData.currentPrice() > 0) {
                int techScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());
                scoredMetrics.add(new MarketMetric("Technical Momentum", techData.currentPrice(), 0.0, techScore, MetricCategory.TECHNICALS));
            }

            int totalScore = engine.calculateTotalScore(scoredMetrics);
            String overallBias = engine.getOverallBiasLabel(totalScore);
            Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);

            // 6. Save to Ledger
            historyRepo.save(new HistoricalScoreEntity("USD", totalScore, overallBias));

            // 7. Update Dashboard State
            DashboardSummaryResponse summary = new DashboardSummaryResponse(
                    totalScore, overallBias, categoryScores, scoredMetrics
            );
            stateService.setLatestSummary(summary);

            System.out.printf("[SYSTEM] Cycle Complete. USD Score (%+d / %s) saved to ledger.\n", totalScore, overallBias);

        } catch (Exception e) {
            System.err.println("[SYSTEM] Scheduled execution failed: " + e.getMessage());
        }
    }
}