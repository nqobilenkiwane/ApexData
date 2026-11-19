package com.uniforex.apexdata.repository;

import com.uniforex.apexdata.model.entity.MetricShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MetricShiftRepository extends JpaRepository<MetricShiftEntity, Long> {
    List<MetricShiftEntity> findTop20ByOrderByChangedAtDesc();
}