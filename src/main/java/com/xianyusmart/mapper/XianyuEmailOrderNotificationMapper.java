package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuEmailOrderNotification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单邮箱通知幂等记录 Mapper。
 */
@Mapper
public interface XianyuEmailOrderNotificationMapper extends BaseMapper<XianyuEmailOrderNotification> {

    @Insert("INSERT IGNORE INTO xianyu_email_order_notification " +
            "(tenant_id, xianyu_account_id, order_key, order_id, event_type, send_status) " +
            "VALUES (#{tenantId}, #{xianyuAccountId}, #{orderKey}, #{orderId}, #{eventType}, 0)")
    int claim(@Param("tenantId") Long tenantId,
              @Param("xianyuAccountId") Long xianyuAccountId,
              @Param("orderKey") String orderKey,
              @Param("orderId") String orderId,
              @Param("eventType") String eventType);

    @Update("UPDATE xianyu_email_order_notification SET send_status = 1, sent_time = NOW(3), " +
            "error_message = NULL WHERE tenant_id = #{tenantId} AND order_key = #{orderKey} " +
            "AND event_type = #{eventType} AND send_status = 0")
    int markSent(@Param("tenantId") Long tenantId,
                 @Param("orderKey") String orderKey,
                 @Param("eventType") String eventType);

    @Update("UPDATE xianyu_email_order_notification SET error_message = #{errorMessage} " +
            "WHERE tenant_id = #{tenantId} AND order_key = #{orderKey} " +
            "AND event_type = #{eventType} AND send_status = 0")
    int markFailed(@Param("tenantId") Long tenantId,
                   @Param("orderKey") String orderKey,
                   @Param("eventType") String eventType,
                   @Param("errorMessage") String errorMessage);
}
