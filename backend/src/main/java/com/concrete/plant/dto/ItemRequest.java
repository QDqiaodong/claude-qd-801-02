package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 配合比明细行的每方用量（只能随任务单一并提交）。
 */
@Getter
@Setter
public class ItemRequest {

    private String materialCode;
    private BigDecimal dosage;
}
