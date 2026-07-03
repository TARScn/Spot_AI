package com.tars.spotai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpotAiChatToolsTest {

    @Test
    void recommendToolDescriptionMentionsLinkFieldsForFinalAnswer() throws Exception {
        Method method = SpotAiChatTools.class.getMethod(
                "recommendShops",
                long.class,
                long.class,
                String.class,
                String.class,
                int.class);
        Tool tool = method.getAnnotation(Tool.class);

        assertThat(tool.description()).contains("markdownLink", "answerHint", "final answer");
    }

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
        SpotAiChatTools tools = new SpotAiChatTools(shopService, shopRepo, voucherRepo, mock(VoucherService.class), null, mapper);

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
    void recommendShopsWritesLowRiskToolCallLog() {
        ShopRepository shopRepo = mock(ShopRepository.class);
        ShopService shopService = mock(ShopService.class);
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        AiToolCallLogger logger = mock(AiToolCallLogger.class);
        when(shopRepo.findAll()).thenReturn(List.of(makeShop(6L, "平价高评分F", 50L, 92, "钟楼")));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, shopRepo, voucherRepo, mock(VoucherService.class), null, new ObjectMapper());
        tools.setToolCallLogger(logger);

        String result = tools.recommendShops(40L, 60L, "", "钟楼", 5);

        assertThat(result).contains("平价高评分F");
        verify(logger).log(argThat(command ->
                "recommendShops".equals(command.toolName())
                        && "low".equals(command.riskLevel())
                        && "shop".equals(command.targetType())
                        && Long.valueOf(6L).equals(command.targetId())
                        && "success".equals(command.status())
                        && command.input().toString().contains("minPrice=40")
                        && command.output().contains("spotai://shop/6")));
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
        SpotAiChatTools tools = new SpotAiChatTools(shopService, shopRepo, mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());

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
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), shopRepo, mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());
        assertThat(tools.recommendShops(0L, 30L, "", "", 5)).isEqualTo("[]");
    }

    @Test
    void recommendShopsBoostsCouponShopsAndReturnsStructuredReasons() throws Exception {
        ShopRepository shopRepo = mock(ShopRepository.class);
        ShopService shopService = mock(ShopService.class);
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        when(shopRepo.findAll()).thenReturn(List.of(
                makeShop(10L, "Plain Noodle", 52L, 88, "gaoxin"),
                makeShop(11L, "Coupon Noodle", 52L, 88, "gaoxin")
        ));
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenAnswer(invocation -> {
            Long shopId = invocation.getArgument(0);
            if (shopId.equals(11L)) {
                Voucher voucher = makeVoucher(101L, "20 off", 5000L, 3000L);
                voucher.setShopId(11L);
                return List.of(voucher);
            }
            return List.of();
        });
        SpotAiChatTools tools = new SpotAiChatTools(shopService, shopRepo, voucherRepo, mock(VoucherService.class), null, new ObjectMapper());

        String result = tools.recommendShops(40L, 60L, "noodle coupon", "gaoxin", 2);

        List<Map<String, Object>> items = new ObjectMapper().readValue(result, new TypeReference<>() {});
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("name")).isEqualTo("Coupon Noodle");
        assertThat(items.get(0)).containsKeys("matchScore", "reasons", "hasCoupon", "couponTitles", "shopUrl", "markdownLink", "answerHint");
        assertThat(items.get(0).get("hasCoupon")).isEqualTo(true);
        assertThat(items.get(0).get("shopUrl")).isEqualTo("spotai://shop/11");
        assertThat(items.get(0).get("markdownLink")).isEqualTo("[Coupon Noodle](spotai://shop/11)");
        assertThat(items.get(0).get("answerHint").toString()).contains("[Coupon Noodle](spotai://shop/11)");
        assertThat(items.get(0).get("answerHint").toString()).contains("avgPrice=52");
    }

    @Test
    void recommendShopsUsesReviewSummaryForSceneMatching() throws Exception {
        ShopRepository shopRepo = mock(ShopRepository.class);
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        ReviewSummaryService summaryService = mock(ReviewSummaryService.class);
        ObjectProvider<ReviewSummaryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(summaryService);
        when(shopRepo.findAll()).thenReturn(List.of(
                makeShop(20L, "Quiet Bistro", 85L, 90, "qujiang"),
                makeShop(21L, "Busy Snack", 70L, 92, "qujiang")
        ));
        ReviewSummaryDTO dateSummary = new ReviewSummaryDTO();
        dateSummary.setStatus(ReviewSummaryDTO.STATUS_READY);
        dateSummary.setSummary("Quiet and comfortable.");
        dateSummary.setScenes(List.of("date"));
        dateSummary.setHighlights(List.of("good service"));
        ReviewSummaryDTO snackSummary = new ReviewSummaryDTO();
        snackSummary.setStatus(ReviewSummaryDTO.STATUS_READY);
        snackSummary.setSummary("Fast meal.");
        snackSummary.setScenes(List.of("solo"));
        when(summaryService.querySummary(20L)).thenReturn(Result.ok(dateSummary));
        when(summaryService.querySummary(21L)).thenReturn(Result.ok(snackSummary));
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenReturn(List.of());
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), shopRepo, voucherRepo, mock(VoucherService.class), provider, new ObjectMapper());

        String result = tools.recommendShops(0L, 100L, "date", "qujiang", 2);

        List<Map<String, Object>> items = new ObjectMapper().readValue(result, new TypeReference<>() {});
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("name")).isEqualTo("Quiet Bistro");
        assertThat(items.get(0).get("reasons").toString()).contains("date");
    }

    @Test
    void recommendShopsRanksSceneMatchAboveCategoryOnlyMatch() throws Exception {
        ShopRepository shopRepo = mock(ShopRepository.class);
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        ReviewSummaryService summaryService = mock(ReviewSummaryService.class);
        ObjectProvider<ReviewSummaryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(summaryService);
        when(shopRepo.findAll()).thenReturn(List.of(
                makeShop(30L, "Hotpot Loud", 50L, 90, "gaoxin"),
                makeShop(31L, "Hotpot Quiet Date", 50L, 88, "gaoxin")
        ));
        ReviewSummaryDTO loudSummary = new ReviewSummaryDTO();
        loudSummary.setStatus(ReviewSummaryDTO.STATUS_READY);
        loudSummary.setSummary("Busy and noisy hotpot.");
        ReviewSummaryDTO quietSummary = new ReviewSummaryDTO();
        quietSummary.setStatus(ReviewSummaryDTO.STATUS_READY);
        quietSummary.setSummary("Quiet date friendly hotpot.");
        quietSummary.setScenes(List.of("date", "quiet"));
        when(summaryService.querySummary(30L)).thenReturn(Result.ok(loudSummary));
        when(summaryService.querySummary(31L)).thenReturn(Result.ok(quietSummary));
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenReturn(List.of());
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), shopRepo, voucherRepo, mock(VoucherService.class), provider, new ObjectMapper());

        String result = tools.recommendShops(40L, 60L, "hotpot date quiet", "gaoxin", 2);

        List<Map<String, Object>> items = new ObjectMapper().readValue(result, new TypeReference<>() {});
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("name")).isEqualTo("Hotpot Quiet Date");
        assertThat(items.get(0).get("reasons").toString()).contains("date", "quiet");
    }

    @Test
    void searchShopReturnsShopsByKeywordWithLinks() {
        ShopService shopService = mock(ShopService.class);
        when(shopService.search("烤肉")).thenReturn(Result.ok(List.of(
                makeShop(1L, "西安小寨烤肉", 60L, 88, "小寨"),
                makeShop(2L, "钟楼烤肉店", 55L, 82, "钟楼")
        )));
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());

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
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());
        assertThat(tools.searchShop("不存在的店")).isEqualTo("[]");
    }

    @Test
    void searchShopReturnsEmptyForBlankKeyword() {
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());
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
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());

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
        SpotAiChatTools tools = new SpotAiChatTools(shopService, mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), null, new ObjectMapper());
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
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), provider, new ObjectMapper());

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
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), mock(VoucherRepository.class), mock(VoucherService.class), provider, new ObjectMapper());
        assertThat(tools.queryReviewSummary(1L)).contains("\"status\":\"UNAVAILABLE\"");
    }

    @Test
    void queryCouponsReturnsCouponList() {
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenReturn(List.of(
                makeVoucher(1L, "满100减20", 10000L, 8000L),
                makeVoucher(2L, "8折券", 5000L, 4000L)
        ));
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), voucherRepo, mock(VoucherService.class), null, new ObjectMapper());

        String result = tools.queryCoupons(42L);

        assertThat(result).contains("满100减20");
        assertThat(result).contains("8折券");
        assertThat(result).contains("10000");
    }

    @Test
    void queryCouponsReturnsEmptyWhenNone() {
        VoucherRepository voucherRepo = mock(VoucherRepository.class);
        when(voucherRepo.findActiveByShopId(anyLong(), any(), anyInt())).thenReturn(List.of());
        SpotAiChatTools tools = new SpotAiChatTools(mock(ShopService.class), mock(ShopRepository.class), voucherRepo, mock(VoucherService.class), null, new ObjectMapper());
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

