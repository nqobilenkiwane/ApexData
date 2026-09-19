package com.uniforex.apexdata.service;

import com.uniforex.apexdata.CompositeScoringEngine;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.CalendarEventEntity;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.CalendarEventRepository;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
    private final TreasuryYieldService yieldService;
    private final EconomicCalendarService calendarService;
    private final CompositeScoringEngine engine;

    // Thread-safe caches for macro pillars
    private List<MarketMetric> cachedInstitutionalMetrics = Collections.synchronizedList(new ArrayList<>());
    private List<MarketMetric> cachedYieldMetrics = Collections.synchronizedList(new ArrayList<>());
    private volatile TechnicalService.AssetTechnicalData cachedTechData = new TechnicalService.AssetTechnicalData(100.00, 0.920, 50.0);

    public EngineScheduler(
            CalendarEventRepository calendarRepo,
            HistoricalScoreRepository historyRepo,
            DashboardStateService stateService,
            CftcService cftcService,
            TechnicalService technicalService,
            TreasuryYieldService yieldService,
            EconomicCalendarService calendarService,
            CompositeScoringEngine engine) {

        this.calendarRepo = calendarRepo;
        this.historyRepo = historyRepo;
        this.stateService = stateService;
        this.cftcService = cftcService;
        this.technicalService = technicalService;
        this.yieldService = yieldService;
        this.calendarService = calendarService;
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void primeStateOnStartup() {
        System.out.println("\n[SYSTEM] Application Started. Priming dashboard state from database...");
        try {
            // Initial one-time fetch to populate static caches
            refreshMacroData();
            rebuildAndScoreState(false);
        } catch (Exception e) {
            System.err.println("[SYSTEM] Failed to prime state on startup: " + e.getMessage());
        }
    }

    // Runs once every day at 9:30 PM SAST, catching all US morning data + FOMC
    @Scheduled(cron = "0 30 21 * * ?", zone = "Africa/Johannesburg")
    public void executeCalendarCycle() {
        System.out.println("\n[SYSTEM] Executing 15-Minute Calendar Poll...");
        try {
            List<MarketMetric> liveCalendarEvents = calendarService.fetchLiveCalendarEvents();
            System.out.println("[DEBUG] Calendar Events Found: " + liveCalendarEvents.size());

            if (!liveCalendarEvents.isEmpty()) {
                List<CalendarEventEntity> entities = liveCalendarEvents.stream()
                        .map(e -> new CalendarEventEntity(e.name(), e.actualValue(), e.forecastValue(), e.category()))
                        .toList();
                calendarRepo.saveAll(entities);
            }

            rebuildAndScoreState(false);

        } catch (Exception e) {
            System.err.println("[API Error] Calendar: " + e.getMessage());
        }
    }

    // 2. SLOW CYCLE: Runs twice a day for Yields, CFTC, and Daily Candle closes
    @Scheduled(cron = "0 0 0,12 * * *")
    public void executeMacroCycle() {
        System.out.println("\n[SYSTEM] Executing Macro, Yields & Technical Cycle...");
        refreshMacroData();
        // Record snapshot to history ledger at daily/macro boundaries
        rebuildAndScoreState(true);
    }

    private void refreshMacroData() {
        try {
            cachedInstitutionalMetrics = cftcService.fetchInstitutionalData();
        } catch (Exception e) {
            System.err.println("[API Error] CFTC: " + e.getMessage());
        }

        try {
            cachedYieldMetrics = yieldService.fetchLatestYields();
        } catch (Exception e) {
            System.err.println("[API Error] Treasury Yields: " + e.getMessage());
        }

        try {
            cachedTechData = technicalService.fetchUsdTechnicals();
        } catch (Exception e) {
            System.err.println("[API Error] Technicals: " + e.getMessage());
        }
    }

    private synchronized void rebuildAndScoreState(boolean recordHistoricalSnapshot) {
        // 1. Load historical calendar state safely (resolving duplicate key collisions)
        Map<String, MarketMetric> persistedState = calendarRepo.findAll().stream()
                .collect(Collectors.toMap(
                        CalendarEventEntity::getMetricName,
                        e -> new MarketMetric(e.getMetricName(), e.getActualValue(), e.getEstimateValue(), 0, e.getCategory()),
                        (existing, replacement) -> replacement
                ));

        // 2. Combine Calendar + CFTC + Yields
        List<MarketMetric> combinedMetrics = new ArrayList<>(persistedState.values());
        combinedMetrics.addAll(cachedInstitutionalMetrics);
        combinedMetrics.addAll(cachedYieldMetrics);

        // 3. Base Score Engine Pass (Scores Calendar, CFTC, and Yields)
        List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(combinedMetrics));

        // 4. Score Technical Momentum
        if (cachedTechData != null && cachedTechData.currentPrice() > 0) {
            int techScore = engine.scoreTechnicals(
                    cachedTechData.currentPrice(),
                    cachedTechData.sma200(),
                    cachedTechData.rsi14()
            );
            scoredMetrics.add(new MarketMetric(
                    "Technical Momentum",
                    cachedTechData.currentPrice(),
                    0.0,
                    techScore,
                    MetricCategory.TECHNICALS
            ));
        }

        int totalScore = engine.calculateTotalScore(scoredMetrics);
        String overallBias = engine.getOverallBiasLabel(totalScore);
        Map<MetricCategory, Integer> categoryScores = engine.calculateCategoryScores(scoredMetrics);

        // 5. Conditional Ledger Write (prevents table bloat)
        if (recordHistoricalSnapshot) {
            historyRepo.save(new HistoricalScoreEntity("USD", totalScore, overallBias));
        }

        // 6. Update Dashboard UI Payload
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                totalScore, overallBias, categoryScores, scoredMetrics
        );
        stateService.setLatestSummary(summary);

        // 7. Trigger Gold Pipeline hook
        stateService.updateGoldPipeline(this.cftcService, this.technicalService, this.engine);

        System.out.printf("[SYSTEM] Dashboard State Rebuilt. USD Score (%+d / %s).\n", totalScore, overallBias);
    }
}