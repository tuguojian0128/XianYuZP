package com.xianyusmart.exception;

import com.xianyusmart.service.RiskControlService;

/**
 * 平台写操作需要等待恢复
 */
public class RiskGuardBlockedException extends RuntimeException {

    private final long retryAt;

    public RiskGuardBlockedException(RiskControlService.GuardDecision decision) {
        super(decision.state() == RiskControlService.GuardState.CIRCUIT_OPEN
                ? reasonMessage(decision.reason()) + "，剩余" + decision.remainingSeconds() + "秒"
                : "写操作过于频繁，请" + decision.remainingSeconds() + "秒后重试");
        this.retryAt = decision.retryAt();
    }

    private static String reasonMessage(String reason) {
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

    public long getRetryAt() {
        return retryAt;
    }
}
