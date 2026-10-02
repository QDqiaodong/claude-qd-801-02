package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 检测项结果录入请求：三块抗压读数由服务端统一计算平均、最小与判定，前端不得传结论。
 */
@Getter
@Setter
public class ResultRequest {

    @NotNull(message = "试块 1 读数不能为空")
    private BigDecimal strength1;

    @NotNull(message = "试块 2 读数不能为空")
    private BigDecimal strength2;

    @NotNull(message = "试块 3 读数不能为空")
    private BigDecimal strength3;

    @NotBlank(message = "检测操作者不能为空")
    @Size(max = 32, message = "操作者姓名过长")
    private String operatorName;

    /** 备用：需要时可直接取三块读数集合 */
    public List<BigDecimal> readings() {
        return List.of(strength1, strength2, strength3);
    }
}
