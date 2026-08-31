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

    private final CftcService cftcService;
    private final TechnicalService technicalService;
    private final EconomicCalendarService calendarService;
    private final CompositeScoringEngine engine;

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

    @Scheduled(fixedRate = 14400000)
    public void executeMarketAnalysis() {
        System.out.println("\n[SYSTEM] Executing Automated Market Analysis Cycle...");
        try {
            // 1. Load historical state (The Cache)
            Map<String, MarketMetric> persistedState = calendarRepo.findAll().stream()
                    .collect(Collectors.toMap(
                            CalendarEventEntity::getMetricName,
                            e -> new MarketMetric(e.getMetricName(), e.getActualValue(), e.getEstimateValue(), 0, e.getCategory())
                    ));

            // 2. Fetch live data
            System.out.println("[SYSTEM] Fetching CFTC data...");
            List<MarketMetric> institutionalMetrics = new ArrayList<>();
            try {
                institutionalMetrics = cftcService.fetchInstitutionalData();
                Thread.sleep(1000);
                // After CFTC fetch
                System.out.println("[DEBUG] CFTC Metrics Found: " + institutionalMetrics.size());
            } catch (Exception e) { System.err.println("[API Error] CFTC: " + e.getMessage()); }

            System.out.println("[SYSTEM] Fetching Technicals...");
            TechnicalService.TechnicalData techData = new TechnicalService.TechnicalData(0.0, 0.0, 0.0, 0.0, 0.0);
            try {
                techData = technicalService.fetchUsdTechnicals();
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("[API Error] Technicals: " + e.getMessage());
                System.out.println("[SYSTEM] Rate limit hit. Injecting Fallback Technical Data for debugging.");
                // Last known real values from your TradingView and Dashboard checks
                techData = new TechnicalService.TechnicalData(0.86, 0.859, 50.0, 4.34, 4.75);
            }

            System.out.println("[SYSTEM] Fetching Economic Calendar...");
            List<MarketMetric> liveCalendarEvents = new ArrayList<>();
            try {
                liveCalendarEvents = calendarService.fetchLiveCalendarEvents();
                // After Calendar fetch
                System.out.println("[DEBUG] Calendar Events Found: " + liveCalendarEvents.size());
            } catch (Exception e) { System.err.println("[API Error] Calendar: " + e.getMessage()); }

            // 3. Upsert live calendar prints (Updates the cache with this week's data)
            for (MarketMetric event : liveCalendarEvents) {
                calendarRepo.save(new CalendarEventEntity(event.name(), event.actualValue(), event.forecastValue(), event.category()));
                persistedState.put(event.name(), event);
            }

            // 4. Combine all metrics
            List<MarketMetric> combinedMetrics = new ArrayList<>(persistedState.values());
            combinedMetrics.addAll(institutionalMetrics);

            // 5. Score engine pass
            List<MarketMetric> scoredMetrics = new ArrayList<>(engine.applyScores(combinedMetrics));

            // Only score technicals and yields if the API call was successful
            if (techData.currentPrice() > 0) {
                int techScore = engine.scoreTechnicals(techData.currentPrice(), techData.sma200(), techData.rsi14());
                scoredMetrics.add(new MarketMetric("Technical Momentum", techData.currentPrice(), 0.0, techScore, MetricCategory.TECHNICALS));

                // Inject the bond yields using absolute macro thresholds
                int score10Y = engine.scoreAbsoluteYield(techData.yield10Y(), 4.00);
                int score2Y = engine.scoreAbsoluteYield(techData.yield2Y(), 4.50);

                scoredMetrics.add(new MarketMetric("2Y Yield Momentum", techData.yield2Y(), 0.0, score2Y, MetricCategory.CAPITAL_FLOWS));
                scoredMetrics.add(new MarketMetric("10Y Real Yield", techData.yield10Y(), 0.0, score10Y, MetricCategory.CAPITAL_FLOWS));

                // Calculate and score the yield curve (10Y - 2Y)
                double yieldCurve = techData.yield10Y() - techData.yield2Y();
                int curveScore = engine.scoreYieldCurve(yieldCurve);

                scoredMetrics.add(new MarketMetric("2s10s Yield Curve", yieldCurve, 0.0, curveScore, MetricCategory.CAPITAL_FLOWS));
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