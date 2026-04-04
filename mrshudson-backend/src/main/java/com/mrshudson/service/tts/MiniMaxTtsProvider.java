package com.mrshudson.service.tts;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.config.VoiceProperties;
import com.mrshudson.service.GitHubStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * MiniMax TTS 提供商
 * 使用 MiniMax 语音合成 API 进行异步语音合成
 *
 * API 流程:
 * 1. POST /v1/t2a_async_v2 - 创建异步任务
 * 2. GET /v1/query/t2a_async_query_v2?task_id={task_id} - 轮询查询任务状态
 * 3. 任务完成后，通过 file_id 获取音频下载 URL
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "voice.tts-provider", havingValue = "minimax")
@RequiredArgsConstructor
public class MiniMaxTtsProvider implements TtsProvider {

    private static final String BASE_URL = "https://api.minimax.io";
    private static final String CREATE_TASK_ENDPOINT = "/v1/t2a_async_v2";
    private static final String QUERY_TASK_ENDPOINT = "/v1/query/t2a_async_query_v2";

    private final VoiceProperties voiceProperties;

    @Autowired(required = false)
    private GitHubStorageService gitHubStorageService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public String synthesize(String text) {
        String apiKey = voiceProperties.getMinimaxApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("MiniMax API Key 未配置，跳过语音合成");
            return null;
        }

        // 文本长度限制检查（MiniMax 支持最长 50000 字符）
        String truncatedText = text;
        if (text.length() > 50000) {
            truncatedText = text.substring(0, 50000);
            log.warn("文本过长，已截断至50000字符");
        }

        // 实际应用中可能需要更短的限制，根据 TTS 超时设置
        if (text.length() > 1000) {
            truncatedText = text.substring(0, 1000) + "...";
            log.warn("文本过长，已截断至1000字符（适配TTS超时设置）");
        }

        if (voiceProperties.isMockMode()) {
            log.info("[模拟模式] MiniMax TTS 语音合成请求，文本长度: {} 字符", truncatedText.length());
            return null;
        }

