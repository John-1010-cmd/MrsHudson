package com.mrshudson.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 语音服务接口
 */
public interface VoiceService {

    /**
     * 语音识别 - 将音频文件转换为文本
     *
     * @param audioFile 音频文件（支持wav、mp3等格式）
     * @return 识别出的文本
     */
    String speechToText(MultipartFile audioFile);

    /**
     * 语音识别 - 带格式参数的转换
     *
     * @param audioFile 音频文件
     * @param format    音频格式（如wav、mp3、pcm等）
     * @param sampleRate 采样率（如16000、8000等）
     * @return 识别出的文本
     */
    String speechToText(MultipartFile audioFile, String format, Integer sampleRate);

    /**
     * 语音合成 - 将文本转换为音频文件
     *
     * @param text 要合成的文本
     * @return 音频文件的访问URL，合成失败返回null
     */
    String textToSpeech(String text);
}
