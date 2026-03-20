package com.mrshudson.optim.intent.extract;

import com.mrshudson.optim.intent.IntentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于规则的参数提取器（第1层）
 * 使用预定义规则和正则表达式提取结构化参数
 * 支持天气/日历/待办/路线意图的参数提取
 */
@Slf4j
@Component
public class RuleBasedExtractor implements ParameterExtractor {

    /**
     * 提取器名称
     */
    private static final String EXTRACTOR_NAME = "RuleBasedExtractor";

    /**
     * 常见城市列表
     */
    private static final Set<String> COMMON_CITIES = new HashSet<>(Arrays.asList(
            "北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "成都", "武汉", "西安",
            "重庆", "天津", "青岛", "大连", "厦门", "宁波", "无锡", "佛山", "东莞", "郑州",
            "长沙", "沈阳", "济南", "哈尔滨", "长春", "石家庄", "太原", "合肥", "南昌", "昆明",
            "贵阳", "南宁", "兰州", "海口", "银川", "西宁", "拉萨", "乌鲁木齐", "呼和浩特",
            "香港", "澳门", "台北", "高雄", "汕头", "珠海", "中山", "惠州", "江门", "湛江",
            "茂名", "肇庆", "梅州", "汕尾", "河源", "阳江", "清远", "潮州", "揭阳", "云浮",
            "徐州", "常州", "南通", "连云港", "淮安", "盐城", "扬州", "镇江", "泰州", "宿迁",
            "芜湖", "蚌埠", "淮南", "马鞍山", "淮北", "铜陵", "安庆", "黄山", "滁州", "阜阳",
            "宿州", "六安", "亳州", "池州", "宣城", "漳州", "龙岩", "三明", "南平", "宁德",
            "莆田", "泉州", "漳州", "淄博", "烟台", "潍坊", "济宁", "泰安", "威海", "日照",
            "临沂", "枣庄", "德州", "聊城", "滨州", "菏泽", "洛阳", "开封", "平顶山", "焦作",
            "鹤壁", "新乡", "安阳", "濮阳", "许昌", "漯河", "三门峡", "南阳", "商丘", "信阳"
    ));

    /**
     * 日期相关关键词映射
     */
    private static final Map<String, LocalDate> DATE_KEYWORDS = new HashMap<>();

    /**
     * 优先级关键词映射
     */
    private static final Map<String, String> PRIORITY_KEYWORDS = new HashMap<>();

    /**
     * 出行方式关键词映射
     */
    private static final Map<String, String> ROUTE_MODE_KEYWORDS = new HashMap<>();

    // 初始化静态数据
    static {
        // 相对日期
        DATE_KEYWORDS.put("今天", LocalDate.now());
        DATE_KEYWORDS.put("明天", LocalDate.now().plusDays(1));
        DATE_KEYWORDS.put("后天", LocalDate.now().plusDays(2));
        DATE_KEYWORDS.put("昨天", LocalDate.now().minusDays(1));
        DATE_KEYWORDS.put("前天", LocalDate.now().minusDays(2));
        DATE_KEYWORDS.put("大后天", LocalDate.now().plusDays(3));

        // 优先级映射
        PRIORITY_KEYWORDS.put("紧急", "high");
        PRIORITY_KEYWORDS.put("重要", "high");
        PRIORITY_KEYWORDS.put("高优先级", "high");
        PRIORITY_KEYWORDS.put("高", "high");
        PRIORITY_KEYWORDS.put("普通", "medium");
        PRIORITY_KEYWORDS.put("一般", "medium");
        PRIORITY_KEYWORDS.put("中", "medium");
        PRIORITY_KEYWORDS.put("低", "low");
        PRIORITY_KEYWORDS.put("不急", "low");
        PRIORITY_KEYWORDS.put("低优先级", "low");

        // 出行方式映射
        ROUTE_MODE_KEYWORDS.put("步行", "walking");
        ROUTE_MODE_KEYWORDS.put("走路", "walking");
        ROUTE_MODE_KEYWORDS.put("公交", "transit");
        ROUTE_MODE_KEYWORDS.put("地铁", "transit");
        ROUTE_MODE_KEYWORDS.put("公共汽车", "transit");
        ROUTE_MODE_KEYWORDS.put("坐车", "transit");
        ROUTE_MODE_KEYWORDS.put("驾车", "driving");
        ROUTE_MODE_KEYWORDS.put("开车", "driving");
        ROUTE_MODE_KEYWORDS.put("自驾", "driving");
        ROUTE_MODE_KEYWORDS.put("打车", "driving");
    }

