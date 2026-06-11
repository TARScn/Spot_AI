package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.service.VoucherService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoucherOrderController {
    private final VoucherService voucherService;

    public VoucherOrderController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/voucher-order/seckill/{voucherId}")
    public Result<Long> seckillVoucher(@PathVariable Long voucherId) {
        return voucherService.seckillVoucher(voucherId);
    }
}
