package com.karaoke.lounge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结账结果。前端据此同时核对消费单状态、扣款流水和会员最新余额。
 */
public record SettlementResult(
        Long bookingId,
        String bookingStatus,
        BigDecimal totalAmount,
        String paymentMethod,
        LocalDateTime settledAt,
        Long memberId,
        Long transactionId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter
) {
}
