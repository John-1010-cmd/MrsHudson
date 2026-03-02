package com.mrshudson.domain.dto;

import lombok.Data;
import java.util.List;

/**
 * 天气数据DTO
 */
@Data
public class WeatherDTO {

    /** 城市名称 */
    private String city;

    /** 当前温度 */
    private String temperature;

    /** 天气描述 */
    private String weather;

    /** 湿度 */
    private String humidity;

    /** 风向 */
    private String windDirection;

    /** 风力 */
    private String windPower;

    /** 报告时间 */
    private String reportTime;

    /** 预报数据 */
    private List<ForecastDayDTO> forecast;

    /**
     * 预报天数DTO
     */
    @Data
    public static class ForecastDayDTO {
        /** 日期 */
        private String date;

        /** 天气 */
        private String weather;

        /** 最低温度 */
        private String tempMin;

        /** 最高温度 */
        private String tempMax;
    }
}
