package com.mrshudson.service;

import com.mrshudson.config.GitHubProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * GitHub 存储服务
 * 用于上传文件到 GitHub 仓库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubStorageService {

    private final GitHubProperties githubProperties;

    private static final String GITHUB_API_URL = "https://api.github.com";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 上传文件到 GitHub 仓库
     *
     * @param fileName    文件名
     * @param content     文件内容（字节数组）
     * @return GitHub Raw URL
     */
    public String uploadFile(String fileName, byte[] content) {
        if (!githubProperties.isEnabled()) {
            log.warn("GitHub storage is not enabled");
            return null;
        }

        try {
            String path = githubProperties.getPath() + fileName;
            String apiUrl = String.format("%s/repos/%s/%s/contents/%s",
                    GITHUB_API_URL,
                    githubProperties.getOwner(),
                    githubProperties.getRepo(),
                    path);

            // 构建请求体
            String contentBase64 = Base64.getEncoder().encodeToString(content);
            String requestBody = String.format(
                    "{\"message\":\"Upload TTS file: %s\",\"content\":\"%s\",\"branch\":\"%s\"}",
                    fileName, contentBase64, githubProperties.getBranch());

            // 设置请求头
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + githubProperties.getToken());
            headers.set("Accept", "application/vnd.github+json");
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<String> request =
                    new org.springframework.http.HttpEntity<>(requestBody, headers);

            // 发送 PUT 请求
            try {
                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        apiUrl,
                        org.springframework.http.HttpMethod.PUT,
                        request,
                        String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully uploaded file to GitHub: {}", fileName);
                    return getRawUrl(fileName);
                } else {
                    log.error("Failed to upload file to GitHub: {}, status: {}",
                            fileName, response.getStatusCode());
                    return null;
                }
            } catch (Exception e) {
                // 文件可能已存在，尝试先获取 SHA 再更新
                log.info("File may exist, trying to get SHA first: {}", fileName);
                return uploadFileWithSha(fileName, content, path, apiUrl, headers, requestBody);
            }
        } catch (Exception e) {
            log.error("Error uploading file to GitHub: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 如果文件已存在，先获取 SHA 再更新
     */
    private String uploadFileWithSha(String fileName, byte[] content, String path,
                                      String apiUrl, org.springframework.http.HttpHeaders headers,
                                      String initialRequestBody) {
        try {
            // 先获取文件信息
            org.springframework.http.HttpEntity<?> getRequest =
                    new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> getResponse = restTemplate.exchange(
                    apiUrl,
                    org.springframework.http.HttpMethod.GET,
                    getRequest,
                    String.class
            );

            String sha = null;
            if (getResponse.getStatusCode().is2xxSuccessful() && getResponse.getBody() != null) {
                // 解析 JSON 获取 sha
                com.alibaba.fastjson2.JSONObject json =
                        com.alibaba.fastjson2.JSON.parseObject(getResponse.getBody());
                sha = json.getString("sha");
            }

            if (sha != null) {
                // 使用 SHA 更新文件
                String contentBase64 = Base64.getEncoder().encodeToString(content);
                String updateBody = String.format(
                        "{\"message\":\"Update TTS file: %s\",\"content\":\"%s\",\"branch\":\"%s\",\"sha\":\"%s\"}",
                        fileName, contentBase64, githubProperties.getBranch(), sha);

                org.springframework.http.HttpEntity<String> updateRequest =
                        new org.springframework.http.HttpEntity<>(updateBody, headers);

                org.springframework.http.ResponseEntity<String> updateResponse = restTemplate.exchange(
                        apiUrl,
                        org.springframework.http.HttpMethod.PUT,
                        updateRequest,
                        String.class
                );

                if (updateResponse.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully updated file on GitHub: {}", fileName);
                    return getRawUrl(fileName);
                }
            }

            log.error("Failed to update file on GitHub: {}", fileName);
            return null;
        } catch (Exception e) {
            log.error("Error updating file on GitHub: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取文件的 Raw URL
     *
     * @param fileName 文件名
     * @return Raw URL
     */
    public String getRawUrl(String fileName) {
        return String.format("%s/%s/%s/%s/%s",
                githubProperties.getRawUrlPrefix(),
                githubProperties.getOwner(),
                githubProperties.getRepo(),
                githubProperties.getBranch(),
                githubProperties.getPath() + fileName);
    }
}
