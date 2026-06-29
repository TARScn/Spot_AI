package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpotAiChatToolsTest {

    @Test
    void recommendShopsFiltersByBudgetRangeAndReturnsShopLinks() {
        ShopRepository shopRepo = mock(ShopRepository.class);
        ShopService shopService = mock(ShopService.class);
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        when(shopRepo.findAll()).thenReturn(List.of(
                makeShop(1L, "贵店A", 100L, 90, "小寨"),
                makeShop(2L, "平价好店B", 45L, 85, "钟楼"),
                makeShop(3L, "平价一般C", 48L, 65, "小寨"),
                makeShop(4L, "便宜高评分D", 30L, 95, "高新"),
                makeShop(5L, "超贵E", 200L, 80, "曲江"),
                makeShop(6L, "平价高评分F", 50L, 92, "钟楼")
        ));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, shopRepo, voucherRepo, null, mapper);

        String result = tools.recommendShops(40L, 60L, "", "", 5);

        assertThat(result).isNotEqualTo("[]");
        assertThat(result).contains("平价好店B");
        assertThat(result).contains("平价高评分F");
        assertThat(result).contains("spotai://shop/6");
        assertThat(result).contains("\"reason\"");
        assertThat(result).doesNotContain("贵店A");
        assertThat(result).doesNotContain("便宜高评分D");
        assertThat(result).doesNotContain("超贵E");
        int idx6 = result.indexOf("平价高评分F");
        int idx3 = result.indexOf("平价一般C");
        int idx2 = result.indexOf("平价好店B");
        assertThat(idx6).isLessThan(idx3);
        assertThat(idx3).isLessThan(idx2);
    }

    @Test
    void recommendShopsFiltersByAreaAndKeyword() {
        ShopRepository shopRepo = mock(ShopRepository.class);
        ShopService shopService = mock(ShopService.class);
        when(shopService.search("烤肉 钟楼")).thenReturn(Result.ok(List.of(
                makeShop(1L, "钟楼烤肉", 52L, 88, "钟楼"),
                makeShop(2L, "小寨烤肉", 48L, 95, "小寨"),
                makeShop(3L, "钟楼火锅", 55L, 90, "钟楼")
        )));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, shopRepo, mock(VoucherRepository.class), null, new ObjectMapper());

        String result = tools.recommendShops(40L, 60L, "烤肉", "钟楼", 5);

        assertThat(result).contains("钟楼烤肉");
        assertThat(result).contains("spotai://shop/1");
        assertThat(result).doesNotContain("小寨烤肉");
        assertThat(result).doesNotContain("钟楼火锅");
    }

    @Test
    void recommendShopsReturnsEmptyWhenNoMatch() {
        ShopRepository shopRepo = mock(ShopRepository.class);
        when(shopRepo.findAll()).thenReturn(List.of(makeShop(1L, "贵店", 100L, 80, "小寨")));
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), shopRepo, mock(VoucherRepository.class), null, new ObjectMapper());
        assertThat(tools.recommendShops(0L, 30L, "", "", 5)).isEqualTo("[]");
    }

    @Test
    void searchShopReturnsShopsByKeywordWithLinks() {
        ShopService shopService = mock(ShopService.class);
        when(shopService.search("烤肉")).thenReturn(Result.ok(List.of(
                makeShop(1L, "西安小寨烤肉", 60L, 88, "小寨"),
                makeShop(2L, "钟楼烤肉店", 55L, 82, "钟楼")
        )));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), null, new ObjectMapper());

        String result = tools.searchShop("烤肉");

        assertThat(result).contains("西安小寨烤肉");
        assertThat(result).contains("钟楼烤肉店");
        assertThat(result).contains("spotai://shop/1");
        assertThat(result).contains("60");
    }

    @Test
    void searchShopReturnsEmptyArrayWhenNoMatch() {
        ShopService shopService = mock(ShopService.class);
        when(shopService.search("不存在的店")).thenReturn(Result.ok(List.of()));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), null, new ObjectMapper());
        assertThat(tools.searchShop("不存在的店")).isEqualTo("[]");
    }

    @Test
    void searchShopReturnsEmptyForBlankKeyword() {
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), mock(VoucherRepository.class), null, new ObjectMapper());
        assertThat(tools.searchShop("")).isEqualTo("[]");
        assertThat(tools.searchShop("  ")).isEqualTo("[]");
    }

    @Test
    void queryShopDetailReturnsShopInfoWithLink() {
        ShopService shopService = mock(ShopService.class);
        Shop shop = makeShop(42L, "测试店", 80L, 90, "高新");
        shop.setAddress("高新路1号");
        shop.setOpenHours("10:00-22:00");
        shop.setComments(100);
        when(shopService.queryById(42L)).thenReturn(Result.ok(shop));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), null, new ObjectMapper());

        String result = tools.queryShopDetail(42L);

        assertThat(result).contains("\"id\":42");
        assertThat(result).contains("\"name\":\"测试店\"");
        assertThat(result).contains("\"shopUrl\":\"spotai://shop/42\"");
        assertThat(result).contains("\"avgPrice\":80");
        assertThat(result).contains("\"score\":90");
        assertThat(result).contains("\"openHours\":\"10:00-22:00\"");
        assertThat(result).contains("\"comments\":100");
    }

    @Test
    void queryShopDetailReturnsErrorWhenNotFound() {
        ShopService shopService = mock(ShopService.class);
        when(shopService.queryById(999L)).thenReturn(Result.fail("店铺不存在"));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), null, new ObjectMapper());
        assertThat(tools.queryShopDetail(999L)).contains("\"error\"");
    }

    @Test
    void queryReviewSummaryReturnsSummaryData() {
        ReviewSummaryService summaryService = mock(ReviewSummaryService.class);
        ObjectProvider<ReviewSummaryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(summaryService);
        ReviewSummaryDTO summary = new ReviewSummaryDTO();
        summary.setStatus(ReviewSummaryDTO.STATUS_READY);
        summary.setSummary("总体评价不错");
        summary.setHighlights(List.of("服务好", "环境稳"));
        summary.setWeaknesses(List.of("价格偏高"));
        summary.setScenes(List.of("约会", "聚餐"));
        when(summaryService.querySummary(42L)).thenReturn(Result.ok(summary));
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), mock(VoucherRepository.class), provider, new ObjectMapper());

        String result = tools.queryReviewSummary(42L);

        assertThat(result).contains("\"summary\":\"总体评价不错\"");
        assertThat(result).contains("\"highlights\"");
        assertThat(result).contains("\"weaknesses\"");
        assertThat(result).contains("\"scenes\"");
    }

    @Test
    void queryReviewSummaryReturnsUnavailableWhenNoService() {
        ObjectProvider<ReviewSummaryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), mock(VoucherRepository.class), provider, new ObjectMapper());
        assertThat(tools.queryReviewSummary(1L)).contains("\"status\":\"UNAVAILABLE\"");
    }

    @Test
    void queryCouponsReturnsCouponList() {
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenReturn(List.of(
                makeVoucher(1L, "满100减20", 10000L, 8000L),
                makeVoucher(2L, "8折券", 5000L, 4000L)
        ));
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), voucherRepo, null, new ObjectMapper());

        String result = tools.queryCoupons(42L);

        assertThat(result).contains("满100减20");
        assertThat(result).contains("8折券");
        assertThat(result).contains("10000");
    }

    @Test
    void queryCouponsReturnsEmptyWhenNone() {
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenReturn(List.of());
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), voucherRepo, null, new ObjectMapper());
        assertThat(tools.queryCoupons(42L)).isEqualTo("[]");
    }

    private static Voucher makeVoucher(Long id, String title, Long payValue, Long actualValue) {
        Voucher v = new Voucher();
        v.setId(id);
        v.setTitle(title);
        v.setPayValue(payValue);
        v.setActualValue(actualValue);
        return v;
    }

    private static Shop makeShop(Long id, String name, Long avgPrice, Integer score, String area) {
        Shop s = new Shop();
        s.setId(id);
        s.setName(name);
        s.setAvgPrice(avgPrice);
        s.setScore(score);
        s.setArea(area);
        return s;
    }
}
