package com.xianyusmart.service.impl;

import com.xianyusmart.service.EmailNotifyService;
import com.xianyusmart.service.NotificationCenterService;
import com.xianyusmart.service.SysSettingService;
import com.xianyusmart.context.TenantContext;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuEmailOrderNotificationMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class EmailNotifyServiceImpl implements EmailNotifyService {

    private static final String KEY_SMTP_HOST = "email_smtp_host";
    private static final String KEY_SMTP_PORT = "email_smtp_port";
    private static final String KEY_SMTP_USERNAME = "email_smtp_username";
    private static final String KEY_SMTP_PASSWORD = "email_smtp_password";
    private static final String KEY_SMTP_FROM = "email_smtp_from";
    private static final String KEY_SMTP_SSL = "email_smtp_ssl";
    private static final String KEY_WS_DISCONNECT_NOTIFY_ENABLED = "email_notify_ws_disconnect_enabled";
    private static final String KEY_COOKIE_EXPIRE_NOTIFY_ENABLED = "email_notify_cookie_expire_enabled";
    private static final String KEY_ORDER_CREATED_NOTIFY_ENABLED = "email_notify_order_created_enabled";
    private static final String KEY_DELIVERY_SUCCESS_NOTIFY_ENABLED = "email_notify_delivery_success_enabled";
    private static final String KEY_DELIVERY_FAILURE_NOTIFY_ENABLED = "email_notify_delivery_failure_enabled";

    @Autowired
    private SysSettingService sysSettingService;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuEmailOrderNotificationMapper orderEmailNotificationMapper;

    @Autowired
    private NotificationCenterService notificationCenterService;

    @Override
    @Async
    public void sendWsDisconnectNotifyEmail(Long accountId, String accountNote) {
        setTenantByAccount(accountId);
        notificationCenterService.dispatch("ACCOUNT_OFFLINE", accountId, "闲鱼账号连接已断开",
                accountNote == null ? "账号连接已断开，请检查登录状态。" : accountNote,
                Map.of("accountNote", accountNote == null ? "" : accountNote));
        if (!isEmailConfigured()) {
            log.warn("邮箱未配置，跳过发送WebSocket断开连接通知邮件");
            return;
        }
        if (!isWsDisconnectNotifyEnabled()) {
            log.debug("WebSocket断开连接邮件通知未启用，跳过");
            return;
        }

        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            String from = getSettingValue(KEY_SMTP_USERNAME);
            String to = getSettingValue(KEY_SMTP_FROM);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String subject = "【XianYuZP】消息监听已掉线 - " + (accountNote != null && !accountNote.isEmpty() ? accountNote : "账号" + accountId);
            String content = buildWsDisconnectEmailContent(accountId, accountNote, time);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "XianYuZP");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("WebSocket断开连接通知邮件发送成功: accountId={}, to={}", accountId, to);
        } catch (Exception e) {
            log.error("WebSocket断开连接通知邮件发送失败: accountId={}", accountId, e);
        }
    }

    @Override
    public boolean isWsDisconnectNotifyEnabled() {
        String value = getSettingValue(KEY_WS_DISCONNECT_NOTIFY_ENABLED);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    @Override
    @Async
    public void sendCookieExpireNotifyEmail(Long accountId, String accountNote) {
        setTenantByAccount(accountId);
        notificationCenterService.dispatch("CREDENTIAL_EXPIRED", accountId, "闲鱼账号登录凭证已失效",
                accountNote == null ? "登录凭证已失效，请重新登录。" : accountNote,
                Map.of("accountNote", accountNote == null ? "" : accountNote));
        if (!isEmailConfigured()) {
            log.warn("邮箱未配置，跳过发送Cookie过期通知邮件");
            return;
        }
        if (!isCookieExpireNotifyEnabled()) {
            log.debug("Cookie过期邮件通知未启用，跳过");
            return;
        }

        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            String from = getSettingValue(KEY_SMTP_USERNAME);
            String to = getSettingValue(KEY_SMTP_FROM);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String subject = "【XianYuZP】Cookie已过期 - " + (accountNote != null && !accountNote.isEmpty() ? accountNote : "账号" + accountId);
            String content = buildCookieExpireEmailContent(accountId, accountNote, time);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "XianYuZP");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Cookie过期通知邮件发送成功: accountId={}, to={}", accountId, to);
        } catch (Exception e) {
            log.error("Cookie过期通知邮件发送失败: accountId={}", accountId, e);
        }
    }

    @Override
    public boolean isCookieExpireNotifyEnabled() {
        String value = getSettingValue(KEY_COOKIE_EXPIRE_NOTIFY_ENABLED);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    @Override
    public boolean isEmailConfigured() {
        String host = getSettingValue(KEY_SMTP_HOST);
        String port = getSettingValue(KEY_SMTP_PORT);
        String username = getSettingValue(KEY_SMTP_USERNAME);
        String password = getSettingValue(KEY_SMTP_PASSWORD);
        String from = getSettingValue(KEY_SMTP_FROM);
        return isNotEmpty(host) && isNotEmpty(port) && isNotEmpty(username)
                && isNotEmpty(password) && isNotEmpty(from);
    }

    @Override
    public String sendTestEmail() {
        if (!isEmailConfigured()) {
            return "邮箱配置不完整，请先配置SMTP服务器、端口、用户名、密码和发件人邮箱";
        }

        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            String from = getSettingValue(KEY_SMTP_USERNAME);
            String to = getSettingValue(KEY_SMTP_FROM);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String subject = "【XianYuZP】邮箱配置测试";
            String content = buildTestEmailContent(time);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "XianYuZP");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("测试邮件发送成功: to={}", to);
            return null;
        } catch (Exception e) {
            log.error("测试邮件发送失败", e);
            return "发送失败: " + e.getMessage();
        }
    }

    private String buildTestEmailContent(String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#34c759;border-bottom:2px solid #34c759;padding-bottom:10px;'>✅ XianYuZP - 邮箱配置测试</h2>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>发送时间：</strong>").append(time).append("</p>");
        sb.append("<p style='margin:8px 0;color:#34c759;'>恭喜！您的邮箱通知配置已正常工作，可以正常接收系统通知邮件。</p>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private JavaMailSenderImpl buildMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(getSettingValue(KEY_SMTP_HOST));
        sender.setPort(Integer.parseInt(getSettingValue(KEY_SMTP_PORT)));
        sender.setUsername(getSettingValue(KEY_SMTP_USERNAME));
        sender.setPassword(getSettingValue(KEY_SMTP_PASSWORD));
        sender.setDefaultEncoding("UTF-8");

        String ssl = getSettingValue(KEY_SMTP_SSL);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (!"0".equals(ssl) && !"false".equalsIgnoreCase(ssl)) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        return sender;
    }

    private String buildWsDisconnectEmailContent(Long accountId, String accountNote, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#f56c6c;border-bottom:2px solid #f56c6c;padding-bottom:10px;'>🔴 XianYuZP - 消息监听已掉线</h2>");
        sb.append("<div style='background:#fef0f0;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>账号ID：</strong>").append(accountId).append("</p>");
        if (accountNote != null && !accountNote.isEmpty()) {
            sb.append("<p style='margin:8px 0;'><strong>账号备注：</strong>").append(accountNote).append("</p>");
        }
        sb.append("<p style='margin:8px 0;'><strong>触发时间：</strong>").append(time).append("</p>");
        sb.append("</div>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>问题描述：</strong>该闲鱼账号的WebSocket消息监听连接已断开，系统尝试多次重连均失败。</p>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>可能原因：</strong></p>");
        sb.append("<ul style='margin:4px 0;padding-left:20px;color:#666;'>");
        sb.append("<li>网络连接不稳定或断开</li>");
        sb.append("<li>闲鱼服务器维护或异常</li>");
        sb.append("<li>Cookie已过期失效</li>");
        sb.append("</ul>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>处理建议：</strong>请检查网络连接，确认Cookie是否有效，并在账号管理页面尝试重新连接。</p>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildCookieExpireEmailContent(Long accountId, String accountNote, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#e6a23c;border-bottom:2px solid #e6a23c;padding-bottom:10px;'>⚠️ XianYuZP - Cookie已过期</h2>");
        sb.append("<div style='background:#fdf6ec;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>账号ID：</strong>").append(accountId).append("</p>");
        if (accountNote != null && !accountNote.isEmpty()) {
            sb.append("<p style='margin:8px 0;'><strong>账号备注：</strong>").append(accountNote).append("</p>");
        }
        sb.append("<p style='margin:8px 0;'><strong>触发时间：</strong>").append(time).append("</p>");
        sb.append("</div>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>问题描述：</strong>该闲鱼账号的Cookie已过期，且系统无法自动续期。</p>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>影响范围：</strong></p>");
        sb.append("<ul style='margin:4px 0;padding-left:20px;color:#666;'>");
        sb.append("<li>无法接收买家消息</li>");
        sb.append("<li>无法自动回复</li>");
        sb.append("<li>无法自动发货</li>");
        sb.append("<li>无法同步商品数据</li>");
        sb.append("</ul>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>处理建议：</strong>请登录闲鱼APP重新获取Cookie，并在账号管理页面更新Cookie。</p>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String getSettingValue(String key) {
        String value = sysSettingService.getSettingValue(key);
        return value != null ? value : "";
    }

    private boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    @Override
    @Async
    public void sendKamiAlertEmail(String toEmail, String configName, int availableCount, int totalCount) {
        if (!isEmailConfigured()) {
            log.warn("邮箱未配置，跳过发送卡密预警邮件");
            return;
        }
        String targetEmail = toEmail;
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            targetEmail = getSettingValue(KEY_SMTP_FROM);
            if (targetEmail.isEmpty()) {
                log.warn("预警邮箱为空且系统邮箱未配置，跳过发送卡密预警邮件");
                return;
            }
        }

        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            String from = getSettingValue(KEY_SMTP_USERNAME);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String subject = "【XianYuZP】卡密库存预警 - " + (configName != null ? configName : "卡密配置");
            String content = buildKamiAlertEmailContent(configName, availableCount, totalCount, time);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "XianYuZP");
            helper.setTo(targetEmail);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("卡密预警邮件发送成功: configName={}, to={}, available={}/{}", configName, targetEmail, availableCount, totalCount);
        } catch (Exception e) {
            log.error("卡密预警邮件发送失败: configName={}, to={}", configName, targetEmail, e);
        }
    }

    @Override
    @Async
    public void sendKamiStockOutEmail(String toEmail, String configName, String orderId) {
        if (!isEmailConfigured()) {
            log.warn("邮箱未配置，跳过发送卡密库存不足邮件");
            return;
        }
        String targetEmail = toEmail;
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            targetEmail = getSettingValue(KEY_SMTP_FROM);
            if (targetEmail.isEmpty()) {
                log.warn("收件邮箱为空且系统邮箱未配置，跳过发送卡密库存不足邮件");
                return;
            }
        }

        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            String from = getSettingValue(KEY_SMTP_USERNAME);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String subject = "【XianYuZP】卡密库存不足 - " + (configName != null ? configName : "卡密配置");
            String content = buildKamiStockOutEmailContent(configName, orderId, time);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "XianYuZP");
            helper.setTo(targetEmail);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("卡密库存不足邮件发送成功: configName={}, orderId={}, to={}", configName, orderId, targetEmail);
        } catch (Exception e) {
            log.error("卡密库存不足邮件发送失败: configName={}, orderId={}, to={}", configName, orderId, targetEmail, e);
        }
    }

    private String buildKamiStockOutEmailContent(String configName, String orderId, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#f56c6c;border-bottom:2px solid #f56c6c;padding-bottom:10px;'>🔴 XianYuZP - 卡密库存不足</h2>");
        sb.append("<div style='background:#fef0f0;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>卡密配置：</strong>").append(configName != null ? configName : "未命名").append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>触发订单：</strong>").append(orderId != null ? orderId : "未知").append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>触发时间：</strong>").append(time).append("</p>");
        sb.append("</div>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#f56c6c;font-weight:bold;'>该卡密仓库已无可用卡密，订单无法自动发货！</p>");
        sb.append("<p style='margin:4px 0;color:#666;'>请及时补充卡密，或手动处理该订单。</p>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    @Override
    @Async
    public void sendAutoDeliveryFailEmail(String toEmail, String xyGoodsId, String orderId, String failReason) {
        sendAutoDeliveryFailEmail(null, null, toEmail, xyGoodsId, orderId, failReason);
    }

    @Override
    @Async
    public void sendAutoDeliveryFailEmail(Long accountId, String toEmail, String xyGoodsId, String orderId, String failReason) {
        sendAutoDeliveryFailEmail(accountId, null, toEmail, xyGoodsId, orderId, failReason);
    }

    @Override
    @Async
    public void sendAutoDeliveryFailEmail(Long accountId, Long recordId, String toEmail,
                                          String xyGoodsId, String orderId, String failReason) {
        setTenantByAccount(accountId);
        notificationCenterService.dispatch("DELIVERY_EXCEPTION", accountId, "自动发货异常",
                failReason == null ? "自动发货失败，请检查订单。" : failReason,
                Map.of("xyGoodsId", xyGoodsId == null ? "" : xyGoodsId,
                        "orderId", orderId == null ? "" : orderId));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String subject = "【XianYuZP】自动发货失败 - " + (orderId != null ? orderId : "未知订单");
        String content = buildAutoDeliveryFailEmailContent(xyGoodsId, orderId, failReason, time);
        sendOrderEmailOnce(accountId, recordId, orderId, xyGoodsId, "DELIVERY_FAILURE",
                KEY_DELIVERY_FAILURE_NOTIFY_ENABLED, subject, content, toEmail);
    }

    @Override
    @Async
    public void sendOrderCreatedEmail(Long accountId, Long recordId, String orderId, String xyGoodsId,
                                      String buyerUserName, boolean reviewRequired) {
        setTenantByAccount(accountId);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String subject = "【XianYuZP】新订单 - " + (orderId != null ? orderId : "未知订单");
        String content = buildOrderCreatedEmailContent(orderId, xyGoodsId, buyerUserName, reviewRequired, time);
        sendOrderEmailOnce(accountId, recordId, orderId, xyGoodsId, "ORDER_CREATED",
                KEY_ORDER_CREATED_NOTIFY_ENABLED, subject, content, null);
    }

    @Override
    @Async
    public void sendDeliverySuccessEmail(Long accountId, Long recordId, String orderId, String xyGoodsId,
                                         String deliveryMode) {
        setTenantByAccount(accountId);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String subject = "【XianYuZP】发货成功 - " + (orderId != null ? orderId : "未知订单");
        String content = buildDeliverySuccessEmailContent(orderId, xyGoodsId, deliveryMode, time);
        sendOrderEmailOnce(accountId, recordId, orderId, xyGoodsId, "DELIVERY_SUCCESS",
                KEY_DELIVERY_SUCCESS_NOTIFY_ENABLED, subject, content, null);
    }

    private void sendOrderEmailOnce(Long accountId, Long recordId, String orderId, String xyGoodsId,
                                    String eventType, String enabledKey, String subject,
                                    String content, String requestedToEmail) {
        if (!isEnabled(enabledKey)) {
            return;
        }
        if (!isEmailConfigured()) {
            log.warn("邮箱未配置，跳过订单邮箱通知: eventType={}, orderId={}", eventType, orderId);
            return;
        }
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            log.warn("订单邮箱通知缺少租户上下文，跳过: eventType={}, accountId={}, orderId={}", eventType, accountId, orderId);
            return;
        }
        String orderKey = resolveOrderKey(orderId, recordId, accountId, xyGoodsId);
        int claimed;
        try {
            claimed = orderEmailNotificationMapper.claim(tenantId, accountId, orderKey, orderId, eventType);
        } catch (Exception e) {
            log.error("抢占订单邮箱通知幂等记录失败: eventType={}, orderId={}", eventType, orderId, e);
            return;
        }
        if (claimed != 1) {
            log.debug("订单邮箱通知已发送或正在发送，跳过重复事件: eventType={}, orderId={}", eventType, orderId);
            return;
        }

        String targetEmail = requestedToEmail;
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            targetEmail = getSettingValue(KEY_SMTP_FROM);
        }
        if (targetEmail.isEmpty()) {
            orderEmailNotificationMapper.markFailed(tenantId, orderKey, eventType, "收件邮箱为空");
            log.warn("收件邮箱为空，跳过订单邮箱通知: eventType={}, orderId={}", eventType, orderId);
            return;
        }
        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(getSettingValue(KEY_SMTP_USERNAME), "XianYuZP");
            helper.setTo(targetEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
            orderEmailNotificationMapper.markSent(tenantId, orderKey, eventType);
            log.info("订单邮箱通知发送成功: eventType={}, orderId={}, to={}", eventType, orderId, targetEmail);
        } catch (Exception e) {
            String errorMessage = e.getMessage() == null ? "邮件发送失败" : e.getMessage();
            orderEmailNotificationMapper.markFailed(tenantId, orderKey, eventType,
                    errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
            log.error("订单邮箱通知发送失败: eventType={}, orderId={}", eventType, orderId, e);
        }
    }

    private boolean isEnabled(String key) {
        String value = getSettingValue(key);
        return value.isEmpty() || "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private String resolveOrderKey(String orderId, Long recordId, Long accountId, String xyGoodsId) {
        if (orderId != null && !orderId.isBlank()) {
            return orderId.trim();
        }
        if (recordId != null) {
            return "record:" + recordId;
        }
        return "account:" + (accountId == null ? "unknown" : accountId)
                + ":goods:" + (xyGoodsId == null ? "unknown" : xyGoodsId);
    }

    private String buildOrderCreatedEmailContent(String orderId, String xyGoodsId, String buyerUserName,
                                                 boolean reviewRequired, String time) {
        return buildOrderEmailShell("🟦 XianYuZP - 新订单", "#409eff", "#ecf5ff",
                "订单已创建并进入自动发货队列。", orderId, xyGoodsId, buyerUserName,
                reviewRequired ? "该订单已暂停自动化并进入人工复核。" : "系统将继续按商品配置尝试自动发货。", time);
    }

    private String buildDeliverySuccessEmailContent(String orderId, String xyGoodsId,
                                                    String deliveryMode, String time) {
        return buildOrderEmailShell("✅ XianYuZP - 发货成功", "#67c23a", "#f0f9eb",
                "订单已按配置完成交付。", orderId, xyGoodsId, null,
                "发货方式：" + (deliveryMode == null ? "自动发货" : deliveryMode), time);
    }

    private String buildOrderEmailShell(String title, String color, String background, String summary,
                                        String orderId, String xyGoodsId, String buyerUserName,
                                        String detail, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:").append(color).append(";border-bottom:2px solid ").append(color)
                .append(";padding-bottom:10px;'>").append(title).append("</h2>");
        sb.append("<div style='background:").append(background).append(";border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>订单ID：</strong>").append(orderId == null ? "未知" : orderId).append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>商品ID：</strong>").append(xyGoodsId == null ? "未知" : xyGoodsId).append("</p>");
        if (buyerUserName != null && !buyerUserName.isBlank()) {
            sb.append("<p style='margin:8px 0;'><strong>买家：</strong>").append(buyerUserName).append("</p>");
        }
        sb.append("<p style='margin:8px 0;'><strong>时间：</strong>").append(time).append("</p>");
        sb.append("</div><p style='margin:8px 0;'>").append(summary).append("</p>");
        sb.append("<p style='margin:8px 0;color:#666;'>").append(detail).append("</p>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>此邮件由 XianYuZP 自动发送，请勿回复</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildAutoDeliveryFailEmailContent(String xyGoodsId, String orderId, String failReason, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#f56c6c;border-bottom:2px solid #f56c6c;padding-bottom:10px;'>🔴 XianYuZP - 自动发货失败</h2>");
        sb.append("<div style='background:#fef0f0;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>商品ID：</strong>").append(xyGoodsId != null ? xyGoodsId : "未知").append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>订单ID：</strong>").append(orderId != null ? orderId : "未知").append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>失败原因：</strong><span style='color:#f56c6c;font-weight:bold;'>").append(failReason != null ? failReason : "未知").append("</span></p>");
        sb.append("<p style='margin:8px 0;'><strong>失败时间：</strong>").append(time).append("</p>");
        sb.append("</div>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#666;'>请检查发货配置或手动处理该订单。</p>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildKamiAlertEmailContent(String configName, int availableCount, int totalCount, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#e6a23c;border-bottom:2px solid #e6a23c;padding-bottom:10px;'>⚠️ XianYuZP - 卡密库存预警</h2>");
        sb.append("<div style='background:#fdf6ec;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>卡密配置：</strong>").append(configName != null ? configName : "未命名").append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>可用数量：</strong><span style='color:#e6a23c;font-weight:bold;'>").append(availableCount).append("</span></p>");
        sb.append("<p style='margin:8px 0;'><strong>总数量：</strong>").append(totalCount).append("</p>");
        sb.append("<p style='margin:8px 0;'><strong>预警时间：</strong>").append(time).append("</p>");
        sb.append("</div>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#666;'>该卡密配置的可用库存已低于预设阈值，请及时补充卡密。</p>");
        sb.append("<p style='margin:4px 0;color:#666;'>如需关闭预警通知，请在卡密配置页面调整预警设置。</p>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    @Override
    @Async
    public void sendCaptchaRequiredEmail(Long accountId, String accountNote, String reason) {
        setTenantByAccount(accountId);
        if (!isEmailConfigured()) {
            log.warn("邮箱未配置，跳过发送风控验证通知邮件");
            return;
        }
        if (!isCookieExpireNotifyEnabled()) {
            log.debug("Cookie过期邮件通知未启用，跳过风控验证通知");
            return;
        }

        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            String from = getSettingValue(KEY_SMTP_USERNAME);
            String to = getSettingValue(KEY_SMTP_FROM);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String subject = "【XianYuZP】触发风控验证 - " + (accountNote != null && !accountNote.isEmpty() ? accountNote : "账号" + accountId);
            String content = buildCaptchaRequiredEmailContent(accountId, accountNote, reason, time);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "XianYuZP");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("风控验证通知邮件发送成功: accountId={}, to={}", accountId, to);
        } catch (Exception e) {
            log.error("风控验证通知邮件发送失败: accountId={}", accountId, e);
        }
    }

    private String buildCaptchaRequiredEmailContent(Long accountId, String accountNote, String reason, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>");
        sb.append("<h2 style='color:#f56c6c;border-bottom:2px solid #f56c6c;padding-bottom:10px;'>🔴 XianYuZP - 触发风控验证</h2>");
        sb.append("<div style='background:#fef0f0;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:8px 0;'><strong>账号ID：</strong>").append(accountId).append("</p>");
        if (accountNote != null && !accountNote.isEmpty()) {
            sb.append("<p style='margin:8px 0;'><strong>账号备注：</strong>").append(accountNote).append("</p>");
        }
        sb.append("<p style='margin:8px 0;'><strong>触发原因：</strong><span style='color:#f56c6c;font-weight:bold;'>").append(reason != null ? reason : "未知").append("</span></p>");
        sb.append("<p style='margin:8px 0;'><strong>触发时间：</strong>").append(time).append("</p>");
        sb.append("</div>");
        sb.append("<div style='background:#fff3cd;border-left:4px solid #ffc107;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#856404;font-weight:bold;'>⚠️ 系统检测到闲鱼风控，需要人工处理滑块验证</p>");
        sb.append("</div>");
        sb.append("<div style='background:#f0f9ff;border-radius:8px;padding:16px;margin:16px 0;'>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>处理步骤：</strong></p>");
        sb.append("<ol style='margin:8px 0;padding-left:20px;color:#666;'>");
        sb.append("<li>打开浏览器，访问闲鱼网页版：<a href='https://www.goofish.com/im' target='_blank'>https://www.goofish.com/im</a></li>");
        sb.append("<li>登录对应的闲鱼账号</li>");
        sb.append("<li>点击消息页面，完成滑块验证</li>");
        sb.append("<li>验证通过后，按F12打开开发者工具</li>");
        sb.append("<li>切换到Application/应用程序标签页</li>");
        sb.append("<li>在左侧找到Cookies，点击https://www.goofish.com</li>");
        sb.append("<li>复制所有Cookie（可使用EditThisCookie等浏览器插件）</li>");
        sb.append("<li>在 XianYuZP 的账号管理页面，更新该账号的Cookie</li>");
        sb.append("</ol>");
        sb.append("<p style='margin:4px 0;color:#666;'><strong>注意事项：</strong></p>");
        sb.append("<ul style='margin:4px 0;padding-left:20px;color:#666;'>");
        sb.append("<li>Cookie更新后，系统会自动恢复正常运行</li>");
        sb.append("<li>如频繁触发风控，建议降低操作频率或更换账号</li>");
        sb.append("<li>风控期间，该账号的所有自动化功能将暂停</li>");
        sb.append("</ul>");
        sb.append("</div>");
        sb.append("<div style='color:#999;font-size:12px;margin-top:20px;border-top:1px solid #eee;padding-top:10px;'>");
        sb.append("此邮件由 XianYuZP 自动发送，请勿回复");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private void setTenantByAccount(Long accountId) {
        if (accountId == null || TenantContext.get() != null) {
            return;
        }
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            TenantContext.set(account.getTenantId());
        }
    }
}


