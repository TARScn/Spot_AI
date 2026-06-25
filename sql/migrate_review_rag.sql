alter table tb_review_embedding
    add column embedding_json json null comment 'persisted embedding vector' after embedding_model,
    add column redis_indexed tinyint unsigned not null default 0 comment '0 pending, 1 indexed' after embedding_json;

update tb_review_embedding
set vector_store = 'redis-stack',
    redis_indexed = 0
where status = 1;