    // 正则表达式模式
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d{1,2})[:点](\\d{0,2})分?|" +
                    "(早上|上午|下午|晚上)?\\s*(\\d{1,2})[:点]?(\\d{0,2})?分?"
    );

    private static final Pattern DATE_NUMBER_PATTERN = Pattern.compile(
            "(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})|" +
                    "(\\d{1,2})月(\\d{1,2})[日号]"
    );

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+)\\s*(分钟|小时|天|周|月)"
    );

    @Override
    public ExtractionResult extract(String query, IntentType intentType) {
        if (query == null || query.trim().isEmpty()) {
            return ExtractionResult.failure("查询内容为空", EXTRACTOR_NAME);
        }

        if (intentType == null) {
            return ExtractionResult.failure("意图类型为空", EXTRACTOR_NAME);
        }

        log.debug("[{}] 开始提取参数: intent={}, query={}", EXTRACTOR_NAME, intentType, query);

        try {
            Map<String, Object> parameters;
            List<String> missingParams = new ArrayList<>();

            switch (intentType) {
                case WEATHER_QUERY:
                    parameters = extractWeatherParameters(query, missingParams);
                    break;
                case CALENDAR_QUERY:
                case CALENDAR_CREATE:
                    parameters = extractCalendarParameters(query, missingParams);
                    break;
                case TODO_QUERY:
                case TODO_CREATE:
                    parameters = extractTodoParameters(query, missingParams);
                    break;
                case ROUTE_QUERY:
                    parameters = extractRouteParameters(query, missingParams);
                    break;
                case SMALL_TALK:
                    parameters = new HashMap<>(); // 闲聊不需要参数
                    break;
                default:
                    parameters = new HashMap<>();
            }

            double confidence = calculateConfidence(parameters, intentType, missingParams);
            log.debug("[{}] 参数提取完成: {}, 缺失: {}, 置信度: {}",
                    EXTRACTOR_NAME, parameters, missingParams, confidence);

            // 如果有缺失的关键参数，返回部分成功
            if (!missingParams.isEmpty() && confidence < 0.7) {
                return ExtractionResult.partial(
                        parameters,
                        "缺少必要参数: " + String.join(", ", missingParams),
                        confidence
                );
            }

            return ExtractionResult.builder()
                    .success(true)
                    .parameters(parameters)
                    .extractorName(EXTRACTOR_NAME)
                    .confidence(confidence)
                    .build();

        } catch (Exception e) {
            log.error("[{}] 参数提取异常: {}", EXTRACTOR_NAME, e.getMessage(), e);
            return ExtractionResult.failure("参数提取异常: " + e.getMessage(), EXTRACTOR_NAME);
        }
    }

    @Override
    public String getName() {
        return EXTRACTOR_NAME;
    }

    @Override
    public String getDescription() {
        return "基于规则的参数提取器，使用预定义规则和正则表达式提取结构化参数";
    }

    @Override
    public boolean supports(IntentType intentType) {
        return intentType == IntentType.WEATHER_QUERY
                || intentType == IntentType.CALENDAR_QUERY
                || intentType == IntentType.CALENDAR_CREATE
                || intentType == IntentType.TODO_QUERY
                || intentType == IntentType.TODO_CREATE
                || intentType == IntentType.ROUTE_QUERY
                || intentType == IntentType.SMALL_TALK;
    }

    /**
     * 提取天气查询参数
     *
     * @param query          用户查询
     * @param missingParams  缺失参数列表
     * @return 参数Map
     */
    private Map<String, Object> extractWeatherParameters(String query, List<String> missingParams) {
        Map<String, Object> params = new HashMap<>();

        // 提取城市
        String city = extractCity(query);
        if (city != null) {
            params.put("city", city);
        } else {
            missingParams.add("city");
        }

        // 提取日期（天气查询通常关注今天/明天）
        LocalDate date = extractDate(query);
        if (date != null) {
            params.put("date", date.toString());
            params.put("dateType", getDateTypeDescription(date));
        } else {
            // 默认今天
            params.put("date", LocalDate.now().toString());
            params.put("dateType", "今天");
        }

        return params;
    }

    /**
     * 提取日历相关参数
     *
     * @param query          用户查询
     * @param missingParams  缺失参数列表
     * @return 参数Map
     */
    private Map<String, Object> extractCalendarParameters(String query, List<String> missingParams) {
        Map<String, Object> params = new HashMap<>();

        // 提取标题/事件名称
        String title = extractEventTitle(query);
        if (title != null) {
            params.put("title", title);
        } else {
            missingParams.add("title");
        }

        // 提取日期
        LocalDate date = extractDate(query);
        if (date != null) {
            params.put("date", date.toString());
            params.put("startDate", date.toString());
        } else {
            missingParams.add("date");
        }

        // 提取时间
        LocalDateTime dateTime = extractDateTime(query);
        if (dateTime != null) {
            params.put("startTime", dateTime.toString());
        }

        // 提取地点
        String location = extractLocation(query);
        if (location != null) {
            params.put("location", location);
        }

        // 提取时长
        Integer duration = extractDurationMinutes(query);
        if (duration != null) {
            params.put("durationMinutes", duration);
        }

        return params;
    }

    /**
     * 提取待办事项参数
     *
     * @param query          用户查询
     * @param missingParams  缺失参数列表
     * @return 参数Map
     */
    private Map<String, Object> extractTodoParameters(String query, List<String> missingParams) {
        Map<String, Object> params = new HashMap<>();

        // 提取标题/任务名称
        String title = extractTaskTitle(query);
        if (title != null) {
            params.put("title", title);
        } else {
            missingParams.add("title");
        }

        // 提取优先级
        String priority = extractPriority(query);
        if (priority != null) {
            params.put("priority", priority);
        } else {
            params.put("priority", "medium"); // 默认中等优先级
        }

        // 提取截止日期
        LocalDate dueDate = extractDate(query);
        if (dueDate != null) {
            params.put("dueDate", dueDate.toString());
        }

        // 提取截止时间
        LocalDateTime dueDateTime = extractDateTime(query);
        if (dueDateTime != null) {
            params.put("dueDateTime", dueDateTime.toString());
        }

        return params;
    }

    /**
     * 提取路线规划参数
     *
     * @param query          用户查询
     * @param missingParams  缺失参数列表
     * @return 参数Map
     */
    private Map<String, Object> extractRouteParameters(String query, List<String> missingParams) {
        Map<String, Object> params = new HashMap<>();

        // 提取起点
        String origin = extractOrigin(query);
        if (origin != null) {
            params.put("origin", origin);
        }

        // 提取终点（必要参数）
        String destination = extractDestination(query);
        if (destination != null) {
            params.put("destination", destination);
        } else {
            missingParams.add("destination");
        }

        // 提取出行方式
        String mode = extractRouteMode(query);
        if (mode != null) {
            params.put("mode", mode);
        } else {
            params.put("mode", "driving"); // 默认驾车
        }

        return params;
    }

    /**
     * 从查询中提取城市名
     */
    private String extractCity(String query) {
        // 1. 先匹配预定义城市列表
        for (String city : COMMON_CITIES) {
            if (query.contains(city)) {
                return city;
            }
        }

        // 2. 移除时间关键词后再提取城市
        String queryWithoutTime = removeTimeKeywords(query);

        // 3. 查找"天气"关键词的位置，然后提取其前面的城市名
        int weatherIndex = queryWithoutTime.indexOf("天气");
        if (weatherIndex > 0) {
            // 提取"天气"前面的2-6个中文字符作为城市名
            int start = Math.max(0, weatherIndex - 6);
            int length = weatherIndex - start;
            if (length >= 2) {
                String potentialCity = queryWithoutTime.substring(start, weatherIndex);
                // 去掉末尾可能的"的"字符
                if (potentialCity.endsWith("的")) {
                    potentialCity = potentialCity.substring(0, potentialCity.length() - 1);
                }
                // 确保城市名不包含时间关键词
                if (potentialCity.length() >= 2 && !containsTimeKeyword(potentialCity)) {
                    return potentialCity;
                }
            }
        }

        // 4. 尝试匹配"XX天气"或"XX的天气"模式作为后备
        Pattern cityPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,6})(?:的)?天气");
        Matcher matcher = cityPattern.matcher(queryWithoutTime);
        while (matcher.find()) {
            String city = matcher.group(1);
            // 确保城市名不包含时间关键词
            if (city.length() >= 2 && !containsTimeKeyword(city)) {
                return city;
            }
        }

        return null;
    }

    /**
     * 移除时间关键词
     */
    private String removeTimeKeywords(String query) {
        String[] timeKeywords = {"今天", "明天", "后天", "昨天", "前天", "大后天",
                "早上", "上午", "下午", "晚上", "现在", "何时", "这里", "那里"};
        String result = query;
        for (String kw : timeKeywords) {
            result = result.replace(kw, "");
        }
        return result;
    }

    /**
     * 检查是否包含时间关键词
     */
    private boolean containsTimeKeyword(String word) {
        String[] timeKeywords = {"今天", "明天", "后天", "昨天", "前天", "大后天",
                "早上", "上午", "下午", "晚上", "现在", "何时", "这里", "那里"};
        for (String kw : timeKeywords) {
            if (word.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是不相关的时间词或其他词
     */
    private boolean isTimeOrOtherKeyword(String word) {
        String[] timeKeywords = {"今天", "明天", "后天", "昨天", "前天", "大后天",
                "早上", "上午", "下午", "晚上", "现在", "何时", "这里", "那里"};
        for (String kw : timeKeywords) {
            if (word.contains(kw) || kw.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取日期
     */
    private LocalDate extractDate(String query) {
        // 1. 先匹配相对日期关键词
        for (Map.Entry<String, LocalDate> entry : DATE_KEYWORDS.entrySet()) {
            if (query.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 2. 匹配星期几
        LocalDate weekDay = extractWeekDay(query);
        if (weekDay != null) {
            return weekDay;
        }

        // 3. 匹配具体日期格式
        Matcher matcher = DATE_NUMBER_PATTERN.matcher(query);
        if (matcher.find()) {
            try {
                if (matcher.group(1) != null) {
                    // yyyy-MM-dd 或 yyyy/MM/dd
                    int year = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));
                    return LocalDate.of(year, month, day);
                } else if (matcher.group(4) != null) {
                    // M月D日
                    int month = Integer.parseInt(matcher.group(4));
                    int day = Integer.parseInt(matcher.group(5));
                    int year = LocalDate.now().getYear();
                    // 如果月份已过，假设是明年
                    if (month < LocalDate.now().getMonthValue()) {
                        year++;
                    }
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception e) {
                log.debug("日期解析失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 提取星期几
     */
    private LocalDate extractWeekDay(String query) {
        String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日",
                "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        String[] weekDaysEn = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

        LocalDate today = LocalDate.now();
        int currentDayOfWeek = today.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday

        for (int i = 0; i < weekDays.length; i++) {
            if (query.contains(weekDays[i]) || query.contains(weekDaysEn[i % 7])) {
                int targetDayOfWeek = (i % 7) + 1;
                int daysToAdd = targetDayOfWeek - currentDayOfWeek;
                if (daysToAdd <= 0) {
                    daysToAdd += 7; // 下周
                }
                return today.plusDays(daysToAdd);
            }
        }

        // 处理"下周X"
        if (query.contains("下周")) {
            for (int i = 0; i < 7; i++) {
                if (query.contains("下周" + weekDays[i]) || query.contains("下周" + weekDays[i + 7])) {
                    int targetDayOfWeek = (i % 7) + 1;
                    int daysToAdd = 7 - currentDayOfWeek + targetDayOfWeek;
                    return today.plusDays(daysToAdd);
                }
            }
        }

        return null;
    }

    /**
     * 提取日期时间
     */
    private LocalDateTime extractDateTime(String query) {
        LocalDate date = extractDate(query);
        if (date == null) {
            date = LocalDate.now();
        }

        // 提取时间
        Matcher matcher = TIME_PATTERN.matcher(query);
        if (matcher.find()) {
            try {
                int hour = 0;
                int minute = 0;

                if (matcher.group(1) != null) {
                    hour = Integer.parseInt(matcher.group(1));
                    if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                        minute = Integer.parseInt(matcher.group(2));
                    }
                } else if (matcher.group(4) != null) {
                    String period = matcher.group(3);
                    hour = Integer.parseInt(matcher.group(4));
                    if (matcher.group(5) != null && !matcher.group(5).isEmpty()) {
                        minute = Integer.parseInt(matcher.group(5));
                    }

                    // 处理上午/下午
                    if ("下午".equals(period) || "晚上".equals(period)) {
                        if (hour < 12) {
                            hour += 12;
                        }
                    } else if ("上午".equals(period) || "早上".equals(period)) {
                        if (hour == 12) {
                            hour = 0;
                        }
                    }
                }

                // 验证时间有效性
                if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                    return date.atTime(hour, minute);
                }
            } catch (Exception e) {
                log.debug("时间解析失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 提取事件标题
     */
    private String extractEventTitle(String query) {
        // 匹配"创建一个XX会议/活动/日程"模式
        Pattern titlePattern = Pattern.compile(
                "(?:创建|添加|新建|安排)\\s*(?:一个|个)?\\s*([^\\s]{2,20})(?:会议|活动|日程|约会|事情)"
        );
        Matcher matcher = titlePattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1) + "会议";
        }

        // 匹配"XX会议/活动"模式
        Pattern simplePattern = Pattern.compile("([^\\s]{2,20})(?:会议|活动|日程)");
        Matcher simpleMatcher = simplePattern.matcher(query);
        if (simpleMatcher.find()) {
            return simpleMatcher.group(0);
        }

        return null;
    }

    /**
     * 提取任务标题
     */
    private String extractTaskTitle(String query) {
        // 匹配"添加一个XX待办/任务"模式
        Pattern titlePattern = Pattern.compile(
                "(?:创建|添加|新建)\\s*(?:一个|个)?\\s*([^\\s]{2,30})(?:待办|任务|提醒|事情)"
        );
        Matcher matcher = titlePattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 匹配"记得XX"模式
        Pattern rememberPattern = Pattern.compile("记得([^\\s]{2,30})(?:要|去做|完成)?");
        Matcher rememberMatcher = rememberPattern.matcher(query);
        if (rememberMatcher.find()) {
            return rememberMatcher.group(1);
        }

        return null;
    }

    /**
     * 提取地点
     */
    private String extractLocation(String query) {
        // 匹配"在XX"、"去XX"、"地点是XX"模式
        Pattern locationPattern = Pattern.compile(
                "(?:在|去|到|地点[是为]|位置[是为])\\s*([^\\s,，。]{2,20})"
        );
        Matcher matcher = locationPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 提取优先级
     */
    private String extractPriority(String query) {
        for (Map.Entry<String, String> entry : PRIORITY_KEYWORDS.entrySet()) {
            if (query.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 提取时长（分钟）
     */
    private Integer extractDurationMinutes(String query) {
        Matcher matcher = DURATION_PATTERN.matcher(query);
        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                switch (unit) {
                    case "分钟":
                        return value;
                    case "小时":
                        return value * 60;
                    case "天":
                        return value * 60 * 24;
                    case "周":
                        return value * 60 * 24 * 7;
                    default:
                        return value;
                }
            } catch (NumberFormatException e) {
                log.debug("时长解析失败");
            }
        }
        return null;
    }

    /**
     * 提取起点
     */
    private String extractOrigin(String query) {
        // 匹配"从XX到XX"、"从XX出发"模式
        Pattern originPattern = Pattern.compile(
                "从\\s*([^\\s,，。到]+?)(?:到|去|出发|开始|走)"
        );
        Matcher matcher = originPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 匹配"起点[是为]XX"模式
        Pattern startPattern = Pattern.compile(
                "(?:起点|出发地|起始地)[是为]?\\s*([^\\s,，。]+)"
        );
        Matcher startMatcher = startPattern.matcher(query);
        if (startMatcher.find()) {
            return startMatcher.group(1).trim();
        }

        return null;
    }

    /**
     * 提取终点
     */
    private String extractDestination(String query) {
        // 匹配"从XX到XX"、"去XX"模式
        Pattern destPattern = Pattern.compile(
                "(?:到|去|往)\\s*([^\\s,，。]+?)(?:怎么走|怎么坐|的路线|导航|方向|$)"
        );
        Matcher matcher = destPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 匹配"终点[是为]XX"模式
        Pattern endPattern = Pattern.compile(
                "(?:终点|目的地|到达地)[是为]?\\s*([^\\s,，。]+)"
        );
        Matcher endMatcher = endPattern.matcher(query);
        if (endMatcher.find()) {
            return endMatcher.group(1).trim();
        }

        // 匹配"怎么去XX"模式
        Pattern howPattern = Pattern.compile("怎么[去到往]\\s*([^\\s,，。]+)");
        Matcher howMatcher = howPattern.matcher(query);
        if (howMatcher.find()) {
            return howMatcher.group(1).trim();
        }

        return null;
    }

    /**
     * 提取出行方式
     */
    private String extractRouteMode(String query) {
        for (Map.Entry<String, String> entry : ROUTE_MODE_KEYWORDS.entrySet()) {
            if (query.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 获取日期类型描述
     */
    private String getDateTypeDescription(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "今天";
        } else if (date.equals(today.plusDays(1))) {
            return "明天";
        } else if (date.equals(today.plusDays(2))) {
            return "后天";
        } else {
            return date.format(DateTimeFormatter.ofPattern("MM月dd日"));
        }
    }

    /**
     * 计算参数提取的置信度
     */
    private double calculateConfidence(Map<String, Object> parameters,
                                       IntentType intentType,
                                       List<String> missingParams) {
        if (parameters == null || parameters.isEmpty()) {
            return 0.0;
        }

        double baseScore = 0.3;
        double paramScore = Math.min(parameters.size() * 0.1, 0.4);

        // 根据意图类型检查关键参数
        boolean hasKeyParams = false;
        switch (intentType) {
            case WEATHER_QUERY:
                hasKeyParams = parameters.containsKey("city");
                break;
            case CALENDAR_CREATE:
                hasKeyParams = parameters.containsKey("title");
                break;
            case TODO_CREATE:
                hasKeyParams = parameters.containsKey("title");
                break;
            case ROUTE_QUERY:
                hasKeyParams = parameters.containsKey("destination");
                break;
            default:
                hasKeyParams = !parameters.isEmpty();
        }

        if (hasKeyParams) {
            baseScore += 0.3;
        }

        // 如果有缺失的关键参数，降低置信度
        if (!missingParams.isEmpty()) {
            baseScore -= (missingParams.size() * 0.1);
        }

        return Math.max(0.0, Math.min(baseScore + paramScore, 0.95));
    }
}
