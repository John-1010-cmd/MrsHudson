package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.domain.dto.WeatherDTO;
import com.mrshudson.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 天气控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * 获取当前天气
     */
    @GetMapping("/current")
    public Result<WeatherDTO> getCurrentWeather(@RequestParam String city) {
        log.info("查询当前天气: {}", city);
        WeatherDTO weather = weatherService.getCurrentWeatherDTO(city);
        return Result.success(weather);
    }

    /**
     * 获取天气预报（包含当前天气+预报）
     */
    @GetMapping("/forecast")
    public Result<WeatherDTO> getWeatherForecast(
            @RequestParam String city,
            @RequestParam(defaultValue = "3") int days) {
        log.info("查询天气预报: {}, 天数: {}", city, days);
        WeatherDTO forecast = weatherService.getWeatherForecastDTO(city, days);
        return Result.success(forecast);
    }
}
