package com.concrete.plant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.Dict;
import com.concrete.plant.dto.DailyLogVO;
import com.concrete.plant.dto.GradeVolumeVO;
import com.concrete.plant.dto.SiloVO;
import com.concrete.plant.dto.SiteDeliveryVO;
import com.concrete.plant.repository.DispatchTicketRepository;
import com.concrete.plant.repository.ProductionOrderRepository;
import com.concrete.plant.repository.ReplenishRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统计与看板：结果按 30 秒缓存进 Redis，避免每帧刷新都跑聚合查询。
 */
@Service
public class StatsService {

    private static final String CACHE_KEY = "concrete:plant:dashboard";

    private final ProductionOrderRepository orderRepository;
    private final DispatchTicketRepository ticketRepository;
    private final ReplenishRequestRepository replenishRepository;
    private final SiloService siloService;
    private final DispatchService dispatchService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StatsService(ProductionOrderRepository orderRepository, DispatchTicketRepository ticketRepository,
                        ReplenishRequestRepository replenishRepository, SiloService siloService,
                        DispatchService dispatchService, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.replenishRepository = replenishRepository;
        this.siloService = siloService;
        this.dispatchService = dispatchService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 手写 JPQL：按强度等级统计方量 */
    @Transactional(readOnly = true)
    public List<GradeVolumeVO> gradeVolume() {
        return orderRepository.statByGrade();
    }

    /** 手写 JPQL：按工地汇总发货量 */
    @Transactional(readOnly = true)
    public List<SiteDeliveryVO> siteDelivery() {
        return ticketRepository.statBySite();
    }

    /** 手写 JPQL：按日期查生产台账 */
    @Transactional(readOnly = true)
    public List<DailyLogVO> dailyLog(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        List<DailyLogVO> logs = orderRepository.dailyLog(from, to);
        for (DailyLogVO log : logs) {
            log.setStatusName(Dict.orderStatusName(log.getStatus()));
            if (log.getDeliveredVolume() == null) {
                log.setDeliveredVolume(BigDecimal.ZERO);
            }
        }
        return logs;
    }

    /**
     * 看板总览（走 Redis 缓存）。
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> dashboard() {
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                Map<String, Object> hit = objectMapper.readValue(cached, Map.class);
                hit.put("cached", true);
                return hit;
            } catch (Exception ignored) {
                // 缓存内容损坏则重新计算
            }
        }
        List<GradeVolumeVO> grades = orderRepository.statByGrade();
        List<SiteDeliveryVO> sites = ticketRepository.statBySite();
        BigDecimal signedVolume = BigDecimal.ZERO;
        for (SiteDeliveryVO site : sites) {
            signedVolume = signedVolume.add(site.getDeliveredVolume() == null ? BigDecimal.ZERO : site.getDeliveredVolume());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("gradeVolume", grades);
        data.put("siteDelivery", sites);
        data.put("orderTotal", orderRepository.count());
        data.put("pendingCount", orderRepository.countByStatus("PENDING"));
        data.put("producingCount", orderRepository.countByStatus("PRODUCING"));
        data.put("doneCount", orderRepository.countByStatus("DONE"));
        data.put("ticketTotal", ticketRepository.count());
        data.put("signedTickets", ticketRepository.countSigned());
        data.put("signedVolume", signedVolume);
        data.put("pendingReplenish", replenishRepository.countPending());
        data.put("dispatchCount", dispatchService.dispatchCount());
        List<SiloVO> lowStock = siloService.lowStock();
        data.put("lowStock", lowStock);
        data.put("lowStockCount", lowStock.size());
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("cached", false);
        try {
            redis.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(data), java.time.Duration.ofSeconds(30));
        } catch (Exception ignored) {
            // 缓存写失败不影响接口
        }
        return data;
    }
}
