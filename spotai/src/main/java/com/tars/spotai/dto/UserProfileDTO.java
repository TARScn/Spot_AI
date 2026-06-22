package com.tars.spotai.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileDTO {
    private UserDTO user;
    private Integer signDays;
    private Integer followCount;
    private Integer fanCount;
    private Integer blogCount;
    private Integer likedBlogCount;
    private Integer voucherCount;
    private Integer reviewCount;
    private List<BlogViewDTO> myBlogs;
    private List<BlogViewDTO> likedBlogs;
    private List<UserVoucherDTO> vouchers;
    private List<ReviewViewDTO> reviews;
}
