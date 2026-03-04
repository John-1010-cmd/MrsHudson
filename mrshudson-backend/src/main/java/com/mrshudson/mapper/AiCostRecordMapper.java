package com.mrshudson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrshudson.domain.entity.AiCostRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI成本记录Mapper
 */
@Mapper
public interface AiCostRecordMapper extends BaseMapper<AiCostRecord> {

    /**
     * 查询用户指定时间范围内的总成本
     */
    @Select("SELECT COALESCE(SUM(cost), 0) FROM ai_cost_record WHERE user_id = #{userId} " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    BigDecimal sumCostByUserAndTimeRange(@Param("userId") Long userId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 查询系统指定时间范围内的总成本
     */
    @Select("SELECT COALESCE(SUM(cost), 0) FROM ai_cost_record WHERE " +
            "created_at >= #{startTime} AND created_at <= #{endTime}")
    BigDecimal sumCostByTimeRange(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间范围内的调用统计（按调用类型分组）
     */
    @Select("SELECT call_type, COUNT(*) as count, SUM(cost) as total_cost, " +
            "SUM(input_tokens) as total_input, SUM(output_tokens) as total_output " +
            "FROM ai_cost_record WHERE created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY call_type")
    List<Map<String, Object>> selectStatsByCallType(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间范围内的路由层统计
     */
    @Select("SELECT router_layer, COUNT(*) as count, SUM(cost) as total_cost " +
            "FROM ai_cost_record WHERE created_at >= #{startTime} AND created_at <= #{endTime} " +
            "AND router_layer IS NOT NULL GROUP BY router_layer")
    List<Map<String, Object>> selectStatsByRouterLayer(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 查询缓存命中率统计
     */
    @Select("SELECT SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END) as hit_count, " +
            "COUNT(*) as total_count " +
            "FROM ai_cost_record WHERE created_at >= #{startTime} AND created_at <= #{endTime}")
    Map<String, Object> selectCacheHitStats(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);
}
