-- Drop legacy tables that are not referenced by the current backend code.
-- Keep this migration separate from the base dump so existing local databases
-- can be cleaned without rebuilding all seed data.

drop table if exists tb_blog_comments;
drop table if exists tb_sign;

drop table if exists tb_user_info_0;
drop table if exists tb_user_info_1;
drop table if exists tb_user_preference_0;
drop table if exists tb_user_preference_1;
drop table if exists tb_user_behavior_log_0;
drop table if exists tb_user_behavior_log_1;

drop table if exists tb_review_like;
drop table if exists tb_review_ai_analysis;

drop table if exists tb_ai_tool_confirm_0;
drop table if exists tb_ai_tool_confirm_1;
drop table if exists tb_ai_recommend_log_0;
drop table if exists tb_ai_recommend_log_1;