        return doMiniMaxSynthesize(truncatedText, apiKey);
    }

    /**
     * 执行 MiniMax TTS 合成
     */
    private String doMiniMaxSynthesize(String text, String apiKey) {
        try {
            log.info("使用 MiniMax API 进行语音合成，文本长度: {} 字符", text.length());

            // 步骤1: 创建异步任务
            TaskCreateResponse taskResponse = createTask(text, apiKey);
            if (taskResponse == null || taskResponse.taskId == null) {
                log.error("创建 MiniMax TTS 任务失败");
                return null;
            }

            log.debug("MiniMax TTS 任务创建成功，taskId: {}", taskResponse.taskId);

            // 步骤2: 轮询查询任务状态
            TaskQueryResponse queryResponse = pollTaskStatus(taskResponse.taskId, apiKey);
            if (queryResponse == null || !"success".equalsIgnoreCase(queryResponse.status)) {
                log.error("MiniMax TTS 任务执行失败，状态: {}",
                        queryResponse != null ? queryResponse.status : "null");
                return null;
            }

            log.debug("MiniMax TTS 任务完成，fileId: {}", queryResponse.fileId);

            // 步骤3: 下载音频文件
            return downloadAndStoreAudio(queryResponse.fileId, apiKey);

        } catch (Exception e) {
            log.error("MiniMax TTS 合成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建异步 TTS 任务
     */
    private TaskCreateResponse createTask(String text, String apiKey) throws IOException {
        String url = BASE_URL + CREATE_TASK_ENDPOINT;

        // 构建请求体
        JSONObject voiceSetting = new JSONObject();
        voiceSetting.put("voice_id", voiceProperties.getMinimaxTtsVoice());
        voiceSetting.put("speed", voiceProperties.getMinimaxTtsSpeed());
        voiceSetting.put("vol", 1.0);
        voiceSetting.put("pitch", 1.0);

        JSONObject audioSetting = new JSONObject();
        audioSetting.put("audio_sample_rate", 32000);
        audioSetting.put("bitrate", 128000);
        audioSetting.put("format", "mp3");
        audioSetting.put("channel", 2);

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", voiceProperties.getMinimaxTtsModel());
        requestBody.put("text", text);
        requestBody.put("language_boost", "auto");
        requestBody.put("voice_setting", voiceSetting);
        requestBody.put("audio_setting", audioSetting);

        RequestBody body = RequestBody.create(
                requestBody.toJSONString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("创建 MiniMax TTS 任务失败，HTTP状态码: {}", response.code());
                if (response.body() != null) {
                    log.error("响应内容: {}", response.body().string());
                }
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null) {
                return null;
            }

            JSONObject json = JSON.parseObject(responseBody);
            TaskCreateResponse result = new TaskCreateResponse();
            result.taskId = json.getLong("task_id");
            result.taskToken = json.getString("task_token");
            result.fileId = json.getLong("file_id");

            // 检查 base_resp 状态
            JSONObject baseResp = json.getJSONObject("base_resp");
            if (baseResp != null && baseResp.getIntValue("status_code") != 0) {
                log.error("MiniMax TTS 创建任务失败: {}", baseResp.getString("status_msg"));
                return null;
            }

            return result;
        }
    }

    /**
     * 轮询查询任务状态
     */
    private TaskQueryResponse pollTaskStatus(long taskId, String apiKey) throws InterruptedException {
        String url = BASE_URL + QUERY_TASK_ENDPOINT + "?task_id=" + taskId;
        int maxPolls = voiceProperties.getMinimaxTtsMaxPolls();
        int pollInterval = voiceProperties.getMinimaxTtsPollInterval();

        for (int i = 0; i < maxPolls; i++) {
            // 等待轮询间隔
            Thread.sleep(pollInterval);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("查询 MiniMax TTS 任务状态失败，HTTP状态码: {}", response.code());
                    continue;
                }

                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null) {
                    continue;
                }

                JSONObject json = JSON.parseObject(responseBody);
                TaskQueryResponse result = new TaskQueryResponse();
                result.taskId = json.getLong("task_id");
                result.status = json.getString("status");
                result.fileId = json.getLong("file_id");

                // 检查 base_resp 状态
                JSONObject baseResp = json.getJSONObject("base_resp");
                if (baseResp != null && baseResp.getIntValue("status_code") != 0) {
                    log.error("MiniMax TTS 查询任务失败: {}", baseResp.getString("status_msg"));
                    return null;
                }

                log.debug("MiniMax TTS 任务状态: {} (轮询 {}/{})", result.status, i + 1, maxPolls);

                // 检查任务是否完成
                if ("success".equalsIgnoreCase(result.status) || "completed".equalsIgnoreCase(result.status)) {
                    return result;
                }

                // 检查任务是否失败
                if ("failed".equalsIgnoreCase(result.status) || "expired".equalsIgnoreCase(result.status)) {
                    log.error("MiniMax TTS 任务执行失败，状态: {}", result.status);
                    return result;
                }

                // 继续轮询 (processing 状态)

            } catch (IOException e) {
                log.warn("查询 MiniMax TTS 任务状态时发生异常: {}", e.getMessage());
            }
        }

        log.error("MiniMax TTS 任务轮询超时，taskId: {}", taskId);
        return null;
    }

    /**
     * 下载并存储音频文件
     */
    private String downloadAndStoreAudio(Long fileId, String apiKey) throws IOException {
        String url = BASE_URL + "/v1/files/retrieve_content?file_id=" + fileId;

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("下载 MiniMax TTS 音频文件失败，HTTP状态码: {}", response.code());
                return null;
            }

            if (response.body() == null) {
                log.error("下载 MiniMax TTS 音频文件失败，响应体为空");
                return null;
            }

            // 生成文件名
            String fileName = "tts_minimax_" + System.currentTimeMillis() + ".mp3";
            String storagePath = voiceProperties.getTtsStoragePath();
            Path absolutePath = Paths.get(storagePath).toAbsolutePath().normalize();
            Path filePath = absolutePath.resolve(fileName);

            // 确保目录存在
            Files.createDirectories(absolutePath);

            // 保存音频文件
            byte[] audioData = response.body().bytes();
            Files.write(filePath, audioData);

            log.info("MiniMax TTS 音频文件保存成功，文件: {}, 大小: {} bytes", fileName, audioData.length);

            // 尝试上传到 GitHub
            if (gitHubStorageService != null && voiceProperties.isUploadToGithub()) {
                try {
                    String gitHubUrl = gitHubStorageService.uploadFile(fileName, audioData);
                    if (gitHubUrl != null) {
                        log.info("MiniMax TTS 文件已上传到 GitHub: {}", gitHubUrl);
                        Files.deleteIfExists(filePath);
                        return gitHubUrl;
                    }
                } catch (Exception e) {
                    log.warn("上传到 GitHub 失败，回退到本地存储: {}", e.getMessage());
                }
            }

            // 返回本地 URL
            String baseUrl = voiceProperties.getTtsBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                return baseUrl + "/" + storagePath + fileName;
            }
            return "/" + storagePath + fileName;
        }
    }

    /**
     * 创建任务响应
     */
    private static class TaskCreateResponse {
        Long taskId;
        String taskToken;
        Long fileId;
    }

    /**
     * 查询任务响应
     */
    private static class TaskQueryResponse {
        Long taskId;
        String status;  // processing, success, failed, expired
        Long fileId;
        String audioUrl;
    }
}
