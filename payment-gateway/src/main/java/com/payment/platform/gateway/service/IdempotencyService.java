package com.payment.platform.gateway.service;

/**
 * 幂等性校验服务接口。
 * <p>防止商户重复提交同一笔支付请求造成重复扣款。</p>
 */
public interface IdempotencyService {

    /**
     * 检查是否已处理过此订单号。
     * <p>使用 Redis 存储已处理的订单号，TTL 72 小时。</p>
     *
     * @param merchantId  商户 ID
     * @param outTradeNo  商户订单号
     * @return 已处理过的原结果（JSON），未处理返回 null
     */
    String check(Long merchantId, String outTradeNo);

    /**
     * 记录处理完毕的订单号及其结果。
     *
     * @param merchantId 商户 ID
     * @param outTradeNo 商户订单号
     * @param resultJson 处理结果（JSON）
     */
    void save(Long merchantId, String outTradeNo, String resultJson);
}
