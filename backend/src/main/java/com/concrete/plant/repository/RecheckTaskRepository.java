package com.concrete.plant.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.RecheckTask;

public interface RecheckTaskRepository extends JpaRepository<RecheckTask, Long> {

    Optional<RecheckTask> findByRecheckNo(String recheckNo);

    /** 唯一复检：按原 28d 检测项定位 */
    Optional<RecheckTask> findByOriginalItemId(Long originalItemId);

    @Query("select count(r) from RecheckTask r where r.group.id = :groupId")
    long countByGroupId(@Param("groupId") Long groupId);

    /** 复检提交与原检测并发时，锁住复检单行防止重复提交 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecheckTask r where r.id = :id")
    Optional<RecheckTask> findByIdForUpdate(@Param("id") Long id);
}
