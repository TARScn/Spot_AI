alter table tb_ai_conversation_0
  add column metadata json null comment 'message metadata, for example used tools' after model_name;

alter table tb_ai_conversation_1
  add column metadata json null comment 'message metadata, for example used tools' after model_name;
