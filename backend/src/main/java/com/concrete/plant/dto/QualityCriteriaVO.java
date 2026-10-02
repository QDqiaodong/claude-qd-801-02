package com.concrete.plant.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 判定来源说明：前端录入框的阈值提示只允许取这里的值，不得在页面自行计算，
 * 以保证数据库、后端接口与前端共用同一判定来源。
 */
@Getter
@Setter
public class QualityCriteriaVO {

    private List<GradeCriterionVO> grades;
    private int specimenCount;
    private String rule28;
    private String rule7;
}
