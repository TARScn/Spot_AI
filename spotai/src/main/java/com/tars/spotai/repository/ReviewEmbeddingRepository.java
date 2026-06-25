package com.tars.spotai.repository;

import com.tars.spotai.entity.Review;
import com.tars.spotai.entity.ReviewEmbedding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReviewEmbeddingRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReviewEmbedding findByReviewId(Long reviewId) {
        List<ReviewEmbedding> rows = jdbcTemplate.query(
                """
                        select review_id, shop_id, chunk_text, embedding_id, embedding_model,
                               embedding_json, redis_indexed
                        from tb_review_embedding
                        where review_id = ? and chunk_index = 0 and status = 1
                        limit 1
                        """,
                (rs, rowNum) -> {
                    ReviewEmbedding embedding = new ReviewEmbedding();
                    embedding.setReviewId(rs.getLong("review_id"));
                    embedding.setShopId(rs.getLong("shop_id"));
                    embedding.setChunkText(rs.getString("chunk_text"));
                    embedding.setEmbeddingId(rs.getString("embedding_id"));
                    embedding.setEmbeddingModel(rs.getString("embedding_model"));
                    embedding.setEmbeddingJson(rs.getString("embedding_json"));
                    embedding.setRedisIndexed(rs.getInt("redis_indexed") == 1);
                    return embedding;
                },
                reviewId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsertEmbedding(Review review,
                                String embeddingId,
                                String embeddingModel,
                                String embeddingJson) {
        jdbcTemplate.update(
                """
                        insert into tb_review_embedding
                            (id, review_id, shop_id, chunk_index, chunk_text, vector_store,
                             embedding_id, embedding_model, embedding_json, redis_indexed, status)
                        values (?, ?, ?, 0, ?, 'redis-stack', ?, ?, ?, 0, 1)
                        on duplicate key update
                            shop_id = values(shop_id),
                            chunk_text = values(chunk_text),
                            vector_store = values(vector_store),
                            embedding_id = values(embedding_id),
                            embedding_model = values(embedding_model),
                            embedding_json = values(embedding_json),
                            redis_indexed = 0,
                            status = 1
                        """,
                review.getId(),
                review.getId(),
                review.getShopId(),
                review.getContent(),
                embeddingId,
                embeddingModel,
                embeddingJson
        );
    }

    public void markRedisIndexed(Long reviewId) {
        jdbcTemplate.update(
                "update tb_review_embedding set redis_indexed = 1 where review_id = ? and chunk_index = 0",
                reviewId
        );
    }

    public void markShopPending(Long shopId) {
        jdbcTemplate.update(
                "update tb_review_embedding set redis_indexed = 0 where shop_id = ? and status = 1",
                shopId
        );
    }

    public List<String> findEmbeddingIdsByShopId(Long shopId) {
        return jdbcTemplate.query(
                "select embedding_id from tb_review_embedding where shop_id = ? and embedding_id is not null",
                (rs, rowNum) -> rs.getString("embedding_id"),
                shopId
        );
    }
}
