package com.mrshudson.service.impl;

import cn.xfyun.api.IatClient;
import cn.xfyun.model.response.iat.IatResponse;
import cn.xfyun.service.iat.AbstractIatWebSocketListener;
import com.mrshudson.config.VoiceProperties;
import com.mrshudson.service.VoiceService;
import com.mrshudson.service.tts.TtsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * 语音服务实现类（讯飞语音SDK方式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final VoiceProperties voiceProperties;

    @Autowired(required = false)
    private TtsProvider ttsProvider;

    @Override
    public String speechToText(MultipartFile audioFile) {
        return speechToText(audioFile, "wav", 16000);
    }

    @Override
    public String speechToText(MultipartFile audioFile, String format, Integer sampleRate) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new RuntimeException("音频文件不能为空");
        }

        if (audioFile.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("音频文件大小不能超过10MB");
        }

        try {
            if (voiceProperties.isMockMode()) {
                log.info("[模拟模式] 语音识别请求，文件大小: {} bytes", audioFile.getSize());
                Thread.sleep(500);
                return voiceProperties.getMockText();
            }

            return recognizeByXfyunSdk(audioFile, format, sampleRate);

        } catch (Exception e) {
            log.error("语音识别失败, audioSize={}bytes", audioFile.getSize(), e);
            throw new RuntimeException("语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 使用讯飞SDK进行语音识别
     */
    private String recognizeByXfyunSdk(MultipartFile audioFile, String format, Integer sampleRate) throws Exception {
        log.info("使用讯飞SDK进行语音识别");

        String appId = voiceProperties.getXfyunAppId();
        String apiSecret = voiceProperties.getXfyunApiSecret();
        String apiKey = voiceProperties.getXfyunApiKey();

        if (appId == null || appId.isEmpty() || apiSecret == null || apiSecret.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("讯飞API配置不完整，请检查appId、apiSecret、apiKey");
        }

        // 保存临时文件
        File tempFile = File.createTempFile("asr_", "." + format);
        audioFile.transferTo(tempFile);

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        StringBuilder resultText = new StringBuilder();

        // 创建IatClient
        IatClient client = new IatClient.Builder()
                .signature(appId, apiKey, apiSecret)
                .language("zh_cn")
                .domain("iat")
                .accent("mandarin")
                .format("audio/L16;rate=" + sampleRate)
                .vad_eos(2000)
                .build();

        // 发送请求
        client.send(tempFile, new AbstractIatWebSocketListener() {
            @Override
            public void onSuccess(WebSocket webSocket, IatResponse iatResponse) {
                if (iatResponse != null && iatResponse.getData() != null && iatResponse.getData().getResult() != null) {
                    cn.xfyun.model.response.iat.IatResult result = iatResponse.getData().getResult();
                    if (result.getWs() != null) {
                        for (cn.xfyun.model.response.iat.IatResult.Ws ws : result.getWs()) {
                            if (ws.getCw() != null) {
                                for (cn.xfyun.model.response.iat.IatResult.Cw cw : ws.getCw()) {
                                    if (cw.getW() != null) {
                                        resultText.append(cw.getW());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onFail(WebSocket webSocket, Throwable t, Response response) {
                log.error("语音识别失败: {}", t.getMessage(), t);
                resultFuture.completeExceptionally(t);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("语音识别连接关闭: code={}, reason={}", code, reason);
                if (!resultFuture.isDone()) {
                    resultFuture.complete(resultText.toString());
                }
            }
        });

        // 等待结果
        String result = resultFuture.get();

        // 清理临时文件
        tempFile.delete();

        return result.isEmpty() ? "未识别到语音内容" : result;
    }

    @Override
    public String textToSpeech(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (ttsProvider == null) {
            log.debug("TTS Provider 未配置，跳过语音合成");
            return null;
        }
        // 委托给 TtsProvider，异常向上传播以便调用方区分 error vs noaudio
        return ttsProvider.synthesize(text);
    }
}
