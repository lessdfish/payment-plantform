package com.payment.platform.account.controller;

import com.payment.platform.common.dto.request.CancelRequest;
import com.payment.platform.common.dto.request.ConfirmRequest;
import com.payment.platform.common.dto.request.TryRequest;
import com.payment.platform.common.dto.response.TryResponse;
import com.payment.platform.account.service.TccService;
import com.payment.platform.common.result.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TCC Controller — 供支付网关调用 Try/Confirm/Cancel。
 */
@RestController
@RequestMapping("/api/v1/account/tcc")
@RequiredArgsConstructor
public class TccController {

    private final TccService tccService;

    /** TCC Try：冻结余额 */
    @PostMapping("/try")
    public ApiResult<TryResponse> tryFreeze(@Valid @RequestBody TryRequest request) {
        return ApiResult.success(tccService.tryFreeze(request));
    }

    /** TCC Confirm：确认扣款 */
    @PostMapping("/confirm")
    public ApiResult<Void> confirm(@Valid @RequestBody ConfirmRequest request) {
        tccService.confirm(request);
        return ApiResult.success();
    }

    /** TCC Cancel：释放冻结 */
    @PostMapping("/cancel")
    public ApiResult<Void> cancel(@Valid @RequestBody CancelRequest request) {
        tccService.cancel(request);
        return ApiResult.success();
    }
}
