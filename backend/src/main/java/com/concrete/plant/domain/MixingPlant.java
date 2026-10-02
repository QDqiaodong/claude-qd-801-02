package com.concrete.plant.domain;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 搅拌楼：一台楼里挂若干台搅拌主机。
 */
@Entity
@Table(name = "mixing_plant")
@BatchSize(size = 50)
@Getter
@Setter
public class MixingPlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_code", nullable = false, unique = true, length = 32)
    private String plantCode;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 生产线数量 */
    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "location", length = 128)
    private String location;

    @OneToMany(mappedBy = "plant")
    @BatchSize(size = 50)
    private List<Mixer> mixers = new ArrayList<>();
}
