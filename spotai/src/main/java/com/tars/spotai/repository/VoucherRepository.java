package com.tars.spotai.repository;

import com.tars.spotai.dto.SeckillVoucherDTO;
import com.tars.spotai.dto.UserVoucherDTO;
import com.tars.spotai.dto.VoucherDTO;
import com.tars.spotai.dto.VoucherActivityDTO;
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
import java.util.ArrayList;
import java.util.List;

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

    public List<VoucherActivityDTO> findAvailableActivities(LocalDateTime now, int limit) {
        List<VoucherActivityDTO> activities = new ArrayList<>();
        for (int shard = 0; shard < 2; shard++) {
            activities.addAll(jdbcTemplate.query(
                    """
                            select v.id, v.shop_id, s.name as shop_name, v.title, v.sub_title, v.rules,
                                   v.pay_value, v.actual_value, v.type, v.status,
                                   sv.init_stock, sv.stock, sv.begin_time, sv.end_time
                            from tb_voucher_%d v
                            left join tb_seckill_voucher_%d sv on sv.voucher_id = v.id
                            left join tb_shop s on s.id = v.shop_id
                            where v.status = 1
                              and (
                                v.type = 0
                                or (v.type = 1 and sv.end_time >= ?)
                              )
                            order by
                              case
                                when v.type = 1 and sv.begin_time <= ? and sv.end_time >= ? then 0
                                when v.type = 1 and sv.begin_time > ? then 1
                                else 2
                              end,
                              sv.begin_time asc,
                              v.create_time desc
                            limit ?
                            """.formatted(shard, shard),
                    new VoucherActivityRowMapper(),
                    now,
                    now,
                    now,
                    now,
                    Math.max(1, Math.min(limit, 50))
            ));
        }
        return activities.stream()
                .sorted((left, right) -> {
                    int leftRank = activityRank(left, now);
                    int rightRank = activityRank(right, now);
                    if (leftRank != rightRank) {
                        return Integer.compare(leftRank, rightRank);
                    }
                    LocalDateTime leftTime = left.getBeginTime() == null ? LocalDateTime.MAX : left.getBeginTime();
                    LocalDateTime rightTime = right.getBeginTime() == null ? LocalDateTime.MAX : right.getBeginTime();
                    return leftTime.compareTo(rightTime);
                })
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
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

    public List<UserVoucherDTO> findOrdersByUserId(Long userId, int limit) {
        return jdbcTemplate.query(
                """
                        select id, voucher_id, status, create_time
                        from %s
                        where user_id = ?
                        order by create_time desc, id desc
                        limit ?
                        """.formatted(ShardUtils.voucherOrderTable(userId)),
                (rs, rowNum) -> {
                    UserVoucherDTO dto = new UserVoucherDTO();
                    dto.setOrderId(rs.getLong("id"));
                    dto.setVoucherId(rs.getLong("voucher_id"));
                    dto.setStatus(rs.getObject("status", Integer.class));
                    dto.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                    return dto;
                },
                userId,
                Math.max(1, Math.min(limit, 50))
        );
    }

    public int countOrdersByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from %s
                        where user_id = ?
                        """.formatted(ShardUtils.voucherOrderTable(userId)),
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
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

    private static class VoucherActivityRowMapper implements RowMapper<VoucherActivityDTO> {
        @Override
        public VoucherActivityDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            VoucherActivityDTO activity = new VoucherActivityDTO();
            activity.setId(rs.getLong("id"));
            activity.setShopId(rs.getLong("shop_id"));
            activity.setShopName(rs.getString("shop_name"));
            activity.setTitle(rs.getString("title"));
            activity.setSubTitle(rs.getString("sub_title"));
            activity.setRules(rs.getString("rules"));
            activity.setPayValue(rs.getLong("pay_value"));
            activity.setActualValue(rs.getLong("actual_value"));
            activity.setType(rs.getInt("type"));
            activity.setStatus(rs.getInt("status"));
            activity.setInitStock(rs.getObject("init_stock", Integer.class));
            activity.setStock(rs.getObject("stock", Integer.class));
            activity.setBeginTime(toLocalDateTime(rs.getTimestamp("begin_time")));
            activity.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
            return activity;
        }
    }

    private static int activityRank(VoucherActivityDTO activity, LocalDateTime now) {
        if (activity.getType() != null && activity.getType() == 1) {
            if (activity.getBeginTime() != null && activity.getEndTime() != null
                    && !now.isBefore(activity.getBeginTime()) && !now.isAfter(activity.getEndTime())) {
                return 0;
            }
            if (activity.getBeginTime() != null && now.isBefore(activity.getBeginTime())) {
                return 1;
            }
        }
        return 2;
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
