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

    /** 审批 / 驳回时对申请上写锁，与筒仓行锁一起保证占用口径串行推进 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReplenishRequest r where r.id = :id")
    Optional<ReplenishRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * 某筒仓全部待审批申请的补料量合计（占用仓容），只读列表口径。
     *
     * @param excludeId 审批重新核对时排除本单自身；发起新申请传 null
     */
    @Query("select coalesce(sum(r.quantityTon), 0) from ReplenishRequest r "
            + "where r.status = 'PENDING' and r.silo.id = :siloId "
            + "and (:excludeId is null or r.id <> :excludeId)")
    BigDecimal sumPendingQuantity(@Param("siloId") Long siloId, @Param("excludeId") Long excludeId);

    /**
     * 加锁读取某筒仓全部待审批申请：写事务持有筒仓行锁后调用，
     * 强制占用量按最新提交版本计算，并串行化同仓的申请/审批/驳回。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReplenishRequest r where r.status = 'PENDING' and r.silo.id = :siloId")
    List<ReplenishRequest> findPendingBySiloForUpdate(@Param("siloId") Long siloId);

    /** 只取申请所属筒仓 id，供审批/驳回先锁筒仓、再锁申请（统一加锁顺序避免死锁） */
    @Query("select r.silo.id from ReplenishRequest r where r.id = :id")
    Optional<Long> findSiloIdById(@Param("id") Long id);
}
