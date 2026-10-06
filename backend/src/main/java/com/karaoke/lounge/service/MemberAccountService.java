package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;
import com.karaoke.lounge.repository.MemberRepository;
import com.karaoke.lounge.repository.MemberTransactionRepository;

/**
 * 会员储值账户服务。
 *
 * <p>余额和只追加流水必须在同一事务内更新；行级悲观锁保证并发储值/扣款后的流水快照仍然连续。</p>
 */
@Service
public class MemberAccountService {

    private final MemberRepository memberRepository;
    private final MemberTransactionRepository transactionRepository;

    public MemberAccountService(MemberRepository memberRepository,
                                MemberTransactionRepository transactionRepository) {
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public MemberTransaction recharge(Long memberId, BigDecimal inputAmount, String inputRemark) {
        BigDecimal amount = normalizeAmount(inputAmount);
        Member member = getLockedMember(memberId);
        BigDecimal before = member.getBalance();
        BigDecimal after = before.add(amount);
        member.setBalance(after);
        memberRepository.save(member);
        return saveTransaction(memberId, "储值", amount, before, after, null,
                inputRemark == null || inputRemark.isBlank() ? "前台储值" : inputRemark.trim(), LocalDateTime.now());
    }

    /**
     * 储值余额扣款。余额不足直接抛业务异常；由外层结账事务回滚消费单状态，不保存失败流水。
     */
    @Transactional
    public MemberTransaction deductForBooking(Long memberId, Long bookingId, BigDecimal inputAmount) {
        BigDecimal amount = normalizeAmount(inputAmount);
        Member member = getLockedMember(memberId);
        BigDecimal before = member.getBalance();
        if (before.compareTo(amount) < 0) {
            throw new BizException("会员 " + member.getMemberName() + " 储值余额不足：当前余额 "
                    + before + " 元，本次需扣 " + amount + " 元");
        }
        BigDecimal after = before.subtract(amount);
        member.setBalance(after);
        memberRepository.save(member);
        return saveTransaction(memberId, "消费扣款", amount, before, after, bookingId,
                "储值余额结算：预订#" + bookingId + "消费合计", LocalDateTime.now());
    }

    private Member getLockedMember(Long memberId) {
        if (memberId == null) {
            throw new BizException("必须指定会员");
        }
        return memberRepository.findWithPessimisticLockById(memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + memberId));
    }

    private MemberTransaction saveTransaction(Long memberId, String type, BigDecimal amount,
                                              BigDecimal before, BigDecimal after, Long bookingId,
                                              String remark, LocalDateTime at) {
        MemberTransaction tx = new MemberTransaction();
        tx.setMemberId(memberId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setBookingId(bookingId);
        tx.setRemark(remark);
        tx.setCreatedAt(at);
        return transactionRepository.save(tx);
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("金额必须大于 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
