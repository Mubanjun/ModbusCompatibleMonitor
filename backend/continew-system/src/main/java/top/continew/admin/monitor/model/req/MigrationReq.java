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

package top.continew.admin.monitor.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 迁移向导请求（FR-IO-06、FR-MIG-01~06）
 *
 * <p>四步向导共用一个请求体：检测只需要连接参数，设备映射额外用 table/portId/nameColumn/addrColumn，
 * 历史迁移与校验用 startTime/endTime，模式切换用 mode。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "迁移向导请求")
public class MigrationReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 缺陷 72（安全）：host/database 会被直接拼进 JDBC URL（MigrationSpec.jdbcUrl），
    // 而两者原先没有任何校验——`database` 里塞 `x?allowLoadLocalInfile=true&autoDeserialize=true`
    // 就能注入连接属性（读服务器本地文件 / 触发 Connector/J 反序列化），`host` 还能把平台
    // 变成任意主机:3306 的出站客户端。这里用字符白名单堵住 URL 元字符；
    // 注意只加 @Pattern（允许 null），因为 PUT /mode 复用同一个 Req 且不带连接参数。
    @Schema(description = "源库主机")
    @Pattern(regexp = "^[A-Za-z0-9._-]{1,128}$", message = "源库主机只能是主机名或 IP 地址")
    private String host;

    @Schema(description = "源库端口")
    @Min(value = 1, message = "源库端口必须在 1~65535")
    @Max(value = 65535, message = "源库端口必须在 1~65535")
    private Integer port;

    @Schema(description = "源库名")
    @Pattern(regexp = "^[A-Za-z0-9_$-]{1,64}$", message = "源库名只能是合法的数据库标识符")
    private String database;

    @Schema(description = "源库用户名")
    private String username;

    @Schema(description = "源库口令")
    private String password;

    @Schema(description = "源设备表名")
    private String table;

    @Schema(description = "目标端口 ID")
    private Long portId;

    @Schema(description = "目标协议（MODBUS_RTU / RS_MODBUS / MODBUS_TCP）")
    private String protocol;

    @Schema(description = "源名称列（默认 DeviceName）")
    private String nameColumn;

    @Schema(description = "源地址列（默认 DeviceAddr）")
    private String addrColumn;

    @Schema(description = "历史/校验起始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "历史/校验结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "是否仅预演")
    private Boolean dryRun;

    @Schema(description = "运行模式（OFF / COEXIST / SWITCHED）")
    private String mode;
}
