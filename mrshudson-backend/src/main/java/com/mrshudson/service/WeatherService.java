package com.mrshudson.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.config.WeatherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 天气服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherProperties weatherProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 获取实时天气
     *
     * @param city 城市名称
     * @return 天气信息JSON字符串
     */
    public String getCurrentWeather(String city) {
        try {
            // 1. 先获取城市Location ID
            String locationId = getLocationId(city);
            if (locationId == null) {
                return String.format("未找到城市：%s", city);
            }

            // 2. 查询实时天气
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/weather/now")
                    .queryParam("location", locationId)
                    .queryParam("key", weatherProperties.getApiKey())
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"200".equals(data.getString("code"))) {
                log.error("查询天气失败: {}", data.getString("code"));
                return "查询天气失败，请稍后重试";
            }

            JSONObject now = data.getJSONObject("now");
            String weatherDesc = getWeatherDesc(now.getString("text"));

            return String.format("%s当前天气：\n" +
                            "🌡️ 温度：%s°C\n" +
                            "💧 湿度：%s%%\n" +
                            "🌬️ 风向：%s %s级\n" +
                            "👁️ 能见度：%s公里",
                    city,
                    now.getString("temp"),
                    now.getString("humidity"),
                    now.getString("windDir"),
                    now.getString("windScale"),
                    now.getString("vis"));

        } catch (Exception e) {
            log.error("获取天气失败", e);
            return "获取天气信息失败: " + e.getMessage();
        }
    }

    /**
     * 获取天气预报
     *
     * @param city 城市名称
     * @param days 天数（1-7）
     * @return 天气预报JSON字符串
     */
    public String getWeatherForecast(String city, int days) {
        try {
            // 限制天数范围
            days = Math.max(1, Math.min(days, 7));

            // 1. 获取城市Location ID
            String locationId = getLocationId(city);
            if (locationId == null) {
                return String.format("未找到城市：%s", city);
            }

            // 2. 查询天气预报（使用7天预报接口）
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/weather/7d")
                    .queryParam("location", locationId)
                    .queryParam("key", weatherProperties.getApiKey())
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"200".equals(data.getString("code"))) {
                log.error("查询天气预报失败: {}", data.getString("code"));
                return "查询天气预报失败，请稍后重试";
            }

            // 3. 构建预报结果
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s未来%d天天气预报：\n\n", city, days));

            var dailyList = data.getJSONArray("daily");
            for (int i = 0; i < Math.min(days, dailyList.size()); i++) {
                JSONObject day = dailyList.getJSONObject(i);
                sb.append(String.format("📅 %s\n", day.getString("fxDate")));
                sb.append(String.format("   %s → %s°C\n",
                        day.getString("tempMin"),
                        day.getString("tempMax")));
                sb.append(String.format("   ☀️ 白天：%s\n", day.getString("textDay")));
                sb.append(String.format("   🌙 夜间：%s\n", day.getString("textNight")));
                if (i < days - 1) {
                    sb.append("\n");
                }
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("获取天气预报失败", e);
            return "获取天气预报失败: " + e.getMessage();
        }
    }

    /**
     * 获取Location ID
     */
    private String getLocationId(String city) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getGeoUrl() + "/city/lookup")
                    .queryParam("location", city)
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("range", "cn")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"200".equals(data.getString("code"))) {
                return null;
            }

            var locations = data.getJSONArray("location");
            if (locations == null || locations.isEmpty()) {
                return null;
            }

            return locations.getJSONObject(0).getString("id");

        } catch (Exception e) {
            log.error("获取Location ID失败", e);
            return null;
        }
    }

    /**
     * 获取天气描述（添加emoji）
     */
    private String getWeatherDesc(String text) {
        if (text.contains("晴")) return "☀️ " + text;
        if (text.contains("云") || text.contains("阴")) return "☁️ " + text;
        if (text.contains("雨")) return "🌧️ " + text;
        if (text.contains("雪")) return "❄️ " + text;
        if (text.contains("雾") || text.contains("霾")) return "🌫️ " + text;
        return "🌤️ " + text;
    }
}
