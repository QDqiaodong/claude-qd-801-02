package com.concrete.plant.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.StrengthTestItem;

public interface StrengthTestItemRepository extends JpaRepository<StrengthTestItem, Long> {

    /** 待检清单：全部检测项按到期时间升序，页面状态由服务端按当前时钟派生 */
    @Query("select i from StrengthTestItem i order by i.dueAt asc, i.id asc")
    List<StrengthTestItem> findAllItems();

    @Query("select i from StrengthTestItem i where i.group.id = :groupId order by i.ageDays asc")
    List<StrengthTestItem> findByGroupId(@Param("groupId") Long groupId);

    /** 两个质检员并发提交同一检测项：行锁串行化，后到请求看到状态已变化 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StrengthTestItem i where i.id = :id")
    Optional<StrengthTestItem> findByIdForUpdate(@Param("id") Long id);
}
