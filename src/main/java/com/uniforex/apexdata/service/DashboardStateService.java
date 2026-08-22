package com.uniforex.apexdata.service;

import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class DashboardStateService {

    private DashboardSummaryResponse latestSummary;

    public DashboardSummaryResponse getLatestSummary() {
        return latestSummary;
    }

    public void setLatestSummary(DashboardSummaryResponse latestSummary) {
        this.latestSummary = latestSummary;
    }
}