package jmeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.util.RsaSignUtil;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * JMeter 压测前置准备：注册商户 + 生成密钥 + 充值 + 打印参数。
 *
 * <p>运行方式（需要先启动 merchant-service 8085, account-service 8081）：</p>
 * <pre>
 *   cd F:/test_file/NoIdea
 *   set JAVA_HOME=F:/Java/java21
 *   mvn exec:java -pl payment-common -Dexec.mainClass="jmeter.SetupJMeterTest"
 * </pre>
 *
 * <p>或者直接在 IDEA 中复制此文件到 src/test/java 下运行。</p>
 *
 * <p>运行后，将打印的 MERCHANT_ID 和 PRIVATE_KEY 填入 JMeter 的 User Defined Variables 中。</p>
 */
public class SetupJMeterTest {

    private static final RestClient CLIENT = RestClient.create();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MERCHANT_URL = "http://localhost:18085/api/v1/merchant";
    private static final String ACCOUNT_URL = "http://localhost:18081/api/v1/account";

    public static void main(String[] args) throws Exception {
        // 1. 注册商户
        String regResp = CLIENT.post()
                .uri(MERCHANT_URL + "/register")
                .header("Content-Type", "application/json")
                .body("{\"merchantName\":\"JMeter压测商户\",\"contactEmail\":\"jmeter@test.com\"}")
                .retrieve().body(String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> regData = (Map<String, Object>) MAPPER.readValue(regResp, Map.class).get("data");
        Long merchantId = Long.valueOf(regData.get("id").toString());
        System.out.println("MERCHANT_ID=" + merchantId);

        // 2. 生成 RSA 密钥对
        String keyResp = CLIENT.post()
                .uri(MERCHANT_URL + "/" + merchantId + "/key/generate")
                .retrieve().body(String.class);
        Map<String, Object> keyData = (Map<String, Object>) MAPPER.readValue(keyResp, Map.class).get("data");
        String privateKey = (String) keyData.get("privateKey");
        String publicKey = (String) keyData.get("publicKey");
        System.out.println("PRIVATE_KEY=" + privateKey);
        System.out.println("PUBLIC_KEY=" + publicKey);

        // 3. 配置微信费率
        CLIENT.post().uri(MERCHANT_URL + "/" + merchantId + "/rate")
                .header("Content-Type", "application/json")
                .body("{\"channelType\":\"WECHAT\",\"feeRate\":0.0038}")
                .retrieve().toBodilessEntity();
        System.out.println("费率已配置: WECHAT=0.38%");

        // 4. 充值 100 万（压测用）
        CLIENT.post().uri(ACCOUNT_URL + "/recharge/" + merchantId
                + "?amount=1000000.00&outTradeNo=JMETER_RECHARGE_" + System.currentTimeMillis())
                .retrieve().toBodilessEntity();
        System.out.println("已充值 100 万元");

        // 5. 打印填入说明
        System.out.println("\n========================================");
        System.out.println("请将以下值填入 JMeter 的 User Defined Variables:");
        System.out.println("  MERCHANT_ID = " + merchantId);
        System.out.println("  PRIVATE_KEY = " + privateKey);
        System.out.println("========================================");
    }
}
