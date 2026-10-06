package com.karaoke.lounge.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.dto.MemberDetail;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;
import com.karaoke.lounge.service.MemberAccountService;
import com.karaoke.lounge.service.MemberService;

/** 会员接口 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final MemberAccountService memberAccountService;

    public MemberController(MemberService memberService,
                            MemberAccountService memberAccountService) {
        this.memberService = memberService;
        this.memberAccountService = memberAccountService;
    }

    @GetMapping
    public ApiResponse<List<Member>> list(@RequestParam(required = false) String level) {
        return ApiResponse.success(memberService.list(level));
    }

    @GetMapping("/{id}")
    public ApiResponse<Member> get(@PathVariable Long id) {
        return ApiResponse.success(memberService.get(id));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<MemberDetail> getDetail(@PathVariable Long id) {
        return ApiResponse.success(memberService.getDetail(id));
    }

    @PostMapping("/{id}/recharge")
    public ApiResponse<MemberTransaction> recharge(@PathVariable Long id,
                                                   @RequestParam BigDecimal amount,
                                                   @RequestParam(required = false) String remark) {
        MemberTransaction tx = memberAccountService.recharge(id, amount, remark);
        return ApiResponse.success("储值成功，当前余额 " + tx.getBalanceAfter() + " 元", tx);
    }

    @PostMapping
    public ApiResponse<Member> create(@RequestBody Member input) {
        return ApiResponse.success("会员已建档", memberService.create(input));
    }

    @PutMapping("/{id}/level")
    public ApiResponse<Member> updateLevel(@PathVariable Long id, @RequestParam String level) {
        return ApiResponse.success("会员等级已更新", memberService.updateLevel(id, level));
    }
}
