CREATE TABLE IF NOT EXISTS `tb_shop_item` (
    `id` bigint NOT NULL COMMENT '主键',
    `shop_id` bigint unsigned NOT NULL COMMENT '店铺id',
    `name` varchar(255) NOT NULL COMMENT '服务/菜品名称',
    `description` varchar(500) DEFAULT NULL COMMENT '简短描述',
    `price` bigint unsigned NOT NULL COMMENT '价格（分）',
    `sort` int unsigned DEFAULT 0 COMMENT '排序',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_shop_sort` (`shop_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='店铺服务/菜品表';
