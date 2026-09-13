package com.xianyusmart.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.service.RiskControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼API调用工具类（带自动刷新机制）
 * 参考Python Demo的被动刷新策略
 */
@Slf4j
@Component
public class XianyuApiCallUtils {
    
    @Autowired
    private com.xianyusmart.service.CookieRefreshService cookieRefreshService;
    
    @Autowired
    private com.xianyusmart.service.AccountService accountService;

    @Autowired
    private RiskControlService riskControlService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 2;
    
    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL = 500;
    
    /**
     * 调用闲鱼API（带自动刷新机制）
     * 
     * @param accountId 账号ID
     * @param apiName API名称
     * @param dataMap 数据Map
     * @param cookiesStr Cookie字符串
     * @return API响应结果
     */
    public ApiCallResult callApiWithRetry(Long accountId, String apiName, 
                                          Map<String, Object> dataMap, String cookiesStr) {
        return callApiWithRetry(accountId, apiName, dataMap, cookiesStr, null, null, 0);
    }

    public ApiCallResult callApiWithRetry(Long accountId, String apiName,
                                          Map<String, Object> dataMap, String cookiesStr,
                                          Map<String, String> extraHeaders) {
        return callApiWithRetry(accountId, apiName, dataMap, cookiesStr, extraHeaders, null, 0);
    }

    public ApiCallResult callApiWithRetry(Long accountId, String apiName,
                                          Map<String, Object> dataMap, String cookiesStr,
                                          Map<String, String> extraHeaders,
                                          Map<String, String> extraQueryParams) {
        return callApiWithRetry(accountId, apiName, "1.0", dataMap, cookiesStr, extraHeaders, extraQueryParams, 0);
    }

    public ApiCallResult callApiWithRetry(Long accountId, String apiName, String apiPathVersion,
                                          Map<String, Object> dataMap, String cookiesStr,
                                          Map<String, String> extraHeaders,
                                          Map<String, String> extraQueryParams) {
        return callApiWithRetry(accountId, apiName, apiPathVersion, dataMap, cookiesStr, extraHeaders, extraQueryParams, 0);
    }
    
    private ApiCallResult callApiWithRetry(Long accountId, String apiName,
                                           Map<String, Object> dataMap, String cookiesStr,
                                           int retryCount) {
        return callApiWithRetry(accountId, apiName, dataMap, cookiesStr, null, null, retryCount);
    }

    private ApiCallResult callApiWithRetry(Long accountId, String apiName,
                                           Map<String, Object> dataMap, String cookiesStr,
                                           Map<String, String> extraHeaders,
                                           int retryCount) {
        return callApiWithRetry(accountId, apiName, dataMap, cookiesStr, extraHeaders, null, retryCount);
    }

    private ApiCallResult callApiWithRetry(Long accountId, String apiName,
                                           Map<String, Object> dataMap, String cookiesStr,
                                           Map<String, String> extraHeaders,
                                           Map<String, String> extraQueryParams,
                                           int retryCount) {
        return callApiWithRetry(accountId, apiName, "1.0", dataMap, cookiesStr, extraHeaders, extraQueryParams, retryCount);
    }

