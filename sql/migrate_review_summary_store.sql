CREATE TABLE IF NOT EXISTS `tb_review_summary` (
    `shop_id` bigint unsigned NOT NULL COMMENT 'shop id',
    `status` varchar(32) NOT NULL COMMENT 'READY, STALE, BUILDING, INSUFFICIENT_REVIEWS, UNAVAILABLE',
    `summary` text DEFAULT NULL COMMENT 'AI review summary',
    `highlights_json` json DEFAULT NULL COMMENT 'positive tags',
    `weaknesses_json` json DEFAULT NULL COMMENT 'negative tags',
    `scenes_json` json DEFAULT NULL COMMENT 'suitable scenes',
    `review_count` int unsigned NOT NULL DEFAULT '0' COMMENT 'review count used by summary',
    `version` int unsigned NOT NULL DEFAULT '1' COMMENT 'incremented when reviews change',
    `generated_at` timestamp NULL DEFAULT NULL COMMENT 'summary generated time',
    `expire_at` timestamp NULL DEFAULT NULL COMMENT 'summary expiry time',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`shop_id`) USING BTREE,
    KEY `idx_status_update` (`status`, `update_time`) USING BTREE,
    KEY `idx_expire_at` (`expire_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI review summary store';
