package com.uniforex.apexdata.service;

import com.uniforex.apexdata.CompositeScoringEngine;
import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.CalendarEventEntity;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.model.entity.MetricShiftEntity;
import com.uniforex.apexdata.repository.CalendarEventRepository;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import com.uniforex.apexdata.repository.MetricShiftRepository;
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
    private final MetricShiftRepository shiftRepo;
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
            MetricShiftRepository shiftRepo,
            DashboardStateService stateService,
            CftcService cftcService,
            TechnicalService technicalService,
            TreasuryYieldService yieldService,
            EconomicCalendarService calendarService,
            CompositeScoringEngine engine) {

        this.calendarRepo = calendarRepo;
        this.historyRepo = historyRepo;
        this.shiftRepo = shiftRepo;
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
            refreshMacroData();
            rebuildAndScoreState(false);
        } catch (Exception e) {
            System.err.println("[SYSTEM] Failed to prime state on startup: " + e.getMessage());
        }
    }

    // Runs once every day at 9:30 PM SAST, catching all US morning data + FOMC
    @Scheduled(cron = "0 30 21 * * ?", zone = "Africa/Johannesburg")
    public void executeCalendarCycle() {
        System.out.println("\n[SYSTEM] Executing Daily Calendar Poll...");
        try {
            List<MarketMetric> liveCalendarEvents = calendarService.fetchLiveCalendarEvents();
            System.out.println("[DEBUG] Calendar Events Found: " + liveCalendarEvents.size());

            if (!liveCalendarEvents.isEmpty()) {
                Map<String, CalendarEventEntity> existingMap = calendarRepo.findAll().stream()
                        .collect(Collectors.toMap(
                                CalendarEventEntity::getMetricName,
                                e -> e,
                                (existing, replacement) -> replacement
                        ));

                List<CalendarEventEntity> eventsToSave = new ArrayList<>();
                List<MetricShiftEntity> shiftsToSave = new ArrayList<>();

                for (MarketMetric incoming : liveCalendarEvents) {
                    CalendarEventEntity existing = existingMap.get(incoming.name());

                    if (existing == null) {
                        // Brand new metric
                        eventsToSave.add(new CalendarEventEntity(
                                incoming.name(), incoming.actualValue(), incoming.forecastValue(), incoming.category()
                        ));
                    } else {
                        boolean actualChanged = Double.compare(existing.getActualValue(), incoming.actualValue()) != 0;
                        boolean estimateChanged = Double.compare(existing.getEstimateValue(), incoming.forecastValue()) != 0;

                        if (actualChanged) {
                            shiftsToSave.add(new MetricShiftEntity(
                                    incoming.name(),
                                    incoming.category().name(),
                                    existing.getActualValue(),
                                    incoming.actualValue(),
                                    incoming.forecastValue()
                            ));
                        }

                        // Only overwrite in DB if a value actually changed, preserving accurate last_updated dates
                        if (actualChanged || estimateChanged) {
                            eventsToSave.add(new CalendarEventEntity(
                                    incoming.name(), incoming.actualValue(), incoming.forecastValue(), incoming.category()
                            ));
                        }
                    }
                }

                if (!eventsToSave.isEmpty()) {
                    calendarRepo.saveAll(eventsToSave);
                    System.out.println("[SYSTEM] Updated " + eventsToSave.size() + " calendar metrics.");
                }
                if (!shiftsToSave.isEmpty()) {
                    shiftRepo.saveAll(shiftsToSave);
                    System.out.println("[SYSTEM] Logged " + shiftsToSave.size() + " new macro shifts.");
                }
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
        // 1. Load historical calendar state safely (now preserving lastUpdated!)
        Map<String, MarketMetric> persistedState = calendarRepo.findAll().stream()
                .collect(Collectors.toMap(
                        CalendarEventEntity::getMetricName,
                        e -> new MarketMetric(
                                e.getMetricName(),
                                e.getActualValue(),
                                e.getEstimateValue(),
                                0,
                                e.getCategory(),
                                e.getLastUpdated()
                        ),
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

        // 7. Trigger Gold, Silver, and Equities Pipeline hooks
        stateService.updateGoldPipeline(this.cftcService, this.technicalService, this.engine);
        stateService.updateSilverPipeline(this.cftcService, this.technicalService, this.engine);
        stateService.updateNasdaqPipeline(this.cftcService, this.technicalService, this.engine);
        stateService.updateDowPipeline(this.cftcService, this.technicalService, this.engine);
        stateService.updateSp500Pipeline(this.cftcService, this.technicalService, this.engine);
        stateService.updateRussell2000Pipeline(this.cftcService, this.technicalService, this.engine);

        System.out.printf("[SYSTEM] Dashboard State Rebuilt. USD Score (%+d / %s).\n", totalScore, overallBias);
    }
}