package com.mrshudson.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.config.WeatherProperties;
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
 * 路线规划服务 - 使用高德地图路径规划API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final WeatherProperties weatherProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 出行方式枚举
     */
    public enum TravelMode {
        WALKING("walking", "步行"),
        DRIVING("driving", "驾车"),
        TRANSIT("transit/integrated", "公交");

        private final String apiPath;
        private final String displayName;

        TravelMode(String apiPath, String displayName) {
            this.apiPath = apiPath;
            this.displayName = displayName;
        }

        public String getApiPath() {
            return apiPath;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static TravelMode fromString(String mode) {
            if (mode == null) return DRIVING;
            return switch (mode.toLowerCase()) {
                case "walk", "walking", "步行" -> WALKING;
                case "drive", "driving", "car", "驾车", "开车", "自驾" -> DRIVING;
                case "transit", "bus", "公交", "公共交通", "地铁" -> TRANSIT;
                default -> DRIVING;
            };
        }
    }

    /**
     * 规划路线（字符串格式，供MCP工具使用）
     *
     * @param origin      起点
     * @param destination 终点
     * @param mode        出行方式
     * @return 路线信息字符串
     */
    public String planRoute(String origin, String destination, String mode) {
        // 检查API密钥
        if (weatherProperties.getApiKey() == null || weatherProperties.getApiKey().isEmpty()) {
            log.warn("高德API密钥未配置，返回模拟数据");
            return generateMockRoute(origin, destination, mode);
        }

        try {
            TravelMode travelMode = TravelMode.fromString(mode);

            // 1. 获取起点和终点的经纬度
            String originCoord = getCoordinate(origin);
            if (originCoord == null) {
                return String.format("未找到起点位置：%s", origin);
            }

            String destCoord = getCoordinate(destination);
            if (destCoord == null) {
                return String.format("未找到终点位置：%s", destination);
            }

            // 2. 调用路径规划API
            String result = callDirectionApi(originCoord, destCoord, travelMode, origin, destination);
            return result;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("无法连接到高德API服务器", e);
            return generateMockRoute(origin, destination, mode, "网络连接失败");
        } catch (Exception e) {
            log.error("规划路线失败", e);
            return generateMockRoute(origin, destination, mode, e.getMessage());
        }
    }

    /**
     * 调用高德路径规划API
     */
    private String callDirectionApi(String origin, String destination, TravelMode mode,
                                    String originName, String destName) {
        try {
            String apiUrl = weatherProperties.getBaseUrl() + "/direction/" + mode.getApiPath();

            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromHttpUrl(apiUrl)
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("origin", origin)
                    .queryParam("destination", destination)
                    .queryParam("output", "JSON");

            // 公交规划添加城市参数
            if (mode == TravelMode.TRANSIT) {
                uriBuilder.queryParam("city", "北京"); // 默认北京，可以从起点解析城市
                uriBuilder.queryParam("cityd", "北京"); // 目的城市
            }

            URI uri = uriBuilder.build().toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                String errorInfo = data.getString("info");
                log.error("路线规划失败: {}", errorInfo);

                if (errorInfo != null && (errorInfo.contains("USERKEY") || errorInfo.contains("KEY"))) {
                    return generateMockRoute(originName, destName, mode.name(), "API密钥无效");
                }

                return "路线规划失败：" + errorInfo;
            }

            // 解析路线结果
            return parseRouteResult(data, mode, originName, destName);

        } catch (Exception e) {
            log.error("调用路线规划API失败", e);
            return generateMockRoute(originName, destName, mode.name(), e.getMessage());
        }
    }

    /**
     * 解析路线结果
     */
    private String parseRouteResult(JSONObject data, TravelMode mode, String originName, String destName) {
        StringBuilder result = new StringBuilder();

        result.append(String.format("🗺️ %s → %s\n", originName, destName));
        result.append(String.format("🚗 出行方式：%s\n\n", mode.getDisplayName()));

        JSONObject route = data.getJSONObject("route");
        if (route == null) {
            return "未找到可行路线";
        }

        // 获取路线列表
        JSONArray paths = route.getJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            // 公交规划的数据结构不同
            JSONArray transits = route.getJSONArray("transits");
            if (transits != null && !transits.isEmpty()) {
                return parseTransitResult(transits, originName, destName);
            }
            return "未找到可行路线";
        }

        // 驾车/步行路线
        JSONObject path = paths.getJSONObject(0);

        // 距离和时间
        String distance = path.getString("distance");
        String duration = path.getString("duration");

        result.append(String.format("📏 总距离：%s\n", formatDistance(distance)));
        result.append(String.format("⏱️ 预计时间：%s\n\n", formatDuration(duration)));

        // 详细步骤
        JSONArray steps = path.getJSONArray("steps");
        if (steps != null && !steps.isEmpty()) {
            result.append("📝 详细路线：\n");
            for (int i = 0; i < Math.min(steps.size(), 10); i++) {
                JSONObject step = steps.getJSONObject(i);
                String instruction = step.getString("instruction");
                // 去除HTML标签
                instruction = instruction.replaceAll("<[^>]+>", "");
                String stepDistance = formatDistance(step.getString("distance"));
                result.append(String.format("%d. %s (%s)\n", i + 1, instruction, stepDistance));
            }
            if (steps.size() > 10) {
                result.append("...（更多步骤省略）\n");
            }
        }

        // 驾车特有信息
        if (mode == TravelMode.DRIVING) {
            String tolls = path.getString("tolls");
            String trafficLights = path.getString("traffic_lights");
            if (tolls != null && !"0".equals(tolls)) {
                result.append(String.format("\n💰 过路费：约%s元\n", tolls));
            }
            if (trafficLights != null) {
                result.append(String.format("🚦 红绿灯：约%s个\n", trafficLights));
            }
        }

        return result.toString();
    }

    /**
     * 解析公交路线结果
     */
    private String parseTransitResult(JSONArray transits, String originName, String destName) {
        StringBuilder result = new StringBuilder();

        result.append(String.format("🗺️ %s → %s\n", originName, destName));
        result.append(String.format("🚌 出行方式：公交/地铁\n\n"));

        // 获取最佳方案
        JSONObject transit = transits.getJSONObject(0);

        // 时间和距离
        String duration = transit.getString("duration");
        String walkingDistance = transit.getString("walking_distance");
        String cost = transit.getString("cost");

        result.append(String.format("⏱️ 预计时间：%s\n", formatDuration(duration)));
        result.append(String.format("🚶 步行距离：%s\n", formatDistance(walkingDistance)));
        if (cost != null && !cost.isEmpty()) {
            result.append(String.format("💰 预计费用：%s元\n", cost));
        }
        result.append("\n");

        // 详细路段
        JSONArray segments = transit.getJSONArray("segments");
        if (segments != null && !segments.isEmpty()) {
            result.append("📝 换乘方案：\n");
            int stepNum = 1;
            for (int i = 0; i < segments.size(); i++) {
                JSONObject segment = segments.getJSONObject(i);

                // 步行段
                JSONObject walking = segment.getJSONObject("walking");
                if (walking != null) {
                    String walkDest = walking.getString("destination");
                    String walkDist = walking.getString("distance");
                    if (walkDest != null && !walkDest.isEmpty()) {
                        result.append(String.format("%d. 🚶 步行至%s（%s）\n",
                                stepNum++, walkDest, formatDistance(walkDist)));
                    }
                }

                // 公交/地铁段
                JSONObject bus = segment.getJSONObject("bus");
                if (bus != null) {
                    JSONArray buslines = bus.getJSONArray("buslines");
                    if (buslines != null && !buslines.isEmpty()) {
                        JSONObject busline = buslines.getJSONObject(0);
                        String busName = busline.getString("name");
                        String busStart = busline.getString("departure_stop");
                        String busEnd = busline.getString("arrival_stop");
                        String busCount = busline.getString("via_num");

                        // 判断是地铁还是公交
                        String icon = busName.contains("地铁") ? "🚇" : "🚌";
                        result.append(String.format("%d. %s 乘坐%s\n", stepNum++, icon, busName));
                        result.append(String.format("   从 %s 上车，%s 下车（经过%s站）\n",
                                busStart, busEnd, busCount));
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * 获取地点的经纬度坐标
     */
    private String getCoordinate(String address) {
        try {
            // 如果是纯数字，直接认为是城市编码（暂不处理）
            if (address.matches("\\d+")) {
                // 尝试从城市编码映射获取中心坐标
                return getCityCenterByCode(address);
            }

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(weatherProperties.getBaseUrl() + "/geocode/geo")
                    .queryParam("key", weatherProperties.getApiKey())
                    .queryParam("address", URLEncoder.encode(address, StandardCharsets.UTF_8))
                    .queryParam("output", "JSON")
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JSONObject data = JSON.parseObject(response.getBody());

            if (!"1".equals(data.getString("status"))) {
                log.warn("地理编码失败: {}", data.getString("info"));
                return null;
            }

            JSONArray geocodes = data.getJSONArray("geocodes");
            if (geocodes == null || geocodes.isEmpty()) {
                return null;
            }

            // 返回经纬度格式：经度,纬度
            return geocodes.getJSONObject(0).getString("location");

        } catch (Exception e) {
            log.error("获取坐标失败: {}", address, e);
            return null;
        }
    }

    /**
     * 常见城市中心坐标映射
     */
    private String getCityCenterByCode(String cityCode) {
        return switch (cityCode) {
            case "110000" -> "116.407526,39.90403";     // 北京
            case "310000" -> "121.473701,31.230416";    // 上海
            case "440100" -> "113.264434,23.129162";    // 广州
            case "440300" -> "114.057868,22.543099";    // 深圳
            case "330100" -> "120.15507,30.274084";     // 杭州
            case "320100" -> "118.796877,32.060255";    // 南京
            case "510100" -> "104.066541,30.572269";    // 成都
            case "420100" -> "114.305393,30.593099";    // 武汉
            case "610100" -> "108.93977,34.341574";     // 西安
            case "500000" -> "106.551556,29.563009";    // 重庆
            default -> null;
        };
    }

    /**
     * 格式化距离
     */
    private String formatDistance(String distance) {
        if (distance == null || distance.isEmpty()) {
            return "未知";
        }
        try {
            double dist = Double.parseDouble(distance);
            if (dist >= 1000) {
                return String.format("%.1f公里", dist / 1000);
            } else {
                return String.format("%.0f米", dist);
            }
        } catch (NumberFormatException e) {
            return distance;
        }
    }

    /**
     * 格式化时间
     */
    private String formatDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return "未知";
        }
        try {
            int seconds = Integer.parseInt(duration);
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;

            if (hours > 0) {
                return String.format("%d小时%d分钟", hours, minutes);
            } else {
                return String.format("%d分钟", minutes);
            }
        } catch (NumberFormatException e) {
            return duration;
        }
    }

    /**
     * 生成模拟路线数据
     */
    private String generateMockRoute(String origin, String destination, String mode, String reason) {
        TravelMode travelMode = TravelMode.fromString(mode);

        StringBuilder result = new StringBuilder();
        result.append(String.format("⚠️ 无法连接到路线规划服务器（%s）。\n\n", reason));
        result.append(String.format("🗺️ %s → %s\n", origin, destination));
        result.append(String.format("🚗 出行方式：%s\n\n", travelMode.getDisplayName()));

        // 模拟数据
        switch (travelMode) {
            case WALKING -> {
                result.append("📏 总距离：约2.5公里\n");
                result.append("⏱️ 预计时间：约35分钟\n\n");
                result.append("📝 模拟路线：\n");
                result.append("1. 从起点向正东方向出发\n");
                result.append("2. 沿主干道直行约800米\n");
                result.append("3. 右转进入商业街\n");
                result.append("4. 沿商业街步行约1公里\n");
                result.append("5. 左转到达目的地\n");
            }
            case TRANSIT -> {
                result.append("⏱️ 预计时间：约45分钟\n");
                result.append("💰 预计费用：4元\n\n");
                result.append("📝 模拟路线：\n");
                result.append("1. 🚶 步行至最近地铁站（约500米）\n");
                result.append("2. 🚇 乘坐地铁2号线（约15分钟）\n");
                result.append("3. 在换乘站换乘1号线\n");
                result.append("4. 🚇 乘坐地铁1号线（约10分钟）\n");
                result.append("5. 🚶 从地铁站步行至目的地（约600米）\n");
            }
            default -> {
                result.append("📏 总距离：约8.5公里\n");
                result.append("⏱️ 预计时间：约25分钟\n\n");
                result.append("📝 模拟路线：\n");
                result.append("1. 从起点向正北方向出发\n");
                result.append("2. 沿主干道行驶约3公里\n");
                result.append("3. 上高架/快速路\n");
                result.append("4. 沿快速路行驶约4公里\n");
                result.append("5. 下高架，进入辅路\n");
                result.append("6. 行驶约1.5公里到达目的地\n");
            }
        }

        result.append("\n💡 提示：路线API需要在有外网访问权限的环境中使用\n");
        result.append("获取地址：https://lbs.amap.com/api/webservice/guide/api/direction");

        return result.toString();
    }

    private String generateMockRoute(String origin, String destination, String mode) {
        return generateMockRoute(origin, destination, mode, "未配置API密钥");
    }
}
