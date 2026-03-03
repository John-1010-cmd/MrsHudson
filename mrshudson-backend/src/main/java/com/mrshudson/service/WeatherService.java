package com.mrshudson.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.config.WeatherProperties;
import com.mrshudson.domain.dto.WeatherDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 天气服务 - 使用高德地图天气API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherProperties weatherProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    // 模拟模式开关（当API不可用时启用）
    private boolean mockMode = false;

    /**
     * 获取实时天气DTO（供API使用）
     *
     * @param city 城市名称
     * @return 天气DTO
     */
    public WeatherDTO getCurrentWeatherDTO(String city) {
        // 检查API密钥
        if (weatherProperties.getApiKey() == null || weatherProperties.getApiKey().isEmpty()) {
            log.warn("天气API密钥未配置，返回模拟数据");
            return generateMockWeatherDTO(city);
        }

        try {
            // 1. 获取城市编码
            String cityCode = getCityCode(city);
            if (cityCode == null) {
                throw new RuntimeException("未找到城市：" + city);
            }

            // 2. 查询实时天气
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/weather/weatherInfo")
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("city", cityCode)
                    .queryParam("extensions", "base")
                    .queryParam("output", "JSON")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                String errorInfo = data.getString("info");
                log.error("查询天气失败: {}", errorInfo);

                // 检查是否是密钥问题
                if (errorInfo != null && (errorInfo.contains("USERKEY") || errorInfo.contains("KEY"))) {
                    return generateMockWeatherDTO(city, "API密钥无效");
                }

                throw new RuntimeException("查询天气失败：" + errorInfo);
            }

            JSONArray lives = data.getJSONArray("lives");
            if (lives == null || lives.isEmpty()) {
                throw new RuntimeException("暂无该城市天气信息");
            }

            JSONObject weather = lives.getJSONObject(0);
            WeatherDTO dto = new WeatherDTO();
            dto.setCity(weather.getString("city"));
            dto.setTemperature(weather.getString("temperature"));
            dto.setWeather(weather.getString("weather"));
            dto.setHumidity(weather.getString("humidity"));
            dto.setWindDirection(weather.getString("winddirection"));
            dto.setWindPower(weather.getString("windpower"));
            dto.setReportTime(weather.getString("reporttime"));

            return dto;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("无法连接到天气API服务器", e);
            return generateMockWeatherDTO(city, "网络连接失败");
        } catch (Exception e) {
            log.error("获取天气失败", e);
            return generateMockWeatherDTO(city, e.getMessage());
        }
    }

    /**
     * 生成模拟天气DTO
     */
    private WeatherDTO generateMockWeatherDTO(String city) {
        return generateMockWeatherDTO(city, "未配置API密钥");
    }

    private WeatherDTO generateMockWeatherDTO(String city, String reason) {
        WeatherDTO dto = new WeatherDTO();
        dto.setCity(city);
        dto.setWeather("晴（模拟数据 - " + reason + "）");
        dto.setTemperature("25");
        dto.setHumidity("60");
        dto.setWindDirection("东南");
        dto.setWindPower("微风");
        dto.setReportTime(java.time.LocalDateTime.now().toString());
        return dto;
    }

    /**
     * 获取天气预报DTO（供API使用）
     *
     * @param city 城市名称
     * @param days 天数（1-7）
     * @return 天气DTO（包含预报）
     */
    public WeatherDTO getWeatherForecastDTO(String city, int days) {
        try {
            // 限制天数范围（高德免费版支持4天预报）
            days = Math.max(1, Math.min(days, 4));

            // 1. 获取当前天气
            WeatherDTO dto = getCurrentWeatherDTO(city);

            // 2. 获取城市编码
            String cityCode = getCityCode(city);

            // 3. 查询预报天气
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/weather/weatherInfo")
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("city", cityCode)
                    .queryParam("extensions", "all")
                    .queryParam("output", "JSON")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                log.error("查询天气预报失败: {}", data.getString("info"));
                return dto;
            }

            JSONArray forecasts = data.getJSONArray("forecasts");
            if (forecasts == null || forecasts.isEmpty()) {
                return dto;
            }

            JSONObject forecast = forecasts.getJSONObject(0);
            JSONArray casts = forecast.getJSONArray("casts");

            // 4. 构建预报列表
            List<WeatherDTO.ForecastDayDTO> forecastList = new ArrayList<>();
            for (int i = 0; i < Math.min(days, casts.size()); i++) {
                JSONObject day = casts.getJSONObject(i);
                WeatherDTO.ForecastDayDTO dayDTO = new WeatherDTO.ForecastDayDTO();
                dayDTO.setDate(day.getString("date"));
                dayDTO.setWeather(day.getString("dayweather"));
                dayDTO.setTempMin(day.getString("nighttemp"));
                dayDTO.setTempMax(day.getString("daytemp"));
                forecastList.add(dayDTO);
            }
            dto.setForecast(forecastList);

            return dto;

        } catch (Exception e) {
            log.error("获取天气预报失败", e);
            throw new RuntimeException("获取天气预报失败: " + e.getMessage());
        }
    }

    /**
     * 获取实时天气（字符串格式，供MCP工具使用）
     *
     * @param city 城市名称
     * @return 天气信息字符串
     */
    public String getCurrentWeather(String city) {
        // 检查API密钥
        if (weatherProperties.getApiKey() == null || weatherProperties.getApiKey().isEmpty()) {
            log.warn("天气API密钥未配置，返回模拟数据");
            return generateMockWeather(city);
        }

        try {
            // 1. 获取城市编码
            String cityCode = getCityCode(city);
            if (cityCode == null) {
                return String.format("未找到城市：%s", city);
            }

            // 2. 查询实时天气
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/weather/weatherInfo")
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("city", cityCode)
                    .queryParam("extensions", "base")
                    .queryParam("output", "JSON")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                String errorInfo = data.getString("info");
                log.error("查询天气失败: {}", errorInfo);

                // 检查是否是密钥问题
                if (errorInfo != null && (errorInfo.contains("USERKEY") || errorInfo.contains("KEY"))) {
                    return String.format("天气API密钥无效或未启用。%s当前天气模拟数据：\n" +
                                    "☀️ 天气：晴\n" +
                                    "🌡️ 温度：25°C\n" +
                                    "💧 湿度：60%%\n" +
                                    "🌬️ 风向：东南风 微风\n" +
                                    "\n💡 提示：请在 https://lbs.amap.com 申请天气API密钥并在.env中配置",
                            city);
                }

                return "查询天气失败：" + errorInfo;
            }

            JSONArray lives = data.getJSONArray("lives");
            if (lives == null || lives.isEmpty()) {
                return "暂无该城市天气信息";
            }

            JSONObject weather = lives.getJSONObject(0);
            String weatherDesc = getWeatherEmoji(weather.getString("weather"));

            return String.format("%s当前天气：\n" +
                            "%s 天气：%s\n" +
                            "🌡️ 温度：%s°C\n" +
                            "💧 湿度：%s%%\n" +
                            "🌬️ 风向：%s 风力%s\n" +
                            "📍 更新时间：%s",
                    weather.getString("city"),
                    weatherDesc,
                    weather.getString("weather"),
                    weather.getString("temperature"),
                    weather.getString("humidity"),
                    weather.getString("winddirection"),
                    weather.getString("windpower"),
                    weather.getString("reporttime"));

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("无法连接到天气API服务器", e);
            return String.format("⚠️ 无法连接到天气服务器（网络限制）。%s当前天气模拟数据：\n" +
                            "☀️ 天气：晴\n" +
                            "🌡️ 温度：25°C\n" +
                            "💧 湿度：60%%\n" +
                            "🌬️ 风向：东南风 微风\n" +
                            "\n💡 提示：天气API需要在有外网访问权限的环境中使用",
                    city);
        } catch (Exception e) {
            log.error("获取天气失败", e);
            return String.format("获取天气信息失败: %s。%s当前天气模拟数据：\n" +
                            "☀️ 天气：晴\n" +
                            "🌡️ 温度：25°C\n" +
                            "💧 湿度：60%%\n" +
                            "🌬️ 风向：东南风 微风",
                    e.getMessage(), city);
        }
    }

    /**
     * 生成模拟天气数据
     */
    private String generateMockWeather(String city) {
        return String.format("%s当前天气（模拟数据）：\n" +
                        "☀️ 天气：晴\n" +
                        "🌡️ 温度：25°C\n" +
                        "💧 湿度：60%%\n" +
                        "🌬️ 风向：东南风 微风\n" +
                        "\n💡 提示：请在.env文件中配置 WEATHER_API_KEY 以获取真实天气数据\n" +
                        "获取地址：https://lbs.amap.com/api/webservice/guide/api/weatherinfo",
                city);
    }

    /**
     * 获取天气预报
     *
     * @param city 城市名称
     * @param days 天数（1-7）
     * @return 天气预报字符串
     */
    public String getWeatherForecast(String city, int days) {
        try {
            // 限制天数范围（高德免费版支持4天预报）
            days = Math.max(1, Math.min(days, 4));

            // 1. 获取城市编码
            String cityCode = getCityCode(city);
            if (cityCode == null) {
                return String.format("未找到城市：%s", city);
            }

            // 2. 查询预报天气
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/weather/weatherInfo")
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("city", cityCode)
                    .queryParam("extensions", "all")
                    .queryParam("output", "JSON")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                log.error("查询天气预报失败: {}", data.getString("info"));
                return "查询天气预报失败：" + data.getString("info");
            }

            JSONArray forecasts = data.getJSONArray("forecasts");
            if (forecasts == null || forecasts.isEmpty()) {
                return "暂无该城市天气预报";
            }

            JSONObject forecast = forecasts.getJSONObject(0);
            JSONArray casts = forecast.getJSONArray("casts");

            // 3. 构建预报结果
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s未来%d天天气预报：\n\n", forecast.getString("city"), days));

            for (int i = 0; i < Math.min(days, casts.size()); i++) {
                JSONObject day = casts.getJSONObject(i);
                sb.append(String.format("📅 %s %s\n", day.getString("date"), getWeekDay(day.getString("week"))));
                sb.append(String.format("   ☀️ 白天：%s %s°C\n",
                        getWeatherEmoji(day.getString("dayweather")),
                        day.getString("daytemp")));
                sb.append(String.format("   🌙 夜间：%s %s°C\n",
                        getWeatherEmoji(day.getString("nightweather")),
                        day.getString("nighttemp")));
                sb.append(String.format("   🌬️ %s风%s级\n",
                        day.getString("daywind"),
                        day.getString("daypower")));
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
     * 获取城市编码（通过地理编码API）
     */
    private String getCityCode(String city) {
        try {
            // 如果是纯数字，直接认为是城市编码
            if (city.matches("\\d+")) {
                return city;
            }

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/geocode/geo")
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("address", URLEncoder.encode(city, StandardCharsets.UTF_8))
                    .queryParam("output", "JSON")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                log.warn("地理编码失败: {}", data.getString("info"));
                // 尝试用城市名直接查询（可能是直辖市或已知编码）
                return getCityCodeByName(city);
            }

            JSONArray geocodes = data.getJSONArray("geocodes");
            if (geocodes == null || geocodes.isEmpty()) {
                return getCityCodeByName(city);
            }

            // 返回行政区划编码（adcode）
            return geocodes.getJSONObject(0).getString("adcode");

        } catch (Exception e) {
            log.error("获取城市编码失败", e);
            return getCityCodeByName(city);
        }
    }

    /**
     * 常见城市编码映射（备用）
     */
    private String getCityCodeByName(String city) {
        return switch (city) {
            case "北京" -> "110000";
            case "上海" -> "310000";
            case "广州" -> "440100";
            case "深圳" -> "440300";
            case "杭州" -> "330100";
            case "南京" -> "320100";
            case "成都" -> "510100";
            case "武汉" -> "420100";
            case "西安" -> "610100";
            case "重庆" -> "500000";
            case "天津" -> "120000";
            case "苏州" -> "320500";
            case "郑州" -> "410100";
            case "长沙" -> "430100";
            case "沈阳" -> "210100";
            case "青岛" -> "370200";
            case "宁波" -> "330200";
            case "东莞" -> "441900";
            case "厦门" -> "350200";
            case "福州" -> "350100";
            case "昆明" -> "530100";
            case "合肥" -> "340100";
            case "济南" -> "370100";
            case "哈尔滨" -> "230100";
            case "长春" -> "220100";
            case "大连" -> "210200";
            case "石家庄" -> "130100";
            case "太原" -> "140100";
            case "南昌" -> "360100";
            case "南宁" -> "450100";
            case "贵阳" -> "520100";
            case "兰州" -> "620100";
            case "海口" -> "460100";
            case "乌鲁木齐" -> "650100";
            case "拉萨" -> "540100";
            case "银川" -> "640100";
            case "呼和浩特" -> "150100";
            case "西宁" -> "630100";
            default -> null;
        };
    }

    /**
     * 获取星期几的中文表示
     */
    private String getWeekDay(String week) {
        return switch (week) {
            case "1" -> "周一";
            case "2" -> "周二";
            case "3" -> "周三";
            case "4" -> "周四";
            case "5" -> "周五";
            case "6" -> "周六";
            case "7" -> "周日";
            default -> "";
        };
    }

    /**
     * 获取天气描述（添加emoji）
     */
    private String getWeatherEmoji(String text) {
        if (text == null) return "🌤️";
        if (text.contains("晴")) return "☀️";
        if (text.contains("多云")) return "⛅";
        if (text.contains("阴")) return "☁️";
        if (text.contains("雨")) return "🌧️";
        if (text.contains("雪")) return "❄️";
        if (text.contains("雾") || text.contains("霾")) return "🌫️";
        if (text.contains("雷") || text.contains("电")) return "⛈️";
        if (text.contains("沙") || text.contains("尘")) return "🌪️";
        return "🌤️";
    }
}
