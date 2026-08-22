package com.uniforex.apexdata.model.entity;

import com.uniforex.apexdata.model.MetricCategory;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_events")
public class CalendarEventEntity {

    @Id
    @Column(name = "metric_name", length = 100)
    private String metricName;

    @Column(name = "actual_value")
    private Double actualValue;

    @Column(name = "estimate_value")
    private Double estimateValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private MetricCategory category;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    // Required no-args constructor for JPA
    public CalendarEventEntity() {}

    public CalendarEventEntity(String metricName, Double actualValue, Double estimateValue, MetricCategory category) {
        this.metricName = metricName;
        this.actualValue = actualValue;
        this.estimateValue = estimateValue;
        this.category = category;
    }

    // Run this automatically before saving or updating the row
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    // Getters
    public String getMetricName() { return metricName; }
    public Double getActualValue() { return actualValue; }
    public Double getEstimateValue() { return estimateValue; }
    public MetricCategory getCategory() { return category; }
}