package com.tars.spotai.repository;

import com.tars.spotai.entity.Blog;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BlogRepository {
    private static final int PAGE_SIZE = 10;

    private final JdbcTemplate jdbcTemplate;

    public BlogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(Blog blog) {
        return jdbcTemplate.update(
                """
                        insert into tb_blog
                            (id, shop_id, user_id, title, images, content, liked, comments, create_time, update_time)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                blog.getId(),
                blog.getShopId(),
                blog.getUserId(),
                blog.getTitle(),
                blog.getImages(),
                blog.getContent(),
                blog.getLiked(),
                blog.getComments(),
                blog.getCreateTime(),
                blog.getUpdateTime()
        );
    }

    public Blog findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, shop_id, user_id, title, images, content, liked, comments, create_time, update_time
                            from tb_blog
                            where id = ?
                            """,
                    new BlogRowMapper(),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Blog> findHot(int current) {
        int offset = offset(current);
        return jdbcTemplate.query(
                """
                        select id, shop_id, user_id, title, images, content, liked, comments, create_time, update_time
                        from tb_blog
                        order by liked desc, create_time desc
                        limit ?, ?
                        """,
                new BlogRowMapper(),
                offset,
                PAGE_SIZE
        );
    }

    public List<Blog> findByUserId(Long userId, int current) {
        int offset = offset(current);
        return jdbcTemplate.query(
                """
                        select id, shop_id, user_id, title, images, content, liked, comments, create_time, update_time
                        from tb_blog
                        where user_id = ?
                        order by create_time desc
                        limit ?, ?
                        """,
                new BlogRowMapper(),
                userId,
                offset,
                PAGE_SIZE
        );
    }

    public int increaseLiked(Long id) {
        return jdbcTemplate.update("update tb_blog set liked = liked + 1 where id = ?", id);
    }

    public int decreaseLiked(Long id) {
        return jdbcTemplate.update("update tb_blog set liked = liked - 1 where id = ? and liked > 0", id);
    }

    private int offset(int current) {
        int page = Math.max(current, 1);
        return (page - 1) * PAGE_SIZE;
    }

    private static class BlogRowMapper implements RowMapper<Blog> {
        @Override
        public Blog mapRow(ResultSet rs, int rowNum) throws SQLException {
            Blog blog = new Blog();
            blog.setId(rs.getLong("id"));
            blog.setShopId(rs.getLong("shop_id"));
            blog.setUserId(rs.getLong("user_id"));
            blog.setTitle(rs.getString("title"));
            blog.setImages(rs.getString("images"));
            blog.setContent(rs.getString("content"));
            blog.setLiked(rs.getObject("liked", Integer.class));
            blog.setComments(rs.getObject("comments", Integer.class));
            blog.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            blog.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return blog;
        }

        private LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
