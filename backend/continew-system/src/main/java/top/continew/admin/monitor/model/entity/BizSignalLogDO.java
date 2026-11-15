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

package top.continew.admin.monitor.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 信令记录实体（FR-SIG-01/03）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_signal_log")
public class BizSignalLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 端口 ID
     */
    private Long portId;

    /**
     * 端口地址
     */
    private String portName;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 从站地址
     */
    private Integer deviceAddr;

    /**
     * 方向（OUT：发送；IN：接收；ERROR：异常）
     */
    private String direction;

    /**
     * 十六进制报文
     */
    private String frame;

    /**
     * 报文字节数
     */
    private Integer frameLength;

    /**
     * 解析结果
     */
    private String parsed;

    /**
     * 耗时（毫秒）
     */
    private Integer durationMs;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 报文时间
     */
    private LocalDateTime traceTime;
}
