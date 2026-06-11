package com.tars.spotai.repository;

import com.tars.spotai.dto.SeckillVoucherDTO;
import com.tars.spotai.dto.VoucherDTO;
import com.tars.spotai.entity.SeckillVoucher;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.utils.ShardUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class VoucherRepository {
    private final JdbcTemplate jdbcTemplate;

    public VoucherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertVoucher(Long voucherId, VoucherDTO dto, int type) {
        String table = ShardUtils.voucherTable(voucherId);
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(table),
                voucherId,
                dto.getShopId(),
                dto.getTitle(),
                dto.getSubTitle(),
                dto.getRules(),
                dto.getPayValue(),
                dto.getActualValue(),
                type,
                dto.getStatus() == null ? 1 : dto.getStatus()
        );
    }

    public void insertSeckillVoucher(Long id, Long voucherId, SeckillVoucherDTO dto) {
        String table = ShardUtils.seckillVoucherTable(voucherId);
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, voucher_id, init_stock, stock, allowed_levels, min_level, begin_time, end_time)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(table),
                id,
                voucherId,
                dto.getStock(),
                dto.getStock(),
                dto.getAllowedLevels(),
                dto.getMinLevel(),
                dto.getBeginTime(),
                dto.getEndTime()
        );
    }

    public Voucher findVoucherById(Long voucherId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, shop_id, title, sub_title, rules, pay_value, actual_value,
                                   type, status, create_time, update_time
                            from %s
                            where id = ?
                            """.formatted(ShardUtils.voucherTable(voucherId)),
                    new VoucherRowMapper(),
                    voucherId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public SeckillVoucher findSeckillVoucherByVoucherId(Long voucherId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, voucher_id, init_stock, stock, allowed_levels, min_level,
                                   create_time, begin_time, end_time, update_time
                            from %s
                            where voucher_id = ?
                            """.formatted(ShardUtils.seckillVoucherTable(voucherId)),
                    new SeckillVoucherRowMapper(),
                    voucherId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean existsOrder(Long userId, Long voucherId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from %s
                        where user_id = ? and voucher_id = ?
                        """.formatted(ShardUtils.voucherOrderTable(userId)),
                Integer.class,
                userId,
                voucherId
        );
        return count != null && count > 0;
    }

    public int deductStock(Long voucherId) {
        return jdbcTemplate.update(
                """
                        update %s
                        set stock = stock - 1
                        where voucher_id = ? and stock > 0
                        """.formatted(ShardUtils.seckillVoucherTable(voucherId)),
                voucherId
        );
    }

    public Integer queryStock(Long voucherId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select stock
                            from %s
                            where voucher_id = ?
                            """.formatted(ShardUtils.seckillVoucherTable(voucherId)),
                    Integer.class,
                    voucherId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void insertVoucherOrder(Long orderId, Long userId, Long voucherId) {
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, user_id, voucher_id, pay_type, status, reconciliation_status)
                        values (?, ?, ?, 1, 1, 1)
                        """.formatted(ShardUtils.voucherOrderTable(userId)),
                orderId,
                userId,
                voucherId
        );
    }

    public void insertVoucherOrderRouter(Long id, Long orderId, Long userId, Long voucherId) {
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, order_id, user_id, voucher_id)
                        values (?, ?, ?, ?)
                        """.formatted(ShardUtils.voucherOrderRouterTable(orderId)),
                id,
                orderId,
                userId,
                voucherId
        );
    }

    public void insertReconcileLog(Long id,
                                   Long orderId,
                                   Long userId,
                                   Long voucherId,
                                   String messageId,
                                   Integer beforeQty,
                                   Integer changeQty,
                                   Integer afterQty,
                                   Long traceId) {
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, order_id, user_id, voucher_id, message_id, detail,
                             before_qty, change_qty, after_qty, trace_id, log_type,
                             business_type, reconciliation_status)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, -1, 1, 1)
                        """.formatted(ShardUtils.voucherReconcileLogTable(orderId)),
                id,
                orderId,
                userId,
                voucherId,
                messageId,
                "创建秒杀订单成功",
                beforeQty,
                changeQty,
                afterQty,
                traceId
        );
    }

    public void insertRollbackFailureLog(Long id,
                                         Long voucherId,
                                         Long userId,
                                         Long orderId,
                                         Long traceId,
                                         String detail,
                                         Integer resultCode,
                                         String source) {
        jdbcTemplate.update(
                """
                        insert into tb_rollback_failure_log
                            (id, voucher_id, user_id, order_id, trace_id, detail,
                             result_code, retry_attempts, source)
                        values (?, ?, ?, ?, ?, ?, ?, 0, ?)
                        """,
                id,
                voucherId,
                userId,
                orderId,
                traceId,
                detail,
                resultCode,
                source
        );
    }

    private static class VoucherRowMapper implements RowMapper<Voucher> {
        @Override
        public Voucher mapRow(ResultSet rs, int rowNum) throws SQLException {
            Voucher voucher = new Voucher();
            voucher.setId(rs.getLong("id"));
            voucher.setShopId(rs.getLong("shop_id"));
            voucher.setTitle(rs.getString("title"));
            voucher.setSubTitle(rs.getString("sub_title"));
            voucher.setRules(rs.getString("rules"));
            voucher.setPayValue(rs.getLong("pay_value"));
            voucher.setActualValue(rs.getLong("actual_value"));
            voucher.setType(rs.getInt("type"));
            voucher.setStatus(rs.getInt("status"));
            voucher.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            voucher.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return voucher;
        }
    }

    private static class SeckillVoucherRowMapper implements RowMapper<SeckillVoucher> {
        @Override
        public SeckillVoucher mapRow(ResultSet rs, int rowNum) throws SQLException {
            SeckillVoucher voucher = new SeckillVoucher();
            voucher.setId(rs.getLong("id"));
            voucher.setVoucherId(rs.getLong("voucher_id"));
            voucher.setInitStock(rs.getInt("init_stock"));
            voucher.setStock(rs.getInt("stock"));
            voucher.setAllowedLevels(rs.getString("allowed_levels"));
            voucher.setMinLevel(rs.getObject("min_level", Integer.class));
            voucher.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            voucher.setBeginTime(toLocalDateTime(rs.getTimestamp("begin_time")));
            voucher.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
            voucher.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return voucher;
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
