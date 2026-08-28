package com.uniforex.apexdata.controller;

import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import com.uniforex.apexdata.service.DashboardStateService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final HistoricalScoreRepository historyRepo;
    private final DashboardStateService stateService;

    public DashboardController(HistoricalScoreRepository historyRepo, DashboardStateService stateService) {
        this.historyRepo = historyRepo;
        this.stateService = stateService;
    }

    // Returns the fully calculated dashboard, ready for React to render
    @GetMapping("/summary")
    public DashboardSummaryResponse getDashboardSummary() {
        return stateService.getLatestSummary();
    }

    // Returns the historical ledger for the React line chart
    @GetMapping("/history")
    public List<HistoricalScoreEntity> getHistoricalScores() {
        return historyRepo.findAll();
    }
}