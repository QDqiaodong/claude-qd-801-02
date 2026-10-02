package com.concrete.plant.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.ProductionOrder;
import com.concrete.plant.dto.DailyLogVO;
import com.concrete.plant.dto.GradeVolumeVO;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    Optional<ProductionOrder> findByOrderNo(String orderNo);

    @Query("select o from ProductionOrder o order by o.plannedStart desc, o.id desc")
    List<ProductionOrder> findAllOrdered();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProductionOrder o where o.id = :id")
    Optional<ProductionOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select o from ProductionOrder o where o.mixer.id = :mixerId order by o.plannedStart asc, o.id asc")
    List<ProductionOrder> findByMixerId(@Param("mixerId") Long mixerId);

    /**
     * 手写 JPQL 一：按强度等级统计计划方量。
     */
    @Query("select new com.concrete.plant.dto.GradeVolumeVO(o.strengthGrade, sum(o.plannedVolume), count(o)) "
            + "from ProductionOrder o group by o.strengthGrade order by o.strengthGrade")
    List<GradeVolumeVO> statByGrade();

    /**
     * 手写 JPQL 二：按日期查生产台账（含已签收方量子查询）。
     */
    @Query("select new com.concrete.plant.dto.DailyLogVO("
            + "o.id, o.orderNo, o.projectName, o.strengthGrade, o.plannedVolume, o.status, m.name, o.plannedStart, "
            + "(select sum(t.signVolume) from DispatchTicket t where t.order.id = o.id)) "
            + "from ProductionOrder o left join o.mixer m "
            + "where o.plannedStart >= :from and o.plannedStart < :to "
            + "order by o.plannedStart")
    List<DailyLogVO> dailyLog(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 手写 JPQL 三：同一主机时段重叠校验（新建时 orderId 传 -1）。
     */
    @Query("select o from ProductionOrder o where o.mixer.id = :mixerId and o.id <> :orderId "
            + "and o.status <> 'DONE' and o.plannedStart < :end and o.plannedEnd > :start")
    List<ProductionOrder> findOverlapping(@Param("mixerId") Long mixerId, @Param("orderId") Long orderId,
                                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select o from ProductionOrder o where o.mixer.id = :mixerId "
            + "and o.plannedStart < :end and o.plannedEnd > :start "
            + "and o.id not in :excludeOrderIds order by o.plannedStart asc, o.id asc")
    List<ProductionOrder> findOverlappingExcluding(@Param("mixerId") Long mixerId,
                                                   @Param("excludeOrderIds") List<Long> excludeOrderIds,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Query("select count(o) from ProductionOrder o where o.status = :status")
    long countByStatus(@Param("status") String status);
}
