package com.concrete.plant.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.DispatchTicket;
import com.concrete.plant.dto.SiteDeliveryVO;

public interface DispatchTicketRepository extends JpaRepository<DispatchTicket, Long> {

    @Query("select t from DispatchTicket t order by t.loadTime desc, t.id desc")
    List<DispatchTicket> findAllOrdered();

    @Query("select t from DispatchTicket t where t.order.id = :orderId order by t.loadTime")
    List<DispatchTicket> findByOrderId(@Param("orderId") Long orderId);

    @Query("select t from DispatchTicket t where t.truck.id = :truckId order by t.loadTime desc")
    List<DispatchTicket> findByTruckId(@Param("truckId") Long truckId);

    /**
     * 试块登记可选发车单：同一任务单下已装车（含已出厂/已到工地/已签收）的发车单，按装车时间升序。
     */
    @Query("select t from DispatchTicket t where t.order.id = :orderId order by t.loadTime asc, t.id asc")
    List<DispatchTicket> findLoadedByOrderId(@Param("orderId") Long orderId);

    /**
     * 手写 JPQL 四：按工地汇总发货量（只统计已签收）。
     */
    @Query("select new com.concrete.plant.dto.SiteDeliveryVO(t.order.projectName, sum(t.signVolume), count(t)) "
            + "from DispatchTicket t where t.status = 'SIGNED' "
            + "group by t.order.projectName order by sum(t.signVolume) desc")
    List<SiteDeliveryVO> statBySite();

    @Query("select sum(t.signVolume) from DispatchTicket t where t.order.id = :orderId")
    BigDecimal sumSignedByOrder(@Param("orderId") Long orderId);

    @Query("select count(t) from DispatchTicket t where t.status = 'SIGNED'")
    long countSigned();

    @Query("select count(t) from DispatchTicket t where t.order.id = :orderId")
    long countByOrderId(@Param("orderId") Long orderId);
}
