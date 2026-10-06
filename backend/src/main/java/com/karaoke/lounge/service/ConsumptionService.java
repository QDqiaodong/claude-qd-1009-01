package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.dto.SettlementResult;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.ConsumptionRepository;
import com.karaoke.lounge.repository.MemberRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 消费业务。
 *
 * <p>规则：① 消费必须挂在有效预订上；② 金额须 &gt; 0；
 * ③ 结账开单时，该预订合计消费须达到包厢最低消费，否则拒绝开单。</p>
 */
@Service
public class ConsumptionService {

    private final ConsumptionRepository consumptionRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final MemberAccountService memberAccountService;

    public ConsumptionService(ConsumptionRepository consumptionRepository,
                              BookingRepository bookingRepository,
                              RoomRepository roomRepository,
                              MemberRepository memberRepository,
                              MemberAccountService memberAccountService) {
        this.consumptionRepository = consumptionRepository;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.memberAccountService = memberAccountService;
    }

    @Transactional(readOnly = true)
    public List<Consumption> listByBooking(Long bookingId) {
        if (bookingId == null) {
            return consumptionRepository.findAll();
        }
        return consumptionRepository.findByBookingId(bookingId);
    }

    @Transactional
    public Consumption create(Consumption input) {
        if (input.getBookingId() == null) {
            throw new BizException("消费必须挂在预订上");
        }
        // 锁住消费单：结账进行中不能再插入新的挂单明细，结账完成后再挂单也会读到「已完成」并拒绝。
        Booking booking = bookingRepository.findWithPessimisticLockById(input.getBookingId())
                .orElseThrow(() -> new BizException("预订不存在：" + input.getBookingId()));
        if ("已取消".equals(booking.getStatus())) {
            throw new BizException("已取消的预订不能挂消费");
        }
        if ("已完成".equals(booking.getStatus())) {
            throw new BizException("该消费单已结账，不能继续挂消费");
        }
        if (input.getItemName() == null || input.getItemName().isBlank()) {
            throw new BizException("消费项目名称不能为空");
        }
        if (input.getAmount() == null || input.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("消费金额必须大于 0");
        }
        Consumption c = new Consumption();
        c.setBookingId(input.getBookingId());
        c.setItemName(input.getItemName().trim());
        c.setAmount(input.getAmount());
        c.setAt(LocalDateTime.now());
        return consumptionRepository.save(c);
    }

    /**
     * 结账：最低消费校验通过后，在同一数据库事务内更新消费单、会员余额和资金流水。
     *
     * <p>取舍：宁可让前台等待行锁和事务提交，也不做「先扣余额、稍后补消费单/流水」的快速结算。
     * 只有 paymentMethod=储值余额 时才校验并扣减余额；现金/银行卡只记录结账方式。
     * 余额不足或后续任一步失败，整个事务回滚，消费单仍为未结账、余额不变、不留下失败扣款流水。</p>
     */
    @Transactional
    public SettlementResult settle(Long bookingId, String inputPaymentMethod) {
        Booking booking = bookingRepository.findWithPessimisticLockById(bookingId)
                .orElseThrow(() -> new BizException("预订不存在：" + bookingId));
        if ("已取消".equals(booking.getStatus())) {
            throw new BizException("已取消的预订无法结账");
        }
        if ("已完成".equals(booking.getStatus())) {
            throw new BizException("该消费单已结账，请勿重复结账");
        }

        String paymentMethod = (inputPaymentMethod == null || inputPaymentMethod.isBlank())
                ? Dict.PAYMENT_CASH : inputPaymentMethod.trim();
        if (!Dict.isValidPaymentMethod(paymentMethod)) {
            throw new BizException("结账方式不合法：" + paymentMethod + "，应为 " + Dict.PAYMENT_METHODS);
        }

        BigDecimal total = consumptionRepository.sumAmountByBookingId(bookingId);
        BigDecimal minSpend = booking.getRoom().getMinSpend();
        if (total.compareTo(minSpend) < 0) {
            throw new BizException("包厢 " + booking.getRoom().getRoomName()
                    + " 最低消费 " + minSpend + " 元，当前消费合计 " + total + " 元，未达标无法开单结账");
        }

        LocalDateTime settledAt = LocalDateTime.now();
        MemberTransaction transaction = null;
        if (Dict.PAYMENT_BALANCE.equals(paymentMethod)) {
            // 先锁定会员行并完成余额扣减/流水写入；若余额不足会抛异常，下面消费单状态更新不会执行。
            transaction = memberAccountService.deductForBooking(booking.getMemberId(), bookingId, total);
        }

        booking.setStatus("已完成");
        booking.setTotalAmount(total);
        booking.setPaymentMethod(paymentMethod);
        booking.setSettledAt(settledAt);
        bookingRepository.save(booking);

        booking.getRoom().setStatus("使用中");
        roomRepository.save(booking.getRoom());

        Member member = memberRepository.findById(booking.getMemberId())
                .orElseThrow(() -> new BizException("会员不存在：" + booking.getMemberId()));
        return new SettlementResult(
                booking.getId(),
                booking.getStatus(),
                total,
                paymentMethod,
                settledAt,
                member.getId(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getBalanceBefore(),
                transaction == null ? member.getBalance() : transaction.getBalanceAfter()
        );
    }
}
