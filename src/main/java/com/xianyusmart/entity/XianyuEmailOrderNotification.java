package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单邮箱通知幂等记录。
 */
@Data
@TableName("xianyu_email_order_notification")
public class XianyuEmailOrderNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long xianyuAccountId;

    private String orderKey;

    private String orderId;

    private String eventType;

    private Integer sendStatus;

    private String errorMessage;

    private LocalDateTime sentTime;

    private LocalDateTime createTime;
}
