package com.concrete.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.concrete.plant.domain.ReplenishRequest;

public interface ReplenishRequestRepository extends JpaRepository<ReplenishRequest, Long> {

    @Query("select r from ReplenishRequest r order by r.createdAt desc, r.id desc")
    List<ReplenishRequest> findAllOrdered();

    @Query("select count(r) from ReplenishRequest r where r.status = 'PENDING'")
    long countPending();
}