    private ApiCallResult callApiWithRetry(Long accountId, String apiName, String apiPathVersion,
                                           Map<String, Object> dataMap, String cookiesStr,
                                           Map<String, String> extraHeaders,
                                           Map<String, String> extraQueryParams,
                                           int retryCount) {
        try {
            RiskControlService.GuardDecision guard = riskControlService.checkApiWrite(accountId, apiName);
            if (!guard.allowed()) {
                log.warn("【账号{}】平台写请求等待恢复: apiName={}, remainingSeconds={}",
                        accountId, apiName, guard.remainingSeconds());
                return ApiCallResult.guardBlocked(guard);
            }

            XianyuApiUtils.ApiCallResultWithHeaders result = XianyuApiUtils.callApiWithHeaders(
                    apiName, apiPathVersion, dataMap, cookiesStr, null, null, extraHeaders, extraQueryParams);

            String response = result.getBody();
            if (response == null || response.isEmpty()) {
                log.error("【账号{}】API调用失败：响应为空", accountId);
                return new ApiCallResult(false, null, "响应为空", false);
            }

            // 2. 【关键】处理响应中的Set-Cookie（参考Python: session自动处理 + clear_duplicate_cookies）
            List<String> setCookieHeaders = result.getSetCookieHeaders();
            if (!setCookieHeaders.isEmpty()) {
                log.info("【账号{}】检测到响应中的Set-Cookie，数量: {}", accountId, setCookieHeaders.size());
                // 更新Cookie到数据库
                updateCookiesFromResponse(accountId, cookiesStr, setCookieHeaders);
            }

            // 3. 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            riskControlService.recordResponse(accountId, responseMap);

            @SuppressWarnings("unchecked")
            List<String> ret = (List<String>) responseMap.get("ret");

            if (ret == null || ret.isEmpty()) {
                log.error("【账号{}】API响应格式错误：缺少ret字段", accountId);
                return new ApiCallResult(false, response, "响应格式错误", false);
            }

            String retCode = ret.get(0);

            // 4. 检查是否成功
            if (retCode.contains("SUCCESS")) {
                log.info("【账号{}】API调用成功: {}", accountId, apiName);
                return new ApiCallResult(true, response, null, false);
            }

            // 5. 检查是否需要刷新Cookie（令牌过期）
            if (isTokenExpired(retCode)) {
                log.warn("【账号{}】检测到令牌过期，尝试自动刷新... (重试次数: {}/{})",
                        accountId, retryCount, MAX_RETRY_COUNT);

                // 检查是否超过最大重试次数
                if (retryCount >= MAX_RETRY_COUNT) {
                    log.error("【账号{}】令牌刷新重试次数已达上限，停止重试", accountId);
                    return new ApiCallResult(false, response, "令牌过期且自动刷新失败", true);
                }

                // 尝试刷新Cookie
                boolean refreshSuccess = cookieRefreshService.refreshCookie(accountId);

                if (refreshSuccess) {
                    log.info("【账号{}】Cookie刷新成功，准备重试API调用...", accountId);

                    // 等待一小段时间
                    Thread.sleep(RETRY_INTERVAL);

                    // 获取新的Cookie
                    String newCookieStr = accountService.getCookieByAccountId(accountId);
                    if (newCookieStr != null && !newCookieStr.isEmpty()) {
                        // 递归调用，重试API
                        return callApiWithRetry(accountId, apiName, apiPathVersion, dataMap, newCookieStr,
                                extraHeaders, extraQueryParams, retryCount + 1);
                    } else {
                        log.error("【账号{}】获取新Cookie失败", accountId);
                    }
                } else {
                    log.warn("【账号{}】Cookie自动刷新失败", accountId);
                }

                return new ApiCallResult(false, response, "令牌过期，自动刷新失败", true);
            }

            // 6. 检查是否触发风控
            if (riskControlService.detectRiskControl(responseMap)) {
                log.error("【账号{}】触发风控: {}", accountId, retCode);
                // 闲鱼不一定把处罚原因放在 ret 中，也可能放在 msg/message/data。
                // 传入完整响应，避免账号冻结被降级成“平台拒绝了本次操作”。
                return new ApiCallResult(false, response, platformRestrictionMessage(retCode, responseMap), false);
            }

            // 7. 其他错误
            log.error("【账号{}】API调用失败: {}", accountId, retCode);
            return new ApiCallResult(false, response, retCode, false);

        } catch (Exception e) {
            log.error("【账号{}】API调用异常: apiName={}", accountId, apiName, e);
            return new ApiCallResult(false, null, "调用异常: " + e.getMessage(), false);
        }
    }

