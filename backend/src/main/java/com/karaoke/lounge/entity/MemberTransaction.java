package com.karaoke.lounge.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会员资金流水（只追加）。
 *
 * <p>储值写入正向前账/后账；消费扣款只在储值余额结账时写入，且与消费单结账状态同事务提交。</p>
 */
@Entity
@Table(name = "member_transaction")
@Getter
@Setter
public class MemberTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 储值 / 消费扣款 */
    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** 发生额（正数）；扣款方向由 type 表达 */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    /** 扣款关联消费单；储值时为空 */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "remark", nullable = false, length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
