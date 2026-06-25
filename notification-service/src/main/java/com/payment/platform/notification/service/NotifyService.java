package com.payment.platform.notification.service;

public interface NotifyService {
    void sendCallback(String outTradeNo, Long merchantId, String notifyUrl, String body);

    void retry(Long recordId);
}