    /**
     * 从响应的Set-Cookie中更新Cookie
     * 参考Python: requests.Session自动处理Set-Cookie + clear_duplicate_cookies
     *
     * @param accountId 账号ID
     * @param currentCookieStr 当前Cookie字符串
     * @param setCookieHeaders 响应中的Set-Cookie列表
     */
    private void updateCookiesFromResponse(Long accountId, String currentCookieStr, List<String> setCookieHeaders) {
        try {
            // 合并Cookie
            String newCookieStr = mergeCookies(currentCookieStr, setCookieHeaders);

            // 清理重复Cookie
            newCookieStr = cookieRefreshService.clearDuplicateCookies(newCookieStr);

            // 更新数据库中的Cookie
            if (!newCookieStr.equals(currentCookieStr)) {
                accountService.updateCookie(accountId, newCookieStr);
                log.info("【账号{}】Cookie已从响应Set-Cookie更新到数据库", accountId);
            }
        } catch (Exception e) {
            log.error("【账号{}】处理响应Set-Cookie失败", accountId, e);
        }
    }

    /**
     * 合并Cookie（新Cookie覆盖旧Cookie）
     * 模拟Python requests.Session自动处理Set-Cookie的行为
     */
    private String mergeCookies(String oldCookieStr, List<String> newCookies) {
        Map<String, String> cookies = new java.util.LinkedHashMap<>();

        // 解析旧Cookie
        if (oldCookieStr != null && !oldCookieStr.isEmpty()) {
            String[] parts = oldCookieStr.split(";\\s*");
            for (String part : parts) {
                int idx = part.indexOf('=');
                if (idx > 0) {
                    String key = part.substring(0, idx);
                    String value = part.substring(idx + 1);
                    cookies.put(key, value);
                }
            }
        }

        // 解析新Cookie（Set-Cookie格式: name=value; Path=/; Domain=.goofish.com; ...）
        for (String newCookie : newCookies) {
            // 只提取第一个name=value对
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s*([^=;\\s]+)=([^;]*)");
            java.util.regex.Matcher matcher = pattern.matcher(newCookie);
            if (matcher.find()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                // 跳过删除Cookie（值为空）
                if (!value.isEmpty()) {
                    cookies.put(key, value);
                } else {
                    cookies.remove(key);
                }
            }
        }

        // 重新构建Cookie字符串
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return sb.toString();
    }
    
    /**
     * 判断是否为令牌过期错误
     */
    private boolean isTokenExpired(String retCode) {
        return retCode.contains("FAIL_SYS_TOKEN_EXOIRED") ||  // 注意：API返回的拼写错误
               retCode.contains("FAIL_SYS_TOKEN_EXPIRED") ||
               retCode.contains("FAIL_SYS_SESSION_EXPIRED") ||
               retCode.contains("令牌过期");
    }
    
    private String platformRestrictionMessage(String retCode, Map<String, Object> responseMap) {
        String responseText = String.valueOf(responseMap == null ? "" : responseMap);
        String normalized = (String.valueOf(retCode) + " " + responseText).toLowerCase();
        if (normalized.contains("冻结") || normalized.contains("封禁")
                || normalized.contains("账号处罚") || normalized.contains("处罚")
                || normalized.contains("account frozen") || normalized.contains("account_locked")
                || normalized.contains("account locked") || normalized.contains("user_banned")
                || normalized.contains("suspended") || normalized.contains("banned")
                || normalized.contains("punish") || normalized.contains("violation")) {
            return "闲鱼账号已被限制或冻结，系统已停止继续发布；请登录闲鱼官方客户端/网页版查看处罚原因并完成申诉或更换账号，切勿反复重试";
        }
        if (normalized.contains("fail_sys_user_validate") || normalized.contains("需要验证")
                || normalized.contains("身份验证") || normalized.contains("user_validate")) {
            return "闲鱼要求完成账号验证，已停止本次操作；请在连接管理重新登录并确认账号状态后再试";
        }
        if (normalized.contains("被挤爆啦") || normalized.contains("请求繁忙")
                || normalized.contains("too many requests") || normalized.contains("rate limit")) {
            return "闲鱼当前请求繁忙，已进入冷却，请等待后再试，不要连续点击发布";
        }
        if (normalized.contains("captcha") || normalized.contains("滑块")
                || normalized.contains("验证码") || normalized.contains("安全验证")) {
            return "闲鱼要求完成验证码或滑块验证，请先在官方页面完成验证后再试";
        }
        if (normalized.contains("illegal_access") || normalized.contains("非法访问")
                || normalized.contains("访问环境异常") || normalized.contains("machine")) {
            return "闲鱼拒绝了当前访问环境，已停止继续请求；请确认账号、网络和官方登录状态";
        }
        return "闲鱼拒绝了本次操作，系统已停止自动重试；请查看账号状态和任务详情后再决定是否手动处理";
    }
    
