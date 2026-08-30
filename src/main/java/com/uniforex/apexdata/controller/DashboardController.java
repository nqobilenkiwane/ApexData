package com.uniforex.apexdata.controller;

import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import com.uniforex.apexdata.service.DashboardStateService;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        DashboardSummaryResponse summary = stateService.getLatestSummary();
        if (summary == null) {
            // Returns a 503 Service Unavailable instead of crashing, giving the engine time to run
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/history")
    public ResponseEntity<List<HistoricalScoreEntity>> getHistoricalScores() {
        return ResponseEntity.ok(historyRepo.findAll());
    }
}