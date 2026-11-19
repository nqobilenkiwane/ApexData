package com.uniforex.apexdata.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "metric_shifts")
public class MetricShiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "old_actual")
    private double oldActual;

    @Column(name = "new_actual")
    private double newActual;

    @Column(name = "estimate_value")
    private double estimateValue;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public MetricShiftEntity() {}

    public MetricShiftEntity(String metricName, String category, double oldActual, double newActual, double estimateValue) {
        this.metricName = metricName;
        this.category = category;
        this.oldActual = oldActual;
        this.newActual = newActual;
        this.estimateValue = estimateValue;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMetricName() { return metricName; }
    public String getCategory() { return category; }
    public double getOldActual() { return oldActual; }
    public double getNewActual() { return newActual; }
    public double getEstimateValue() { return estimateValue; }
    public LocalDateTime getChangedAt() { return changedAt; }
}