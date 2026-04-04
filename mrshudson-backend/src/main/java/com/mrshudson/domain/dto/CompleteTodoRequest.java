package com.mrshudson.domain.dto;

import lombok.Data;

/**
 * 完成/取消完成待办事项请求
 */
@Data
public class CompleteTodoRequest {

    /**
     * 是否完成
     */
    private Boolean completed;
}
