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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EngineScheduler {

    private final CalendarEventRepository calendarRepo;
    private final HistoricalScoreRepository historyRepo;
    private final DashboardStateService stateService;

    private final CftcService cftcService;
    private final TechnicalService technicalService;
    private final EconomicCalendarService calendarService;
    private final CompositeScoringEngine engine;

    // Cache for slow-moving macro data so the fast calendar cycle can reuse it
    private List<MarketMetric> cachedInstitutionalMetrics = new ArrayList<>();
    private TechnicalService.TechnicalData cachedTechData = new TechnicalService.TechnicalData(0.86, 0.859, 50.0, 4.34, 4.75); // Fallback init

    public EngineScheduler(
            CalendarEventRepository calendarRepo,
            HistoricalScoreRepository historyRepo,
            DashboardStateService stateService,
            @Value("${ALPHA_VANTAGE_API_KEY}") String alphaApiKey) {

        this.calendarRepo = calendarRepo;
        this.historyRepo = historyRepo;
        this.stateService = stateService;

        MarketDataClient client = new MarketDataClient();
        ObjectMapper mapper = new ObjectMapper();
        this.engine = new CompositeScoringEngine();

        this.cftcService = new CftcService(client, mapper);
        this.technicalService = new TechnicalService(client, mapper, alphaApiKey);
        this.calendarService = new EconomicCalendarService(client, mapper, null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void primeStateOnStartup() {
        System.out.println("\n[SYSTEM] Application Started. Priming dashboard state from database...");
        try {
            // Immediately load the existing DB data and generate a score so the UI has something to render
            rebuildAndScoreState();
        } catch (Exception e) {
            System.err.println("[SYSTEM] Failed to prime state on startup: " + e.getMessage());
        }
    }

    // 1. FAST CYCLE: Runs at the top of every hour to catch live news drops
    @Scheduled(cron = "0 */15 * * * *")
    public void executeCalendarCycle() {
        System.out.println("\n[SYSTEM] Executing Hourly Calendar Update...");

        System.out.println("[SYSTEM] Fetching CFTC data...");
        try {
            cachedInstitutionalMetrics = cftcService.fetchInstitutionalData();
            System.out.println("[DEBUG] CFTC Metrics Found: " + cachedInstitutionalMetrics.size());
        } catch (Exception e) {
            System.err.println("[API Error] CFTC: " + e.getMessage());
        }

        System.out.println("[SYSTEM] Fetching Calendar Events data...");
        try {
            List<MarketMetric> liveCalendarEvents = calendarService.fetchLiveCalendarEvents();
            System.out.println("[DEBUG] Calendar Events Found: " + liveCalendarEvents.size());

            for (MarketMetric event : liveCalendarEvents) {
                calendarRepo.save(new CalendarEventEntity(event.name(), event.actualValue(), event.forecastValue(), event.category()));
            }

            // Rebuild the dashboard state using new calendar data + cached macro data
            rebuildAndScoreState();

        } catch (Exception e) {
            System.err.println("[API Error] Calendar: " + e.getMessage());
        }
    }

    // 2. SLOW CYCLE: Runs at 00:00 and 12:00 everyday to respect API limits
    @Scheduled(cron = "0 0 0,12 * * *")
    public void executeMacroCycle() {
        System.out.println("\n[SYSTEM] Executing 12-Hour Macro & Technicals...");
        try {
//            System.out.println("[SYSTEM] Fetching CFTC data...");
//            try {
//                cachedInstitutionalMetrics = cftcService.fetchInstitutionalData();
//                System.out.println("[DEBUG] CFTC Metrics Found: " + cachedInstitutionalMetrics.size());
//            } catch (Exception e) {
//                System.err.println("[API Error] CFTC: " + e.getMessage());
//            }

            // 12-second buffer to guarantee we do not trip the 5 req/min rate limit
            Thread.sleep(12000);

            System.out.println("[SYSTEM] Fetching Technicals...");
            try {
                cachedTechData = technicalService.fetchUsdTechnicals();
            } catch (Exception e) {
                System.err.println("[API Error] Technicals: " + e.getMessage());
                System.out.println("[SYSTEM] Rate limit hit. Retaining previously cached Technical Data.");
            }

            // Rebuild the dashboard state using cached calendar data + new macro data
            rebuildAndScoreState();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[SYSTEM] Rate limit buffer interrupted.");
        } catch (Exception e) {
            System.err.println("[SYSTEM] Macro cycle failed: " + e.getMessage());
        }
    }

    // Centralized state builder that merges fast and slow data streams
    private void rebuildAndScoreState() {
        // 1. Load historical calendar state from Postgres
        Map<String, MarketMetric> persistedState = calendarRepo.findAll().stream()
                .collect(Collectors.toMap(
                        CalendarEventEntity::getMetricName,
                        e -> new MarketMetric(e.getMetricName(), e.getActualValue(), e.getEstimateValue(), 0, e.getCategory())
                ));

        // 2. Combine Calendar + CFTC
        List<MarketMetric> combinedMetrics = new ArrayList<>(persistedState.values());
        combinedMetrics.addAll(cachedInstitutionalMetrics);

        // 3. Base Score Engine Pass
        List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(combinedMetrics));

        // 4. Inject and Score Technicals & Yields
        if (cachedTechData.currentPrice() > 0) {
            int techScore = engine.scoreTechnicals(cachedTechData.currentPrice(), cachedTechData.sma200(), cachedTechData.rsi14());
            scoredMetrics.add(new MarketMetric("Technical Momentum", cachedTechData.currentPrice(), 0.0, techScore, MetricCategory.TECHNICALS));

            int score10Y = engine.scoreAbsoluteYield(cachedTechData.yield10Y(), 4.00);
            int score2Y = engine.scoreAbsoluteYield(cachedTechData.yield2Y(), 4.50);

            scoredMetrics.add(new MarketMetric("2Y Yield Momentum", cachedTechData.yield2Y(), 0.0, score2Y, MetricCategory.CAPITAL_FLOWS));
            scoredMetrics.add(new MarketMetric("10Y Real Yield", cachedTechData.yield10Y(), 0.0, score10Y, MetricCategory.CAPITAL_FLOWS));

            double yieldCurve = cachedTechData.yield10Y() - cachedTechData.yield2Y();
            int curveScore = engine.scoreYieldCurve(yieldCurve);

            scoredMetrics.add(new MarketMetric("2s10s Yield Curve", yieldCurve, 0.0, curveScore, MetricCategory.CAPITAL_FLOWS));
        }

        int totalScore = engine.calculateTotalScore(scoredMetrics);
        String overallBias = engine.getOverallBiasLabel(totalScore);
        Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);

        // 5. Save to Ledger
        historyRepo.save(new HistoricalScoreEntity("USD", totalScore, overallBias));

        // 6. Update Dashboard UI Payload
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                totalScore, overallBias, categoryScores, scoredMetrics
        );
        stateService.setLatestSummary(summary);

        System.out.printf("[SYSTEM] Dashboard State Rebuilt. USD Score (%+d / %s) saved to ledger.\n", totalScore, overallBias);
    }
}