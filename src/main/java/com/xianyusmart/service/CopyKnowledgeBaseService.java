package com.xianyusmart.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 本地铺货文案知识库。
 *
 * <p>这里不调用外部模型，也不产生额外的 AI 用量。优化只基于内置的
 * 标题结构、风险词清理、重复内容压缩和详情模板规则，且不会凭空添加
 * 品牌、规格、收益、售后或库存等商品事实。</p>
 */
@Service
public class CopyKnowledgeBaseService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_DESCRIPTION_LENGTH = 3000;
    private static final Pattern REPEATED_TEXT = Pattern.compile("(.{2,20})\\1+");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s，。；;、|｜·•]+|[\\s，。；;、|｜·•]+$");

    /** 违反平台规范或容易造成误导的宣传词，只做删除，不替换成新的事实。 */
    private static final List<String> RISK_PHRASES = List.of(
            "稳赚不赔", "稳赚", "零风险", "保证收益", "保证赚钱", "躺赚", "暴富",
            "日赚", "月入", "年入", "回本神器", "绝对可靠", "百分百", "100%保证",
            "全网最低", "内部群", "内部渠道", "百亿补贴", "回血", "无脑", "不封号"
    );

    public OptimizationResult optimize(String title, String description) {
        String originalTitle = normalize(title);
        String originalDescription = normalize(description);
        if (originalTitle.isBlank()) {
            throw new IllegalArgumentException("商品标题不能为空");
        }

        String optimizedTitle = cleanText(originalTitle);
        optimizedTitle = compressRepeatedText(optimizedTitle);
        optimizedTitle = trimToLength(optimizedTitle, MAX_TITLE_LENGTH);
        if (optimizedTitle.isBlank()) {
            optimizedTitle = trimToLength(originalTitle, MAX_TITLE_LENGTH);
        }

        String sourceDescription = originalDescription.isBlank() ? originalTitle : originalDescription;
        String cleanedDescription = cleanText(sourceDescription);
        cleanedDescription = compressRepeatedLines(cleanedDescription);
        String optimizedDescription = buildDescription(optimizedTitle, cleanedDescription);
        optimizedDescription = trimToLength(optimizedDescription, MAX_DESCRIPTION_LENGTH);

        return new OptimizationResult(
                optimizedTitle,
                optimizedDescription,
                List.of("清理重复词", "移除夸大承诺词", "整理商品说明结构", "补充交易确认提示"),
                true
        );
    }

    /**
     * 发布前扫描高风险宣传词。命中时由发布流程阻止提交，要求用户先修改真实内容。
     */
    public List<String> detectRiskPhrases(String... values) {
        Set<String> found = new LinkedHashSet<>();
        for (String value : values) {
            String text = normalize(value);
            for (String phrase : RISK_PHRASES) {
                if (text.contains(phrase)) {
                    found.add(phrase);
                }
            }
        }
        return List.copyOf(found);
    }

    private String buildDescription(String title, String description) {
        StringBuilder result = new StringBuilder();
        result.append("【商品说明】\n").append(description.isBlank() ? title : description.trim());
        result.append("\n\n【交易提示】\n")
                .append("商品信息以实际页面展示为准，请在下单前确认商品内容、规格和交付方式。")
                .append("\n交付/发货按本商品配置执行，具体以双方沟通为准。");
        return result.toString();
    }

    private String cleanText(String value) {
        String cleaned = normalize(value);
        for (String phrase : RISK_PHRASES) {
            cleaned = cleaned.replace(phrase, "");
            cleaned = cleaned.replace(phrase.toLowerCase(Locale.ROOT), "");
        }
        cleaned = cleaned.replaceAll("(?i)\\b\\d+(?:[.-]\\d+)?\\s*[万千kK](?=\\s*(?:/月|每月|收益|回报|收入|买|赚))", "");
        cleaned = cleaned.replaceAll("[，。；;、]{2,}", "，");
        cleaned = compressRepeatedText(cleaned);
        cleaned = EDGE_PUNCTUATION.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    private String compressRepeatedLines(String value) {
        Set<String> unique = new LinkedHashSet<>();
        for (String line : value.split("\\n+")) {
            String normalized = line.trim();
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return String.join("\n", unique);
    }

    private String compressRepeatedText(String value) {
        String current = value;
        String previous;
        do {
            previous = current;
            current = REPEATED_TEXT.matcher(current).replaceAll("$1");
        } while (!current.equals(previous));
        return current;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\u0000', ' ')
                .replaceAll("[ \\t\\r\\f]+", " ")
                .replaceAll("[ \\t]*\\n[ \\t]*", "\\n");
        return normalized.trim();
    }

    private String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).replaceAll("[，。；;、|｜·•\\s]+$", "").trim();
    }

    public record OptimizationResult(
            String title,
            String description,
            List<String> rulesApplied,
            boolean free
    ) { }
}
