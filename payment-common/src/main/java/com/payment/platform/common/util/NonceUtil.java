package com.payment.platform.common.util;

import cn.hutool.core.util.IdUtil;

/**
 * 防重放攻击工具类。
 * <p>Nonce（随机字符串）由商户侧生成，每次请求唯一，用于防重放攻击。
 * 平台侧将 nonce 存入 Redis（TTL 5 分钟），重复出现则拒绝请求。</p>
 *
 * <p>Redis key 格式：nonce:{merchantId}:{nonce}</p>
 */
public final class NonceUtil {

    private NonceUtil() {}

    /**
     * 生成随机 nonce 字符串。
     * <p>使用 UUID 去掉横线，长度 32 字符，保证唯一性。</p>
     *
     * @return 32 位随机字符串
     */
    public static String generate() {
        return IdUtil.fastSimpleUUID();
    }
}
