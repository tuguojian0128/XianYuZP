package com.xianyusmart.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyKnowledgeBaseServiceTest {

    private final CopyKnowledgeBaseService service = new CopyKnowledgeBaseService();

    @Test
    void optimizesCopyWithoutExternalAi() {
        CopyKnowledgeBaseService.OptimizationResult result = service.optimize(
                "商品商品 绝对可靠稳赚不赔",
                "商品商品\n稳赚不赔，保证收益。"
        );

        assertTrue(result.free());
        assertTrue(result.title().length() <= 60);
        assertTrue(result.description().contains("【商品说明】"));
        assertTrue(result.description().contains("【交易提示】"));
        assertFalse(result.title().contains("稳赚不赔"));
        assertFalse(result.description().contains("保证收益"));
    }
}
