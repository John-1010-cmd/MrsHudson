package com.mrshudson.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线规划响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    /**
     * 路线规划结果文本
     */
    private String result;
}
