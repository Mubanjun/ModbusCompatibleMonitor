/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.monitor.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.resp.BizDataResp;
import top.continew.admin.monitor.model.resp.DataAggregateResp;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 采集历史数据 Mapper
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizDataMapper extends BaseMapper<BizDataDO> {

    /**
     * 查询已存在的来源暂存主键（幂等去重用）
     *
     * @param sourceIds 来源暂存主键
     * @return 已存在的来源主键
     */
    @Select("<script>SELECT source_id FROM biz_data WHERE source_id IN"
        + " <foreach collection='sourceIds' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
        + "</script>")
    List<Long> selectExistingSourceIds(@Param("sourceIds") Collection<Long> sourceIds);

    /**
     * 物理清理超过保留期的历史数据（DR-05 数据保留与清理）
     *
     * @param deadline 早于该时间的记录将被删除
     * @return 删除行数
     */
    @Delete("DELETE FROM biz_data WHERE record_time < #{deadline}")
    int purgeBefore(@Param("deadline") LocalDateTime deadline);

    /**
     * 按时间桶聚合历史数据（FR-WEB-05「聚合」）
     *
     * <p>时间桶表达式使用 MySQL 的 {@code DATE_FORMAT}，与项目当前只提供 MySQL 变更集保持一致。</p>
     *
     * @param pattern   时间桶格式
     * @param deviceId  设备 ID，可为空
     * @param nodeId    节点编号，可为空
     * @param analogNo  模拟量序号，可为空
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return 聚合结果
     */
    @Select("<script>SELECT DATE_FORMAT(record_time, #{pattern}) AS bucket,"
        + " AVG(data_value) AS avg_value, MIN(data_value) AS min_value,"
        + " MAX(data_value) AS max_value, COUNT(*) AS sample_count"
        + " FROM biz_data WHERE deleted = 0"
        + " <if test='deviceId != null'> AND device_id = #{deviceId}</if>"
        + " <if test='nodeId != null'> AND node_id = #{nodeId}</if>"
        + " <if test='analogNo != null'> AND analog_no = #{analogNo}</if>"
        + " <if test='startTime != null'> AND record_time &gt;= #{startTime}</if>"
        + " <if test='endTime != null'> AND record_time &lt;= #{endTime}</if>"
        + " GROUP BY bucket ORDER BY bucket</script>")
    List<DataAggregateResp> aggregate(@Param("pattern") String pattern,
        @Param("deviceId") Long deviceId, @Param("nodeId") Integer nodeId,
        @Param("analogNo") Integer analogNo, @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    /**
     * 按时间升序读取曲线点（FR-WEB-02 实时曲线取数）
     *
     * <p>与分页查询的区别：固定按 {@code record_time} 升序、只取曲线需要的列，
     * 并由 {@code limit} 兜底避免一次拉回过多点；同一时间点存在多条时按主键保证顺序稳定。</p>
     *
     * @param deviceId   设备 ID，可为空
     * @param deviceAddr 设备地址，可为空
     * @param nodeId     节点编号，可为空
     * @param analogNo   模拟量序号，可为空
     * @param startTime  起始时间（含），可为空
     * @param endTime    结束时间（含），可为空
     * @param limit      最大返回点数（已由上层收敛）
     * @return 曲线点（时间升序）
     */
    @Select("<script>SELECT id, device_id, device_name, device_addr, node_id, analog_no,"
        + " element_name, data_value, raw_value, unit, alarm_flag, record_time"
        + " FROM biz_data WHERE deleted = 0"
        + " <if test='deviceId != null'> AND device_id = #{deviceId}</if>"
        + " <if test='deviceAddr != null'> AND device_addr = #{deviceAddr}</if>"
        + " <if test='nodeId != null'> AND node_id = #{nodeId}</if>"
        + " <if test='analogNo != null'> AND analog_no = #{analogNo}</if>"
        + " <if test='startTime != null'> AND record_time &gt;= #{startTime}</if>"
        + " <if test='endTime != null'> AND record_time &lt;= #{endTime}</if>"
        + " ORDER BY record_time ASC, id ASC LIMIT #{limit}</script>")
    List<BizDataResp> listCurve(@Param("deviceId") Long deviceId,
        @Param("deviceAddr") Integer deviceAddr, @Param("nodeId") Integer nodeId,
        @Param("analogNo") Integer analogNo, @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime, @Param("limit") int limit);
}
