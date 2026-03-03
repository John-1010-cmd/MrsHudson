package com.mrshudson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrshudson.domain.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 对话会话Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 更新会话标题
     */
    @Update("UPDATE conversation SET title = #{title}, updated_at = NOW() WHERE id = #{id}")
    int updateTitle(@Param("id") Long id, @Param("title") String title);
}
