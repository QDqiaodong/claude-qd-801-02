package com.concrete.plant.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 搅拌楼台账（含主机数量，避免把懒加载集合直接序列化出去）。
 */
@Getter
@Setter
public class PlantVO {

    private Long id;
    private String plantCode;
    private String name;
    private Integer lineCount;
    private String location;
    private Integer mixerCount;

    public PlantVO() {
    }

    public PlantVO(Long id, String plantCode, String name, Integer lineCount, String location) {
        this.id = id;
        this.plantCode = plantCode;
        this.name = name;
        this.lineCount = lineCount;
        this.location = location;
    }
}
