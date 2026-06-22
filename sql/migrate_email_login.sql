USE `spotai_0`;

ALTER TABLE `tb_user_0`
    CHANGE COLUMN `phone` `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邮箱地址',
    DROP INDEX `uniqe_key_phone`,
    ADD UNIQUE KEY `unique_key_email` (`email`) USING BTREE;

ALTER TABLE `tb_user_1`
    CHANGE COLUMN `phone` `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邮箱地址',
    DROP INDEX `uniqe_key_phone`,
    ADD UNIQUE KEY `unique_key_email` (`email`) USING BTREE;

RENAME TABLE `tb_user_phone_0` TO `tb_user_email_0`;
RENAME TABLE `tb_user_phone_1` TO `tb_user_email_1`;

ALTER TABLE `tb_user_email_0`
    CHANGE COLUMN `phone` `email` varchar(255) NOT NULL COMMENT '邮箱地址',
    DROP INDEX `phone_idx`,
    ADD KEY `email_idx` (`email`) USING BTREE;

ALTER TABLE `tb_user_email_1`
    CHANGE COLUMN `phone` `email` varchar(255) NOT NULL COMMENT '邮箱地址',
    DROP INDEX `phone_idx`,
    ADD KEY `email_idx` (`email`) USING BTREE;

-- 旧手机号数据无法自动还原成真实邮箱，请按需要手动绑定。
-- 示例：
-- UPDATE tb_user_0 SET email = 'user@example.com' WHERE id = 1;
-- UPDATE tb_user_email_0 SET email = 'user@example.com' WHERE user_id = 1;
