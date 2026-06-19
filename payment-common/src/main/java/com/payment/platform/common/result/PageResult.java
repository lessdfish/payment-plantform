package com.payment.platform.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应体。
 * <p>用于需要分页的查询接口，如订单列表、流水列表等。</p>
 *
 * @param <T> 记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页记录列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int pageNum;

    /** 每页条数 */
    private int pageSize;
}
