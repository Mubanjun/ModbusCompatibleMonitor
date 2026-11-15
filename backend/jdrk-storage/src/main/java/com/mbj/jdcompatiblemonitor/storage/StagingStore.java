/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
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

package com.mbj.jdcompatiblemonitor.storage;

import java.util.Collection;
import java.util.List;

/**
 * 本地暂存库（FR-BUF-01/03/04/05/06）。
 *
 * <p>采集线程只做「写入暂存」，上传线程按批次读取并确认；
 * 两者通过状态位解耦，业务库不可用时积压只增不减，恢复后按时间顺序续传。</p>
 */
public interface StagingStore extends AutoCloseable {

    /**
     * 打开暂存库并完成崩溃恢复
     */
    void open();

    /**
     * 是否已打开
     *
     * @return 是否已打开
     */
    boolean isOpen();

    /**
     * 批量写入暂存
     *
     * @param records 待写入记录（{@code id} 被忽略）
     * @return 实际写入条数
     */
    int append(Collection<StagingRecord> records);

    /**
     * 取出一批待上传记录并标记为「上传中」
     *
     * @param limit 批次上限
     * @param batchId 批次标识
     * @return 记录列表，按写入顺序
     */
    List<StagingRecord> claimBatch(int limit, String batchId);

    /**
     * 删除已上传成功的记录
     *
     * @param ids 暂存主键
     * @return 删除条数
     */
    int delete(Collection<Long> ids);

    /**
     * 把「上传中」的记录退回待上传（上传失败时调用）
     *
     * @param batchId 批次标识
     * @return 退回条数
     */
    int releaseBatch(String batchId);

    /**
     * 按容量上限滚动清理最旧数据（FR-BUF-04）
     *
     * @param maxRows 容量上限
     * @return 清理条数
     */
    int purgeOverflow(long maxRows);

    /**
     * 状态快照
     *
     * @return 快照
     */
    StagingStats stats();

    /**
     * 关闭暂存库
     */
    @Override
    void close();
}
