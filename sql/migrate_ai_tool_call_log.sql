create table if not exists tb_ai_tool_call_log_0 (
  id bigint not null comment 'primary key',
  user_id bigint unsigned default null comment 'user id',
  session_id varchar(64) default null comment 'AI session id',
  tool_name varchar(100) not null comment 'tool name',
  risk_level varchar(20) not null comment 'low, medium, high',
  target_type varchar(50) default null comment 'business target type',
  target_id bigint default null comment 'business target id',
  tool_input json default null comment 'tool input',
  tool_output json default null comment 'tool output',
  status varchar(20) not null comment 'pending, confirmed, success, failed, rejected',
  confirm_required tinyint unsigned not null default 0 comment 'whether confirmation is required',
  confirm_token varchar(128) default null comment 'confirmation token',
  error_message varchar(1024) default null comment 'error message',
  create_time timestamp not null default current_timestamp comment 'create time',
  update_time timestamp not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  key idx_user_time (user_id, create_time),
  key idx_session_time (session_id, create_time),
  key idx_tool_name (tool_name),
  key idx_confirm_token (confirm_token)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_general_ci comment='AI tool call log table';

create table if not exists tb_ai_tool_call_log_1 like tb_ai_tool_call_log_0;
