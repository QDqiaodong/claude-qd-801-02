package com.concrete.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.BizException;
import com.concrete.plant.common.Dict;
import com.concrete.plant.domain.Mixer;
import com.concrete.plant.domain.MixingPlant;
import com.concrete.plant.dto.MixerLoadVO;
import com.concrete.plant.dto.PlantVO;
import com.concrete.plant.repository.MixerRepository;
import com.concrete.plant.repository.MixingPlantRepository;

/**
 * 搅拌楼与主机台账。
 */
@Service
public class MixerService {

    private final MixerRepository mixerRepository;
    private final MixingPlantRepository plantRepository;

    public MixerService(MixerRepository mixerRepository, MixingPlantRepository plantRepository) {
        this.mixerRepository = mixerRepository;
        this.plantRepository = plantRepository;
    }

    @Transactional(readOnly = true)
    public List<PlantVO> listPlants() {
        List<MixingPlant> plants = plantRepository.findAllOrdered();
        List<PlantVO> vos = new java.util.ArrayList<>();
        for (MixingPlant plant : plants) {
            PlantVO vo = new PlantVO(plant.getId(), plant.getPlantCode(), plant.getName(),
                    plant.getLineCount(), plant.getLocation());
            vo.setMixerCount(plant.getMixers().size());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 主机产能负载看板：完成方量 / 当日计划方量，超计划部分单独给出。
     */
    @Transactional(readOnly = true)
    public List<MixerLoadVO> loadBoard() {
        List<MixerLoadVO> board = mixerRepository.loadBoard();
        for (MixerLoadVO vo : board) {
            vo.setStatusName(Dict.mixerStatusName(vo.getStatus()));
            BigDecimal plan = nz(vo.getDailyPlanVolume());
            BigDecimal done = nz(vo.getDoneVolume());
            vo.setDailyPlanVolume(plan);
            vo.setDoneVolume(done);
            if (plan.compareTo(BigDecimal.ZERO) > 0) {
                vo.setLoadRate(done.multiply(BigDecimal.valueOf(100)).divide(plan, 1, RoundingMode.HALF_UP));
            } else {
                vo.setLoadRate(BigDecimal.ZERO);
            }
            vo.setOverPlan(done.compareTo(plan) > 0);
            vo.setOverVolume(vo.isOverPlan() ? done.subtract(plan) : BigDecimal.ZERO);
        }
        return board;
    }

    @Transactional(readOnly = true)
    public List<Mixer> listMixers() {
        return mixerRepository.findAllOrdered();
    }

    @Transactional
    public MixerLoadVO updatePlan(Long id, BigDecimal dailyPlanVolume) {
        Mixer mixer = mixerRepository.findByIdForUpdate(id).orElseThrow(() -> new BizException("搅拌主机不存在：id=" + id));
        if (dailyPlanVolume == null || dailyPlanVolume.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("当日计划方量必须大于 0");
        }
        if (mixer.getTheoreticalCapacity() != null
                && dailyPlanVolume.compareTo(mixer.getTheoreticalCapacity().multiply(BigDecimal.valueOf(24))) > 0) {
            throw new BizException("当日计划方量 " + dailyPlanVolume + " m³ 超过主机 24 小时理论产能上限");
        }
        mixer.setDailyPlanVolume(dailyPlanVolume);
        mixerRepository.save(mixer);
        return loadBoard().stream()
                .filter(vo -> vo.getMixerId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BizException("搅拌主机不存在：id=" + id));
    }

    @Transactional
    public Mixer updateStatus(Long id, String status) {
        Mixer mixer = mixerRepository.findByIdForUpdate(id).orElseThrow(() -> new BizException("搅拌主机不存在：id=" + id));
        if (!Dict.isValidMixerStatus(status)) {
            throw new BizException("未知的主机运行状态：" + status);
        }
        if ("FAULT".equals(status)) {
            throw new BizException("故障停机必须通过「临时故障停机」流程完成任务预演与整批改派，不能只修改主机状态");
        }
        if ("MAINTENANCE".equals(status)) {
            long producing = mixerRepository.countProducing(id);
            if (producing > 0) {
                throw new BizException("主机 " + mixer.getName() + " 尚有 " + producing + " 张生产中任务单，不能置为「"
                        + Dict.mixerStatusName(status) + "」");
            }
        }
        mixer.setStatus(status);
        return mixerRepository.save(mixer);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
