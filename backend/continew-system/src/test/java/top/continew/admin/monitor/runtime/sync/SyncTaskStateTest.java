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

package top.continew.admin.monitor.runtime.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同步任务运行时长上限的单元测试（P1-4 缺陷回归，FR-SYNC-06）。
 *
 * <p>锁死「任务不能永久停在 RUNNING」这一行为：到点必须能被引擎发现并置为 FAILED，
 * 否则页面一直转圈、无法再次启动，只能重启后端。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
class SyncTaskStateTest {

    @Test
    void notTimedOutWhenDeadlineUnset() {
        SyncTaskState state = new SyncTaskState(1L);
        // 0 表示「不限时长」，默认配置下不应被判超时
        assertFalse(state.timedOut());
    }

    @Test
    void timedOutAfterDeadline() {
        SyncTaskState state = new SyncTaskState(1L);
        state.deadline(System.currentTimeMillis() - 1L);
        assertTrue(state.timedOut());
    }

    @Test
    void notTimedOutBeforeDeadline() {
        SyncTaskState state = new SyncTaskState(1L);
        state.deadline(System.currentTimeMillis() + 60_000L);
        assertFalse(state.timedOut());
    }
}
