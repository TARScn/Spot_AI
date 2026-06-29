package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.SeckillVoucherDTO;
import com.tars.spotai.dto.VoucherActivityDTO;
import com.tars.spotai.dto.VoucherDTO;
import com.tars.spotai.service.VoucherService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class VoucherController {
    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping("/voucher/activities")
    public Result<List<VoucherActivityDTO>> listActivities() {
        return voucherService.queryActivities();
    }

    @GetMapping("/voucher/activities/of/shop")
    public Result<List<VoucherActivityDTO>> listActivitiesByShop(@RequestParam Long shopId) {
        return voucherService.queryActivitiesByShopId(shopId);
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
