package com.concrete.plant.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.concrete.plant.domain.Mixer;
import com.concrete.plant.dto.MixerLoadVO;

public interface MixerRepository extends JpaRepository<Mixer, Long> {

    @Query("select m from Mixer m order by m.mixerCode")
    List<Mixer> findAllOrdered();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mixer m where m.id = :id")
    Optional<Mixer> findByIdForUpdate(@Param("id") Long id);

    /**
     * 主机产能负载：已完成方量取该主机名下所有发车单的装车方量，
     * 用子查询聚合避免一次性拉出全部任务单与发车单（配合 @BatchSize 控制 N+1）。
     */
    @Query("select new com.concrete.plant.dto.MixerLoadVO("
            + "m.id, m.mixerCode, m.name, m.model, "
            + "(select p.name from MixingPlant p where p.id = m.plant.id), "
            + "m.theoreticalCapacity, m.dailyPlanVolume, m.status, "
            + "(select sum(t.loadVolume) from DispatchTicket t where t.order.mixer.id = m.id), "
            + "(select count(o) from ProductionOrder o where o.mixer.id = m.id)) "
            + "from Mixer m order by m.mixerCode")
    List<MixerLoadVO> loadBoard();

    @Query("select count(o) from ProductionOrder o where o.mixer.id = :mixerId and o.status = 'PRODUCING'")
    long countProducing(@Param("mixerId") Long mixerId);
}
