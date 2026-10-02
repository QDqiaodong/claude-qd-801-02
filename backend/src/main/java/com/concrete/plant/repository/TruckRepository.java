package com.concrete.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.concrete.plant.domain.Truck;

public interface TruckRepository extends JpaRepository<Truck, Long> {

    @Query("select t from Truck t order by t.queueSeq, t.id")
    List<Truck> findAllOrdered();

    @Query("select t from Truck t where t.status = 'STANDBY' order by t.queueSeq, t.id")
    List<Truck> findStandby();
}
