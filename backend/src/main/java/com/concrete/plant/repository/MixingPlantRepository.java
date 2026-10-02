package com.concrete.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.concrete.plant.domain.MixingPlant;

public interface MixingPlantRepository extends JpaRepository<MixingPlant, Long> {

    @Query("select p from MixingPlant p order by p.plantCode")
    List<MixingPlant> findAllOrdered();
}
