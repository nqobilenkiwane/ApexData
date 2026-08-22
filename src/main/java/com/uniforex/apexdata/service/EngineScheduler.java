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
    private final DashboardStateService stateService; // Fixed: Made final

    private final FredService fredService;
    private final CftcService cftcService;
    private final TechnicalService technicalService;
    private final EconomicCalendarService calendarService;
    private final CompositeScoringEngine engine;

    // Fixed: Injected DashboardStateService into the constructor
    public EngineScheduler(
            CalendarEventRepository calendarRepo,
            HistoricalScoreRepository historyRepo,
            DashboardStateService stateService,
            @Value("${FRED_API_KEY}") String fredApiKey,
            @Value("${ALPHA_VANTAGE_API_KEY}") String alphaApiKey) {

        this.calendarRepo = calendarRepo;
        this.historyRepo = historyRepo;
        this.stateService = stateService;

        // Bridge your existing manual classes into the Spring context
        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        this.engine = new CompositeScoringEngine();

        this.fredService = new FredService(client, mapper, fredApiKey);
        this.cftcService = new CftcService(client, mapper);
        this.technicalService = new TechnicalService(client, mapper, alphaApiKey);
        this.calendarService = new EconomicCalendarService(client, mapper, null);
    }

    // Runs immediately on startup, then loops every 4 hours (14,400,000 milliseconds)
    @Scheduled(fixedRate = 14400000)
    public void executeMarketAnalysis() {
        System.out.println("\n[SYSTEM] Executing Automated Market Analysis Cycle...");
        try {
            // 1. Load historical state from PostgreSQL using Spring Data JPA
            Map<String, MarketMetric> persistedState = calendarRepo.findAll().stream()
                    .collect(Collectors.toMap(
                            CalendarEventEntity::getMetricName,
                            e -> new MarketMetric(e.getMetricName(), e.getActualValue(), e.getEstimateValue(), 0, e.getCategory())
                    ));

            // 2. Fetch live data
            List<MarketMetric> rawFredMetrics = fredService.fetchMacroData();
            List<MarketMetric> institutionalMetrics = cftcService.fetchInstitutionalData();
            TechnicalService.TechnicalData techData = technicalService.fetchUsdTechnicals();
            List<MarketMetric> liveCalendarEvents = calendarService.fetchLiveCalendarEvents();

            // 3. Persist new live calendar prints into PostgreSQL (The Upsert)
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
            int techScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());
            scoredMetrics.add(new MarketMetric("Technical Momentum", techData.currentPrice(), 0.0, techScore, MetricCategory.TECHNICALS));

            // Fixed: Consolidating calculations and avoiding duplication
            int totalScore = engine.calculateTotalScore(scoredMetrics);
            String overallBias = engine.getOverallBiasLabel(totalScore);
            Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);

            // 6. Save to the Historical Ledger (Table 2)
            historyRepo.save(new HistoricalScoreEntity("USD", totalScore, overallBias));

            // 7. Push the fully computed dashboard to the in-memory state service
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