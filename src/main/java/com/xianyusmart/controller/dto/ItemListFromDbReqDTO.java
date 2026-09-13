package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 从数据库获取商品列表请求DTO
 */
@Data
public class ItemListFromDbReqDTO {
    
    /**
     * 只显示在售商品
     * true=只显示在售(status=0), false/null=显示全部
     * 默认true
     */
    private Boolean onlyOnSale = true;

    /**
     * 按平台状态筛选。传入后优先于 onlyOnSale：0=在售，1=已下架，2=已售出。
     */
    private Integer status;
    
    /**
     * 闲鱼账号ID（可选）
     */
    private Long xianyuAccountId;
    
    /**
     * 页码，从1开始
     * 默认1
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量
     * 默认20
     */
    private Integer pageSize = 20;
}
