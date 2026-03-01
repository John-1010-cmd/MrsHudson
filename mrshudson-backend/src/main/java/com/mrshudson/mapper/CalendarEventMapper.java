package com.mrshudson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrshudson.domain.entity.CalendarEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日历事件 Mapper
 */
@Mapper
public interface CalendarEventMapper extends BaseMapper<CalendarEvent> {
}
