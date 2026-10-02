package com.concrete.plant.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.Silo;

public interface SiloRepository extends JpaRepository<Silo, Long> {

    @Query("select s from Silo s order by s.siloCode")
    List<Silo> findAllOrdered();

    /**
     * 手写 JPQL 五：低于安全库存的筒仓（前端据此闪烁告警）。
     */
    @Query("select s from Silo s where s.stockTon < s.safetyStockTon order by (s.safetyStockTon - s.stockTon) desc")
    List<Silo> findLowStock();

    @Query("select sum(s.stockTon) from Silo s where s.materialCode = :materialCode")
    BigDecimal sumStockByMaterial(@Param("materialCode") String materialCode);
}
