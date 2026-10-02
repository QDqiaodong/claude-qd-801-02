package com.concrete.plant.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 复检录入请求：复检必须引用原检测（路径上的复检单已绑定原检测项），只接受三块读数与操作者。
 */
@Getter
@Setter
public class RecheckRequest {

    @NotNull(message = "复检试块 1 读数不能为空")
    private BigDecimal strength1;

    @NotNull(message = "复检试块 2 读数不能为空")
    private BigDecimal strength2;

    @NotNull(message = "复检试块 3 读数不能为空")
    private BigDecimal strength3;

    @NotBlank(message = "复检操作者不能为空")
    @Size(max = 32, message = "操作者姓名过长")
    private String operatorName;

    private String remark;
}
