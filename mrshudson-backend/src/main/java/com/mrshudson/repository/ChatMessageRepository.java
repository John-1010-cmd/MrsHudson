package com.mrshudson.repository;

import com.mrshudson.domain.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话消息Repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 查找用户的对话历史
     */
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 查找用户的最近N条消息
     */
    List<ChatMessage> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
