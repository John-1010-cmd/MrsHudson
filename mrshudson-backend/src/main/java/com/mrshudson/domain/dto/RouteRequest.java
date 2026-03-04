package com.mrshudson.domain.dto;

import lombok.Data;

/**
 * 路线规划请求DTO
 */
@Data
public class RouteRequest {

    /**
     * 起点地址
     */
    private String origin;

    /**
     * 终点地址
     */
    private String destination;

    /**
     * 出行方式：walking(步行)、driving(驾车)、transit(公交)
     * 默认为 driving
     */
    private String mode;
}
