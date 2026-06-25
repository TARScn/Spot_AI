create table if not exists tb_ai_user_memory_0 (
  id bigint not null,
  user_id bigint unsigned not null,
  memory_key varchar(128) not null,
  memory_type varchar(32) not null,
  memory_json json not null,
  confidence decimal(5,4) not null default 0.8000,
  source_message_id bigint null,
  source_agent varchar(64) not null default 'PreferenceExtractorAgent',
  status tinyint unsigned not null default 1,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp on update current_timestamp,
  primary key (id),
  unique key uk_user_memory_key (user_id, memory_key),
  key idx_user_type (user_id, memory_type),
  key idx_source_message (source_message_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_general_ci comment='AI 用户长期记忆表';

create table if not exists tb_ai_user_memory_1 (
  id bigint not null,
  user_id bigint unsigned not null,
  memory_key varchar(128) not null,
  memory_type varchar(32) not null,
  memory_json json not null,
  confidence decimal(5,4) not null default 0.8000,
  source_message_id bigint null,
  source_agent varchar(64) not null default 'PreferenceExtractorAgent',
  status tinyint unsigned not null default 1,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp on update current_timestamp,
  primary key (id),
  unique key uk_user_memory_key (user_id, memory_key),
  key idx_user_type (user_id, memory_type),
  key idx_source_message (source_message_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_general_ci comment='AI 用户长期记忆表';
