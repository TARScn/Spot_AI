package com.tars.spotai.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class SeckillVoucherDTO extends VoucherDTO {
    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存必须大于0")
    private Integer stock;

    private String allowedLevels;

    private Integer minLevel;

    @NotNull(message = "秒杀开始时间不能为空")
    private LocalDateTime beginTime;

    @NotNull(message = "秒杀结束时间不能为空")
    @Future(message = "秒杀结束时间必须晚于当前时间")
    private LocalDateTime endTime;
}
