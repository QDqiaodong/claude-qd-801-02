package com.concrete.plant.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.ReplenishRequest;

public interface ReplenishRequestRepository extends JpaRepository<ReplenishRequest, Long> {

    @Query("select r from ReplenishRequest r order by r.createdAt desc, r.id desc")
    List<ReplenishRequest> findAllOrdered();

    @Query("select count(r) from ReplenishRequest r where r.status = 'PENDING'")
    long countPending();

    /** 按筒仓汇总待审批占用量，用于列表一次性计算可申请余量。 */
    @Query("select r.silo.id as siloId, coalesce(sum(r.quantityTon), 0) as pendingTon "
            + "from ReplenishRequest r where r.status = 'PENDING' group by r.silo.id")
    List<PendingOccupancy> sumPendingBySilo();

    @Query("select coalesce(sum(r.quantityTon), 0) from ReplenishRequest r "
            + "where r.silo.id = :siloId and r.status = 'PENDING'")
    BigDecimal sumPendingBySiloId(@Param("siloId") Long siloId);

    @Query("select coalesce(sum(r.quantityTon), 0) from ReplenishRequest r "
            + "where r.silo.id = :siloId and r.status = 'PENDING' and r.id <> :requestId")
    BigDecimal sumPendingBySiloIdExcluding(@Param("siloId") Long siloId, @Param("requestId") Long requestId);

    @Query("select r.silo.id from ReplenishRequest r where r.id = :id")
    Optional<Long> findSiloIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReplenishRequest r where r.id = :id")
    Optional<ReplenishRequest> findByIdForUpdate(@Param("id") Long id);

    interface PendingOccupancy {
        Long getSiloId();

        BigDecimal getPendingTon();
    }
}
