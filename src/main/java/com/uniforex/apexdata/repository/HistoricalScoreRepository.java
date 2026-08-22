package com.uniforex.apexdata.repository;

import com.uniforex.apexdata.model.entity.HistoricalScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoricalScoreRepository extends JpaRepository<HistoricalScoreEntity, Long> {
}