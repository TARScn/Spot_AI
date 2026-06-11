package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.SeckillVoucherDTO;
import com.tars.spotai.dto.VoucherDTO;
import com.tars.spotai.service.VoucherService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoucherController {
    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/voucher")
    public Result<Long> addVoucher(@Valid @RequestBody VoucherDTO dto) {
        return voucherService.addVoucher(dto);
    }

    @PostMapping("/voucher/seckill")
    public Result<Long> addSeckillVoucher(@Valid @RequestBody SeckillVoucherDTO dto) {
        return voucherService.addSeckillVoucher(dto);
    }
}
