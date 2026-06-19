package com.payment.platform.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商户入驻请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "商户入驻请求")
public class MerchantRegisterRequest {

    /** 商户名称 */
    @NotBlank(message = "商户名称不能为空")
    @Schema(description = "商户名称", example = "小明科技")
    private String merchantName;

    /** 联系人邮箱（用于通知） */
    @Schema(description = "联系人邮箱", example = "admin@xiaoming.com")
    private String contactEmail;
}
