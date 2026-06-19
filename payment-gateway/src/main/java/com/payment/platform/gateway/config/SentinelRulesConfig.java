package com.payment.platform.gateway.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则初始化配置。
 * <p>服务启动时自动加载基础限流规则，后续可通过 Sentinel Dashboard 动态调整。</p>
 */
@Slf4j
@Configuration
public class SentinelRulesConfig {

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 支付接口总 QPS 限制：2000
        FlowRule payRule = new FlowRule();
        payRule.setResource("POST:/api/v1/pay/create");
        payRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        payRule.setCount(2000);
        rules.add(payRule);

        // 支付查询接口 QPS 限制：5000（查询压力远小于支付）
        FlowRule queryRule = new FlowRule();
        queryRule.setResource("GET:/api/v1/pay/query");
        queryRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        queryRule.setCount(5000);
        rules.add(queryRule);

        FlowRuleManager.loadRules(rules);
        log.info("[SENTINEL] 限流规则加载完成，共 {} 条", rules.size());
    }
}
