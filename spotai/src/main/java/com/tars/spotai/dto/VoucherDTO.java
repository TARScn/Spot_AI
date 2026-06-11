package com.tars.spotai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoucherDTO {
    @NotNull(message = "商户ID不能为空")
    @Min(value = 1, message = "商户ID不合法")
    private Long shopId;

    @NotBlank(message = "优惠券标题不能为空")
    private String title;

    private String subTitle;

    private String rules;

    @NotNull(message = "支付金额不能为空")
    @Min(value = 0, message = "支付金额不能小于0")
    private Long payValue;

    @NotNull(message = "抵扣金额不能为空")
    @Min(value = 1, message = "抵扣金额必须大于0")
    private Long actualValue;

    private Integer status = 1;
}
