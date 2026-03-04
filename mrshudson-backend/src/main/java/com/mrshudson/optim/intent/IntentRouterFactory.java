package com.mrshudson.optim.intent;

import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.intent.extract.ParameterExtractor;
import com.mrshudson.optim.intent.extract.RuleBasedExtractor;
import com.mrshudson.optim.intent.handler.AbstractIntentHandler;
import com.mrshudson.optim.intent.impl.IntentRouterImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 意图路由器工厂
 * 根据配置创建对应类型的路由器实例
 */
@Slf4j
@Configuration
public class IntentRouterFactory {

    /**
     * 路由模式常量
     */
    public static final String MODE_RULE_ONLY = "rule-only";
    public static final String MODE_HYBRID = "hybrid";
    public static final String MODE_AI_ONLY = "ai-only";

    private final OptimProperties optimProperties;
    private final List<AbstractIntentHandler> handlers;

    @Autowired
    public IntentRouterFactory(OptimProperties optimProperties, List<AbstractIntentHandler> handlers) {
        this.optimProperties = optimProperties;
        this.handlers = handlers;
    }

    /**
     * 创建意图路由器
     * 根据 optim.intent.router.mode 配置创建对应类型的路由器
     *
     * @return 意图路由器实例
     */
    @Bean
    public IntentRouter intentRouter() {
        String mode = getRouterMode();
        log.info("创建意图路由器，模式: {}", mode);

        IntentRouter router;

        switch (mode) {
            case MODE_RULE_ONLY:
                router = createRuleOnlyRouter();
                break;
            case MODE_HYBRID:
                router = createHybridRouter();
                break;
            case MODE_AI_ONLY:
                router = createAiOnlyRouter();
                break;
            default:
                log.warn("未知的路由模式: {}，使用默认的 hybrid 模式", mode);
                router = createHybridRouter();
        }

        // 注册所有处理器
        registerHandlers(router);

        log.info("意图路由器创建完成: {}，已注册 {} 个处理器",
                router.getName(), handlers != null ? handlers.size() : 0);

        return router;
    }

    /**
     * 创建纯规则路由器
     * 仅使用关键词匹配和规则引擎，不调用AI
     *
     * @return 规则路由器
     */
    private IntentRouter createRuleOnlyRouter() {
        log.info("创建纯规则意图路由器");
        ParameterExtractor extractor = new RuleBasedExtractor();
        return new IntentRouterImpl(extractor, MODE_RULE_ONLY, "RuleOnlyIntentRouter");
    }

    /**
     * 创建混合路由器
     * 结合规则匹配和轻量AI，置信度不足时升级到完整AI
     *
     * @return 混合路由器
     */
    private IntentRouter createHybridRouter() {
        log.info("创建混合意图路由器");
        // 当前实现使用规则路由器作为基础
        // 后续可以扩展为包含轻量AI层的混合路由器
        ParameterExtractor extractor = new RuleBasedExtractor();
        return new IntentRouterImpl(extractor, MODE_HYBRID, "HybridIntentRouter");
    }

    /**
     * 创建纯AI路由器
     * 所有查询都交给AI处理
     *
     * @return AI路由器
     */
    private IntentRouter createAiOnlyRouter() {
        log.info("创建纯AI意图路由器");
        // AI-only模式的路由器，所有查询都标记为需要AI处理
        return new IntentRouter() {
            @Override
            public IntentClassification classify(String query) {
                // AI-only模式下，所有查询都返回GENERAL_CHAT，由AI决定具体意图
                return IntentClassification.fullAi(IntentType.GENERAL_CHAT, 1.0);
            }

            @Override
            public RouteResult route(Long userId, String query) {
                // 直接返回需要AI处理的结果
                return RouteResult.needAi(IntentType.GENERAL_CHAT, 1.0);
            }

            @Override
            public void registerHandler(AbstractIntentHandler handler) {
                // AI-only模式下不需要注册处理器
                log.debug("AI-only模式忽略处理器注册: {}", handler.getClass().getSimpleName());
            }

            @Override
            public String getName() {
                return "AiOnlyIntentRouter";
            }

            @Override
            public String getMode() {
                return MODE_AI_ONLY;
            }
        };
    }

    /**
     * 注册所有意图处理器到路由器
     *
     * @param router 意图路由器
     */
    private void registerHandlers(IntentRouter router) {
        if (handlers == null || handlers.isEmpty()) {
            log.warn("没有找到意图处理器");
            return;
        }

        for (AbstractIntentHandler handler : handlers) {
            try {
                router.registerHandler(handler);
            } catch (Exception e) {
                log.error("注册处理器失败: {}", handler.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 获取当前配置的路由模式
     *
     * @return 路由模式
     */
    private String getRouterMode() {
        if (optimProperties == null || optimProperties.getIntentRouter() == null) {
            return MODE_HYBRID; // 默认混合模式
        }
        return optimProperties.getIntentRouter().getMode();
    }

    /**
     * 获取路由模式描述
     *
     * @param mode 路由模式
     * @return 模式描述
     */
    public static String getModeDescription(String mode) {
        switch (mode) {
            case MODE_RULE_ONLY:
                return "纯规则模式 - 仅使用关键词匹配，不调用AI";
            case MODE_HYBRID:
                return "混合模式 - 规则匹配 + 轻量AI + 完整AI降级";
            case MODE_AI_ONLY:
                return "纯AI模式 - 所有查询都交给AI处理";
            default:
                return "未知模式";
        }
    }

    /**
     * 检查是否为有效的路由模式
     *
     * @param mode 路由模式
     * @return 是否有效
     */
    public static boolean isValidMode(String mode) {
        return MODE_RULE_ONLY.equals(mode)
                || MODE_HYBRID.equals(mode)
                || MODE_AI_ONLY.equals(mode);
    }
}