    /**
     * API调用结果封装类
     */
    public static class ApiCallResult {
        private final boolean success;
        private final String response;
        private final String errorMessage;
        private final boolean tokenExpired;
        private final RiskControlService.GuardState guardState;
        private final long remainingSeconds;
        private final long retryAt;
        
        public ApiCallResult(boolean success, String response, String errorMessage, boolean tokenExpired) {
            this(success, response, errorMessage, tokenExpired, null, 0, 0);
        }

        private ApiCallResult(boolean success, String response, String errorMessage, boolean tokenExpired,
                              RiskControlService.GuardState guardState, long remainingSeconds, long retryAt) {
            this.success = success;
            this.response = response;
            this.errorMessage = errorMessage;
            this.tokenExpired = tokenExpired;
            this.guardState = guardState;
            this.remainingSeconds = remainingSeconds;
            this.retryAt = retryAt;
        }

        public static ApiCallResult guardBlocked(RiskControlService.GuardDecision guard) {
            String message = guard.state() == RiskControlService.GuardState.CIRCUIT_OPEN
                    ? guardReasonMessage(guard.reason()) + "，剩余" + guard.remainingSeconds() + "秒"
                    : "写操作过于频繁，请" + guard.remainingSeconds() + "秒后重试";
            return new ApiCallResult(false, null, message, false,
                    guard.state(), guard.remainingSeconds(), guard.retryAt());
        }

        private static String guardReasonMessage(String reason) {
            if (reason == null || reason.isBlank()) {
                return "账号风控冷却中";
            }
            return switch (reason) {
                case "RGV587", "REQUEST_BUSY" -> "闲鱼返回异常流量或请求繁忙，系统已暂停发布；请停止重复点击，等待冷却后再试";
                case "USER_VALIDATE" -> "闲鱼要求完成账号验证；请先在官方客户端/网页版完成验证，再回到连接管理重新连接账号";
                case "CAPTCHA" -> "闲鱼要求完成验证码或滑块验证；请先在官方页面完成验证，再重新连接账号";
                case "ACCOUNT_FROZEN", "PUNISH" -> "闲鱼账号已被限制或处罚；请查看官方处罚通知并人工处理";
                case "ILLEGAL_ACCESS", "WUA_MACHINE" -> "闲鱼拒绝了当前访问环境；请确认官方登录状态、网络和设备环境";
                default -> "账号风控冷却中";
            };
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getResponse() {
            return response;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public boolean isTokenExpired() {
            return tokenExpired;
        }

        public boolean isGuardBlocked() {
            return guardState == RiskControlService.GuardState.RATE_WAIT
                    || guardState == RiskControlService.GuardState.CIRCUIT_OPEN;
        }

        public RiskControlService.GuardState getGuardState() {
            return guardState;
        }

        public long getRemainingSeconds() {
            return remainingSeconds;
        }

        public long getRetryAt() {
            return retryAt;
        }
        
        /**
         * 从响应中提取data字段
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> extractData() {
            if (response == null || response.isEmpty()) {
                return null;
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> responseMap = mapper.readValue(response, Map.class);
                return (Map<String, Object>) responseMap.get("data");
            } catch (Exception e) {
                log.error("提取data字段失败", e);
                return null;
            }
        }
    }
}
