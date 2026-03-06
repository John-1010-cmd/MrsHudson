package com.mrshudson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrshudson.domain.entity.DeviceToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备Token Mapper
 */
@Mapper
public interface DeviceTokenMapper extends BaseMapper<DeviceToken> {
}
