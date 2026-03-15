package com.mrshudson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitHub 存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    /**
     * GitHub Personal Access Token
     */
    private String token;

    /**
     * 仓库所有者
     */
    private String owner;

    /**
     * 仓库名称
     */
    private String repo;

    /**
     * 分支名称
     */
    private String branch = "master";

    /**
     * 存储路径
     */
    private String path = "tts/";

    /**
     * GitHub Raw URL 前缀
     */
    private String rawUrlPrefix = "https://raw.githubusercontent.com";

    /**
     * 是否启用
     */
    private boolean enabled = false;
}