package com.payment.platform.common.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.asymmetric.Sign;
import cn.hutool.crypto.asymmetric.SignAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyPair;

/**
 * RSA 签名/验签工具类。
 * <p>商户私钥签名，平台公钥验签——微信/支付宝同款方案。</p>
 *
 * <p>签名串构建规则（按字典序）：</p>
 * <pre>method + "\n" + url + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n"</pre>
 */
@Slf4j
public final class RsaSignUtil {

    private RsaSignUtil() {}

    /**
     * 生成 RSA 密钥对（2048 位）。
     * <p>商户入驻时调用，私钥返回给商户，公钥保存到平台。</p>
     *
     * @return KeyPair 对象，包含公私钥
     */
    public static KeyPair generateKeyPair() {
        RSA rsa = new RSA();
        return new KeyPair(rsa.getPublicKey(), rsa.getPrivateKey());
    }

    /**
     * 使用商户私钥对签名串进行签名。
     * <p>商户侧调用，签名后放入请求 Header: X-Signature。</p>
     *
     * @param signContent 签名串（method + url + timestamp + nonce + body）
     * @param privateKey  商户 RSA 私钥（Base64 编码）
     * @return Base64 编码的签名结果
     */
    public static String sign(String signContent, String privateKey) {
        Sign sign = new Sign(SignAlgorithm.SHA256withRSA, privateKey, null);
        byte[] signed = sign.sign(signContent.getBytes());
        return Base64.encode(signed);
    }

    /**
     * 使用商户公钥验证签名。
     * <p>网关侧调用，验证商户请求是否被篡改。</p>
     *
     * @param signContent 签名串
     * @param signature   商户传入的签名（Base64 编码）
     * @param publicKey   商户 RSA 公钥（Base64 编码）
     * @return true 验签通过，false 验签失败
     */
    public static boolean verify(String signContent, String signature, String publicKey) {
        try {
            Sign sign = new Sign(SignAlgorithm.SHA256withRSA, null, publicKey);
            return sign.verify(
                    signContent.getBytes(),
                    Base64.decode(signature)
            );
        } catch (Exception e) {
            log.warn("[RSA] 验签异常", e);
            return false;
        }
    }

    /**
     * 将公钥编码为 Base64 字符串（方便存储和传输）。
     */
    public static String encodePublicKey(java.security.PublicKey publicKey) {
        return Base64.encode(publicKey.getEncoded());
    }

    /**
     * 将私钥编码为 Base64 字符串（方便存储和传输）。
     */
    public static String encodePrivateKey(java.security.PrivateKey privateKey) {
        return Base64.encode(privateKey.getEncoded());
    }
}
