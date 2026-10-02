package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 试块组关联发车单（追溯用） */
@Getter
@Setter
public class CubeTicketVO {

    private Long id;
    private String ticketNo;
    private String plateNo;
    private BigDecimal loadVolume;
    private LocalDateTime loadTime;
    private String status;
    private String statusName;
}
