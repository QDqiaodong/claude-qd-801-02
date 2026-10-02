package com.concrete.plant.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.CubeGroup;

public interface CubeGroupRepository extends JpaRepository<CubeGroup, Long> {

    Optional<CubeGroup> findByCubeNo(String cubeNo);

    boolean existsByCubeNo(String cubeNo);

    @Query("select g from CubeGroup g order by g.sampledAt desc, g.id desc")
    List<CubeGroup> findAllOrdered();

    @Query("select g from CubeGroup g where g.order.id = :orderId order by g.sampledAt desc, g.id desc")
    List<CubeGroup> findByOrderId(@Param("orderId") Long orderId);

    /** 录结果 / 提交复检时对试块组上写锁，保证终态串行推进 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CubeGroup g where g.id = :id")
    Optional<CubeGroup> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(g) from CubeGroup g where g.status = :status")
    long countByStatus(@Param("status") String status);
}
