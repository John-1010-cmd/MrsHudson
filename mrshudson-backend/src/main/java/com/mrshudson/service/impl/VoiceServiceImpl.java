package com.mrshudson.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.config.VoiceProperties;
import com.mrshudson.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 语音服务实现类（讯飞语音）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final VoiceProperties voiceProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String speechToText(MultipartFile audioFile) {
        // 默认使用wav格式，16k采样率
        return speechToText(audioFile, "wav", 16000);
    }

    @Override
    public String speechToText(MultipartFile audioFile, String format, Integer sampleRate) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new RuntimeException("音频文件不能为空");
        }

        // 检查文件大小（最大10MB）
        if (audioFile.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("音频文件大小不能超过10MB");
        }

        try {
            // 模拟模式：直接返回模拟文本（用于开发和测试）
            if (voiceProperties.isMockMode()) {
                log.info("[模拟模式] 语音识别请求，文件大小: {} bytes, 格式: {}, 采样率: {}",
                        audioFile.getSize(), format, sampleRate);
                // 模拟处理延迟
                Thread.sleep(500);
                return voiceProperties.getMockText();
            }

            // 使用讯飞语音识别
            return recognizeByXfyun(audioFile, format, sampleRate);

        } catch (Exception e) {
            log.error("语音识别失败, audioSize={}bytes, format={}, sampleRate={}",
                    audioFile.getSize(), format, sampleRate, e);
            throw new RuntimeException("语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 使用讯飞进行语音识别（WebAPI方式）
     */
    private String recognizeByXfyun(MultipartFile audioFile, String format, Integer sampleRate) throws Exception {
        log.info("使用讯飞进行语音识别");

        String appId = voiceProperties.getXfyunAppId();
        String apiSecret = voiceProperties.getXfyunApiSecret();
        String apiKey = voiceProperties.getXfyunApiKey();

        if (appId == null || appId.isEmpty() || apiSecret == null || apiSecret.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("讯飞API配置不完整，请检查appId、apiSecret、apiKey");
        }

        // 1. 构建鉴权URL
        String authUrl = buildXfyunAuthUrl(
                "http://iat-api.xfyun.cn/v2/iat",
                apiKey,
                apiSecret
        );

        // 2. 读取音频并转为Base64
        byte[] audioBytes = audioFile.getBytes();
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

        // 3. 构建请求体
        JSONObject requestBody = new JSONObject();

        // 公共参数
        JSONObject common = new JSONObject();
        common.put("app_id", appId);

        // 业务参数
        JSONObject business = new JSONObject();
        business.put("language", "zh_cn");
        business.put("domain", "iat");
        business.put("accent", "mandarin");
        // 音频格式
        String xfyunFormat = mapToXfyunFormat(format);
        business.put("format", xfyunFormat);
        business.put("sample_rate", String.valueOf(sampleRate));
        business.put("vad_eos", 2000);

        // 数据参数
        JSONObject data = new JSONObject();
        data.put("status", 2); // 2表示一次性发送全部数据
        data.put("encoding", "raw");
        data.put("audio", audioBase64);

        requestBody.put("common", common);
        requestBody.put("business", business);
        requestBody.put("data", data);

        // 4. 发送HTTP请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(authUrl, entity, String.class);

        // 5. 解析结果
        String responseBody = response.getBody();
        log.debug("讯飞识别响应: {}", responseBody);

        JSONObject result = JSON.parseObject(responseBody);

        // 检查错误
        if (result.containsKey("code")) {
            int code = result.getIntValue("code");
            if (code != 0) {
                String message = result.getString("message");
                log.error("讯飞识别失败: code={}, message={}", code, message);
                throw new RuntimeException("讯飞识别失败: " + message);
            }
        }

        // 解析识别结果
        if (result.containsKey("data")) {
            JSONObject dataObj = result.getJSONObject("data");
            if (dataObj.containsKey("result")) {
                StringBuilder text = new StringBuilder();
                JSONObject resultObj = dataObj.getJSONObject("result");
                if (resultObj.containsKey("ws")) {
                    var wsArray = resultObj.getJSONArray("ws");
                    for (int i = 0; i < wsArray.size(); i++) {
                        JSONObject ws = wsArray.getJSONObject(i);
                        if (ws.containsKey("cw")) {
                            var cwArray = ws.getJSONArray("cw");
                            for (int j = 0; j < cwArray.size(); j++) {
                                JSONObject cw = cwArray.getJSONObject(j);
                                if (cw.containsKey("w")) {
                                    text.append(cw.getString("w"));
                                }
                            }
                        }
                    }
                }
                String recognizedText = text.toString();
                log.info("讯飞识别结果: {}", recognizedText);
                return recognizedText.isEmpty() ? "未识别到语音内容" : recognizedText;
            }
        }

        return "语音识别失败";
    }

    /**
     * 映射音频格式到讯飞格式
     */
    private String mapToXfyunFormat(String format) {
        return switch (format.toLowerCase()) {
            case "wav" -> "audio/L16;rate=16000";
            case "pcm" -> "raw";
            case "mp3" -> "lame";
            case "speex" -> "speex";
            default -> "audio/L16;rate=16000";
        };
    }

    /**
     * 构建讯飞鉴权URL
     */
    private String buildXfyunAuthUrl(String requestUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(requestUrl);

        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());

        // 1. 构建signature_origin
        String signatureOrigin = String.format("host: %s\ndate: %s\nGET %s HTTP/1.1",
                url.getHost(), date, url.getPath());

        // 2. 使用HMAC-SHA256签名
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(spec);
        byte[] signatureBytes = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signatureBytes);

        // 3. 构建authorization
        String authorizationOrigin = String.format("api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signature);
        String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        // 4. 构建完整URL
        StringBuilder urlBuilder = new StringBuilder(requestUrl);
        urlBuilder.append("?authorization=");
        urlBuilder.append(URLEncoder.encode(authorization, StandardCharsets.UTF_8));
        urlBuilder.append("&date=");
        urlBuilder.append(URLEncoder.encode(date, StandardCharsets.UTF_8));
        urlBuilder.append("&host=");
        urlBuilder.append(URLEncoder.encode(url.getHost(), StandardCharsets.UTF_8));

        return urlBuilder.toString();
    }

    /**
     * 将音频文件转换为Base64编码（用于某些API）
     */
    private String encodeAudioToBase64(MultipartFile audioFile) throws IOException {
        byte[] bytes = audioFile.getBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }
}
