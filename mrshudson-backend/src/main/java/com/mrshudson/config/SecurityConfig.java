package com.mrshudson.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（前后端分离开发）
            .csrf(AbstractHttpConfigurer::disable)
            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // 登录接口放行
                .requestMatchers("/api/auth/**").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            // 配置session管理
            .sessionManagement(session -> session
                .maximumSessions(1)
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Demo阶段使用BCrypt，但密码明文比较
        return new BCryptPasswordEncoder();
    }
}
