package com.payment.platform.notification.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.notification.entity.NotifyRecord;
import com.payment.platform.notification.repository.NotifyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotifyRecordRepository notifyRecordRepository;

    @GetMapping("/record/{outTradeNo}")
    public ApiResult<NotifyRecord> getRecord(@PathVariable String outTradeNo) {
        return ApiResult.success(
                notifyRecordRepository.findByOutTradeNoAndStatus(outTradeNo, "SUCCESS").orElse(null));
    }
}
