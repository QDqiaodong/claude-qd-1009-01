package com.karaoke.lounge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;

/** 会员详情：当前余额、已结账消费、逐笔储值/扣款流水分区展示。 */
public record MemberDetail(
        Long id,
        String memberNo,
        String memberName,
        String phone,
        String level,
        BigDecimal balance,
        List<SettledBooking> settledBookings,
        List<TransactionView> transactions
) {

    public record SettledBooking(
            Long bookingId,
            BigDecimal totalAmount,
            String paymentMethod,
            LocalDateTime settledAt
    ) {
        public static SettledBooking from(Booking b) {
            return new SettledBooking(b.getId(), b.getTotalAmount(), b.getPaymentMethod(), b.getSettledAt());
        }
    }

    public record TransactionView(
            Long id,
            String type,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            Long bookingId,
            String remark,
            LocalDateTime createdAt
    ) {
        public static TransactionView from(MemberTransaction t) {
            return new TransactionView(t.getId(), t.getType(), t.getAmount(), t.getBalanceBefore(),
                    t.getBalanceAfter(), t.getBookingId(), t.getRemark(), t.getCreatedAt());
        }
    }

    public static MemberDetail of(Member member, List<Booking> bookings, List<MemberTransaction> txs) {
        return new MemberDetail(
                member.getId(),
                member.getMemberNo(),
                member.getMemberName(),
                member.getPhone(),
                member.getLevel(),
                member.getBalance(),
                bookings.stream().map(SettledBooking::from).toList(),
                txs.stream().map(TransactionView::from).toList()
        );
    }
}
