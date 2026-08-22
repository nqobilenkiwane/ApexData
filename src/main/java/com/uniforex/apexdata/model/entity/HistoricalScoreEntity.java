package com.uniforex.apexdata.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historical_scores")
public class HistoricalScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Auto-incrementing primary key

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "bias_label", length = 50)
    private String biasLabel;

    public HistoricalScoreEntity() {}

    public HistoricalScoreEntity(String currency, Integer totalScore, String biasLabel) {
        this.timestamp = LocalDateTime.now();
        this.currency = currency;
        this.totalScore = totalScore;
        this.biasLabel = biasLabel;
    }

    // Getters
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getCurrency() { return currency; }
    public Integer getTotalScore() { return totalScore; }
    public String getBiasLabel() { return biasLabel; }
}