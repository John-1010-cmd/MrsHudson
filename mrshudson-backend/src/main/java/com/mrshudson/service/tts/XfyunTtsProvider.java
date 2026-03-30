package com.mrshudson.service.tts;

import cn.xfyun.api.TtsClient;
import cn.xfyun.model.response.TtsResponse;
import cn.xfyun.service.tts.AbstractTtsWebSocketListener;
import com.mrshudson.config.VoiceProperties;
import com.mrshudson.service.GitHubStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * 讯飞 TTS 提供商
 * 使用讯飞语音 SDK 进行语音合成
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "voice.tts-provider", havingValue = "xfyun", matchIfMissing = true)
@RequiredArgsConstructor
public class XfyunTtsProvider implements TtsProvider {

    private final VoiceProperties voiceProperties;

    @Autowired(required = false)
    private GitHubStorageService gitHubStorageService;

    @Override
    public String synthesize(String text) {
        String appId = voiceProperties.getXfyunAppId();
        String apiKey = voiceProperties.getXfyunApiKey();
        String apiSecret = voiceProperties.getXfyunApiSecret();

        if (appId == null || appId.isEmpty() || apiSecret == null || apiSecret.isEmpty()
                || apiKey == null || apiKey.isEmpty()) {
            log.warn("讯飞API配置不完整，跳过语音合成");
            return null;
        }

        String truncatedText = text;
        if (text.length() > 1000) {
            truncatedText = text.substring(0, 1000) + "...";
            log.warn("文本过长，已截断至1000字符");
        }

        if (voiceProperties.isMockMode()) {
            log.info("[模拟模式] 语音合成请求，文本长度: {} 字符", truncatedText.length());
            return null;
        }

        return doXfyunSynthesize(truncatedText);
    }

    private String doXfyunSynthesize(String text) {
        try {
            log.info("使用讯飞SDK进行语音合成，文本长度: {} 字符", text.length());

            String appId = voiceProperties.getXfyunAppId();
            String apiSecret = voiceProperties.getXfyunApiSecret();
            String apiKey = voiceProperties.getXfyunApiKey();

            String fileName = "tts_" + System.currentTimeMillis() + ".mp3";
            String storagePath = voiceProperties.getTtsStoragePath();
            Path absolutePath = Paths.get(storagePath).toAbsolutePath().normalize();
            Path filePath = absolutePath.resolve(fileName);

            Files.createDirectories(absolutePath);

            CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

            TtsClient client = new TtsClient.Builder()
                    .signature(appId, apiKey, apiSecret)
                    .vcn(voiceProperties.getXfyunTtsVoice())
                    .speed(50)
                    .volume(50)
                    .pitch(50)
                    .aue("lame")
                    .build();

            client.send(text, new AbstractTtsWebSocketListener(filePath.toFile()) {
                @Override
                public void onSuccess(byte[] bytes) {
                    // SDK 自动写入文件
                }

                @Override
                public void onBusinessFail(WebSocket webSocket, TtsResponse ttsResponse) {
                    log.error("语音合成业务失败: {}", ttsResponse);
                    resultFuture.complete(false);
                }

                @Override
                public void onFail(WebSocket webSocket, Throwable t, Response response) {
                    log.error("语音合成失败: {}", t.getMessage(), t);
                    resultFuture.completeExceptionally(t);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    log.info("语音合成连接关闭: code={}, reason={}", code, reason);
                    if (!resultFuture.isDone()) {
                        resultFuture.complete(true);
                    }
                }
            });

            Boolean success = resultFuture.get();

            if (success) {
                log.info("语音合成成功，文件: {}, 大小: {} bytes", fileName, Files.size(filePath));

                // 尝试上传到 GitHub
                if (gitHubStorageService != null && voiceProperties.isUploadToGithub()) {
                    try {
                        byte[] fileContent = Files.readAllBytes(filePath);
                        String gitHubUrl = gitHubStorageService.uploadFile(fileName, fileContent);
                        if (gitHubUrl != null) {
                            log.info("TTS 文件已上传到 GitHub: {}", gitHubUrl);
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
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException("TTS 合成失败: " + e.getMessage(), e);
        }
    }
}
