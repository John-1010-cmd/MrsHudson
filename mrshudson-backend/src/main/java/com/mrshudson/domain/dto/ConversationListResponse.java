package com.mrshudson.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 会话列表响应
 */
@Data
@Builder
public class ConversationListResponse {

    /**
     * 会话列表
     */
    private List<ConversationDTO> conversations;
}
