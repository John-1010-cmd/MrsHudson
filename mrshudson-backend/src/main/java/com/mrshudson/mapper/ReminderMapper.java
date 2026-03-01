package com.mrshudson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrshudson.domain.entity.Reminder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提醒记录 Mapper
 */
@Mapper
public interface ReminderMapper extends BaseMapper<Reminder> {
}
