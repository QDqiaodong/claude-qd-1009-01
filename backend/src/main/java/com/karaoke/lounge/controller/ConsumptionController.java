package com.karaoke.lounge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.dto.SettlementResult;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.service.ConsumptionService;

/** 消费接口（含结账最低消费校验） */
@RestController
@RequestMapping("/api/consumptions")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping
    public ApiResponse<List<Consumption>> list(@RequestParam(required = false) Long bookingId) {
        return ApiResponse.success(consumptionService.listByBooking(bookingId));
    }

    @PostMapping
    public ApiResponse<Consumption> create(@RequestBody Consumption input) {
        return ApiResponse.success("消费已记录", consumptionService.create(input));
    }

    /** 结账开单：校验合计达到最低消费；选择储值余额时原子扣减余额并写资金流水 */
    @PostMapping("/settle")
    public ApiResponse<SettlementResult> settle(@RequestParam Long bookingId,
                                                @RequestParam(required = false) String paymentMethod) {
        SettlementResult result = consumptionService.settle(bookingId, paymentMethod);
        String message = Dict.PAYMENT_BALANCE.equals(result.paymentMethod())
                ? "储值余额结账成功，扣款 " + result.totalAmount()
                        + " 元，余额 " + result.balanceAfter() + " 元，流水#" + result.transactionId()
                : "结账成功，合计 " + result.totalAmount() + " 元（" + result.paymentMethod() + "）";
        return ApiResponse.success(message, result);
    }
}
