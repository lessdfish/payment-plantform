package com.payment.platform.account.service;

import com.payment.platform.common.dto.request.CancelRequest;
import com.payment.platform.common.dto.request.ConfirmRequest;
import com.payment.platform.common.dto.request.TryRequest;
import com.payment.platform.common.dto.response.TryResponse;

/**
 * TCC 分布式事务服务。
 * <p>Try-Confirm-Cancel 三阶段协议，保证余额操作的最终一致性。</p>
 */
public interface TccService {

    /**
     * Try：冻结商户余额。
     * <p>SQL: UPDATE account SET frozen_amount = frozen_amount + ?
     * WHERE merchant_id = ? AND balance - frozen_amount >= ?</p>
     *
     * @return tccId + 冻结金额
     */
    TryResponse tryFreeze(TryRequest request);

    /**
     * Confirm：实扣冻结金额 + 生成复式记账流水。
     * <p>必须幂等：同一 tccId 重复调用直接返回成功。</p>
     */
    void confirm(ConfirmRequest request);

    /**
     * Cancel：释放冻结金额。
     * <p>必须幂等：同一 tccId 重复调用直接返回成功。</p>
     */
    void cancel(CancelRequest request);
}
