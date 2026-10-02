package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 按工地汇总发货量（以签收方量为准）。
 */
@Getter
@Setter
public class SiteDeliveryVO {

    private String projectName;
    private BigDecimal deliveredVolume;
    private Long ticketCount;

    public SiteDeliveryVO(String projectName, BigDecimal deliveredVolume, Long ticketCount) {
        this.projectName = projectName;
        this.deliveredVolume = deliveredVolume;
        this.ticketCount = ticketCount == null ? 0L : ticketCount;
    }
}
