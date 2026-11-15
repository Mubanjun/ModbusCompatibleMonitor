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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.starter.data.mapper.BaseMapper;

/**
 * 采集设备 Mapper
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizDeviceMapper extends BaseMapper<BizDeviceDO> {

    /** 绕过数据权限读取原始设备，供访问守卫先判定再返回。 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM biz_device WHERE id = #{id} AND deleted = 0 LIMIT 1")
    BizDeviceDO selectRawById(@Param("id") Long id);
}
