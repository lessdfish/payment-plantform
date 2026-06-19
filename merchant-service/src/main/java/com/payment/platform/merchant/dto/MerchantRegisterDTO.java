package com.payment.platform.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户入驻请求 DTO。
 */
@Data
@Schema(description = "商户入驻请求")
public class MerchantRegisterDTO {

    /** 商户名称 */
    @NotBlank(message = "商户名称不能为空")
    @Schema(description = "商户名称", example = "小明科技")
    private String merchantName;

    /** 联系人邮箱 */
    @Schema(description = "联系人邮箱", example = "admin@xiaoming.com")
    private String contactEmail;
}
