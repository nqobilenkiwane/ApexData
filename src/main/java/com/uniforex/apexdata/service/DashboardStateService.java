package com.uniforex.apexdata.service;

import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository; // Make sure this matches your repo name
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DashboardStateService {

    private DashboardSummaryResponse latestSummary;

    @Autowired
    private HistoricalScoreRepository historicalScoreRepository;

    public DashboardSummaryResponse getLatestSummary() {
        return latestSummary;
    }

    public void setLatestSummary(DashboardSummaryResponse latestSummary) {
        this.latestSummary = latestSummary;
    }

    /**
     * EOD Snapshot: Runs at exactly 22:00 (10 PM) every day.
     * Timezone is explicitly set to South African Standard Time.
     */

    // Reverted back to the official 10:00 PM schedule
    @Scheduled(cron = "0 0 22 * * *", zone = "Africa/Johannesburg")
    public void captureDailySnapshot() {
        System.out.println("Executing EOD Snapshot at 10:00 PM SAST...");

        if (latestSummary == null) {
            System.out.println("EOD Snapshot aborted: No summary data was generated today.");
            return;
        }

        int currentScore = latestSummary.totalScore();
        String currentBias = latestSummary.overallBias();

        // 1. Create the entity using your custom constructor (Passing "USD" as the currency)
        HistoricalScoreEntity snapshot = new HistoricalScoreEntity("USD", currentScore, currentBias);

        // 2. Save it to PostgreSQL
        historicalScoreRepository.save(snapshot);

        System.out.println("EOD Snapshot saved successfully: " + currentScore + " (" + currentBias + ")");;
    }
}