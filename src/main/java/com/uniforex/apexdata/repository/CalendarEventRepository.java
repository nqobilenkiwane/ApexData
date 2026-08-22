package com.uniforex.apexdata.repository;

import com.uniforex.apexdata.model.entity.CalendarEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEventEntity, String> {
    // By extending JpaRepository, Spring automatically gives you built-in
    // methods like .save(), .findAll(), and .findById() for free!
}