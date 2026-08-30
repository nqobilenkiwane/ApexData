package com.uniforex.apexdata.controller;

import com.uniforex.apexdata.model.dto.DashboardSummaryResponse;
import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import com.uniforex.apexdata.repository.HistoricalScoreRepository;
import com.uniforex.apexdata.service.DashboardStateService;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> getDashboardSummary() {
        try {
            DashboardSummaryResponse summary = stateService.getLatestSummary();
            if (summary == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Engine has not completed first cycle yet.");
            }
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            System.err.println("[CRITICAL ERROR] Failed to fetch summary:");
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistoricalScores() {
        try {
            return ResponseEntity.ok(historyRepo.findAll());
        } catch (Exception e) {
            System.err.println("[CRITICAL ERROR] Failed to fetch history:");
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}