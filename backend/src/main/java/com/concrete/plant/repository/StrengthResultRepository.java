package com.concrete.plant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.concrete.plant.domain.StrengthResult;

public interface StrengthResultRepository extends JpaRepository<StrengthResult, Long> {

    boolean existsByItemId(Long itemId);
}
