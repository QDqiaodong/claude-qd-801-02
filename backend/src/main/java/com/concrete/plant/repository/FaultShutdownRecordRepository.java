package com.concrete.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.concrete.plant.domain.FaultShutdownRecord;

public interface FaultShutdownRecordRepository extends JpaRepository<FaultShutdownRecord, Long> {

    @Query("select r from FaultShutdownRecord r order by r.confirmedAt desc, r.id desc")
    List<FaultShutdownRecord> findAllOrdered();
}
