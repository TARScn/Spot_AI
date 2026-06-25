package com.tars.spotai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

@Configuration
@ConditionalOnProperty(prefix = "spotai.ai.review-summary", name = "enabled", havingValue = "true")
public class ReviewAiConfig {

    @Bean(destroyMethod = "close")
    public JedisPooled reviewVectorJedis(ReviewAiProperties properties) {
        ReviewAiProperties.VectorRedis redis = properties.getVectorRedis();
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(redis.getTimeoutMillis())
                .socketTimeoutMillis(redis.getTimeoutMillis());
        if (StringUtils.hasText(redis.getPassword())) {
            config.password(redis.getPassword());
        }
        return new JedisPooled(new HostAndPort(redis.getHost(), redis.getPort()), config.build());
    }

    @Bean
    public VectorStore reviewVectorStore(JedisPooled reviewVectorJedis,
                                         EmbeddingModel embeddingModel,
                                         ReviewAiProperties properties) {
        return RedisVectorStore.builder(reviewVectorJedis, embeddingModel)
                .indexName(properties.getIndexName())
                .prefix(properties.getKeyPrefix())
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("reviewId"),
                        RedisVectorStore.MetadataField.tag("shopId"),
                        RedisVectorStore.MetadataField.numeric("score"),
                        RedisVectorStore.MetadataField.text("createTime")
                )
                .initializeSchema(true)
                .build();
    }
}
