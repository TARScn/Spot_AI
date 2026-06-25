package com.tars.spotai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.VoucherRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpringAiCouponAgent implements CouponAgent {
    private final VoucherRepository voucherRepository;
    private final ObjectMapper objectMapper;

    public SpringAiCouponAgent(VoucherRepository voucherRepository, ObjectMapper objectMapper) {
        this.voucherRepository = voucherRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String buildContext(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return "";
        }
        try {
            List<Voucher> vouchers = voucherRepository.findActiveByShopId(shopId, LocalDateTime.now(), 8);
            if (vouchers.isEmpty()) {
                return "";
            }
            List<Map<String, Object>> items = vouchers.stream()
                    .map(v -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", v.getId());
                        item.put("title", v.getTitle());
                        if (v.getSubTitle() != null) item.put("subTitle", v.getSubTitle());
                        item.put("payValue", v.getPayValue());
                        item.put("actualValue", v.getActualValue());
                        if (v.getRules() != null) item.put("rules", v.getRules());
                        return item;
                    })
                    .toList();
            return objectMapper.writeValueAsString(items);
        } catch (Exception ignored) {
            return "";
        }
    }
}
