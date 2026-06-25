package com.payment.platform.common.dto.event;

import com.payment.platform.common.dto.request.PayRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 已受理支付事件。网关完成验签、风控和幂等占位后写入 RocketMQ。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayProcessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String outTradeNo;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
    private String notifyUrl;
    private String subject;

    public static PayProcessEvent from(PayRequest request) {
        return PayProcessEvent.builder()
                .outTradeNo(request.getOutTradeNo())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .notifyUrl(request.getNotifyUrl())
                .subject(request.getSubject())
                .build();
    }

    public PayRequest toRequest() {
        return new PayRequest(
                outTradeNo, merchantId, amount, currency, notifyUrl, subject);
    }
}
