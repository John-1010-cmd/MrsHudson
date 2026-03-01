package com.mrshudson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrshudson.domain.entity.TodoItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办事项 Mapper
 */
@Mapper
public interface TodoItemMapper extends BaseMapper<TodoItem> {
}
