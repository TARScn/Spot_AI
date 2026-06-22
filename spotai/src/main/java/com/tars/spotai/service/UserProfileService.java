package com.tars.spotai.service;

import com.tars.spotai.dto.BlogViewDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewViewDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.UserProfileDTO;
import com.tars.spotai.dto.UserVoucherDTO;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.BlogRepository;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.VoucherRepository;
import com.tars.spotai.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserProfileService {
    private final BlogRepository blogRepository;
    private final FollowRepository followRepository;
    private final ReviewRepository reviewRepository;
    private final VoucherRepository voucherRepository;
    private final ShopRepository shopRepository;
    private final BlogService blogService;
    private final ReviewService reviewService;
    private final SignService signService;

    public UserProfileService(BlogRepository blogRepository,
                              FollowRepository followRepository,
                              ReviewRepository reviewRepository,
                              VoucherRepository voucherRepository,
                              ShopRepository shopRepository,
                              BlogService blogService,
                              ReviewService reviewService,
                              SignService signService) {
        this.blogRepository = blogRepository;
        this.followRepository = followRepository;
        this.reviewRepository = reviewRepository;
        this.voucherRepository = voucherRepository;
        this.shopRepository = shopRepository;
        this.blogService = blogService;
        this.reviewService = reviewService;
        this.signService = signService;
    }

    public Result<UserProfileDTO> queryCurrentProfile() {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("璇峰厛鐧诲綍");
        }

        Long userId = currentUser.getId();
        List<BlogViewDTO> myBlogs = blogService.queryBlogByUser(userId, 1).getData();
        List<BlogViewDTO> likedBlogs = blogService.queryUserLikedBlogs(userId, 10);
        List<ReviewViewDTO> reviews = reviewService.queryUserReviewList(userId, 10);
        List<UserVoucherDTO> vouchers = enrichVouchers(voucherRepository.findOrdersByUserId(userId, 10));

        UserProfileDTO profile = new UserProfileDTO();
        profile.setUser(currentUser);
        profile.setSignDays(readSignDays());
        profile.setFollowCount(followRepository.countFollows(userId));
        profile.setFanCount(followRepository.countFans(userId));
        profile.setBlogCount(blogRepository.countByUserId(userId));
        profile.setLikedBlogCount(likedBlogs.size());
        profile.setVoucherCount(voucherRepository.countOrdersByUserId(userId));
        profile.setReviewCount(reviewRepository.countByUserId(userId));
        profile.setMyBlogs(myBlogs == null ? List.of() : myBlogs);
        profile.setLikedBlogs(likedBlogs);
        profile.setVouchers(vouchers);
        profile.setReviews(reviews);
        return Result.ok(profile);
    }

    private Integer readSignDays() {
        Result<Integer> result = signService.countContinuousSignDays();
        return result.isSuccess() && result.getData() != null ? result.getData() : 0;
    }

    private List<UserVoucherDTO> enrichVouchers(List<UserVoucherDTO> orders) {
        return orders.stream().peek(order -> {
            Voucher voucher = voucherRepository.findVoucherById(order.getVoucherId());
            if (voucher == null) {
                return;
            }
            order.setShopId(voucher.getShopId());
            order.setTitle(voucher.getTitle());
            order.setSubTitle(voucher.getSubTitle());
            order.setPayValue(voucher.getPayValue());
            order.setActualValue(voucher.getActualValue());
            order.setType(voucher.getType());
            Shop shop = voucher.getShopId() == null ? null : shopRepository.findById(voucher.getShopId());
            if (shop != null) {
                order.setShopName(shop.getName());
            }
        }).toList();
    }
}
