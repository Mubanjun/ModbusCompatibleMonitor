<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import type { SelfCheckItemResp, WatchdogStatusResp } from '@/apis'
import { getWatchdogStatus, ingestWatchdogEvents, runSelfCheck } from '@/apis'

defineOptions({ name: 'EnvWatchdog' })

const status = ref<WatchdogStatusResp>()
const formatHeartbeat = (value?: WatchdogStatusResp) => {
  if (!value) return '从未收到心跳'
  if (value.heartbeatMessage) return value.heartbeatMessage
  return value.heartbeatAgeSeconds == null ? '从未收到心跳' : `${value.heartbeatAgeSeconds} 秒`
}
const checks = ref<SelfCheckItemResp[]>([])
const loading = ref(false)
const checking = ref(false)

const checkColumns: TableInstance['columns'] = [
  { title: '检查项', dataIndex: 'name', width: 200 },
  { title: '结果', dataIndex: 'ok', slotName: 'ok', width: 100, align: 'center' },
  { title: '说明', dataIndex: 'detail', minWidth: 320, ellipsis: true, tooltip: true },
]

const restartColumns: TableInstance['columns'] = [
  { title: '时间', dataIndex: 'time', width: 190 },
  { title: '事件', dataIndex: 'eventType', slotName: 'eventType', width: 150, align: 'center' },
  { title: '原因', dataIndex: 'reason', minWidth: 280, ellipsis: true, tooltip: true },
  { title: '累计重启', dataIndex: 'restartCount', width: 110, align: 'center' },
]

const load = async () => {
  loading.value = true
  try {
    const res = await getWatchdogStatus()
    status.value = res.data
  } finally {
    loading.value = false
  }
}

const onSelfCheck = async () => {
  checking.value = true
  try {
    const res = await runSelfCheck()
    checks.value = res.data ?? []
    const failed = checks.value.filter((item) => !item.ok).length
    if (failed === 0) {
      Message.success('自检全部通过')
    } else {
      Message.warning(`自检有 ${failed} 项未通过`)
    }
    await load()
  } finally {
    checking.value = false
  }
}

const onIngest = async () => {
  const res = await ingestWatchdogEvents()
  Message.success(`已导入 ${res.data ?? 0} 条看门狗事件`)
  await load()
}

onMounted(async () => {
  await load()
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" title="看门狗状态（心跳与失联判定）" style="margin-bottom: 12px">
      <template #extra>
        <a-space>
          <a-button v-permission="['monitor:watchdog:selfCheck']" type="primary" :loading="checking" @click="onSelfCheck">
            执行自检
          </a-button>
          <a-button @click="onIngest">
            导入看门狗事件
          </a-button>
          <a-button :loading="loading" @click="load">
            刷新
          </a-button>
        </a-space>
      </template>
      <a-spin :loading="loading" style="width: 100%">
        <a-descriptions :column="3" bordered size="small">
          <a-descriptions-item label="被监控进程">
            {{ status?.processName ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="最近心跳">
            {{ status?.lastHeartbeat ?? '（尚未收到）' }}
          </a-descriptions-item>
          <a-descriptions-item label="心跳状态">
            <a-tag :color="status?.heartbeatStale ? 'red' : 'green'">
              {{ status?.heartbeatStale ? '已超时' : '正常' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="心跳距今">
            {{ formatHeartbeat(status) }} / 阈值 {{ status?.heartbeatTimeoutSeconds ?? '-' }} 秒
          </a-descriptions-item>
          <a-descriptions-item label="近一小时重启">
            {{ status?.restartCountLastHour ?? 0 }} / {{ status?.restartLimitPerHour ?? '-' }} 次
          </a-descriptions-item>
          <a-descriptions-item label="重启限流">
            <a-tag :color="status?.restartLimitExceeded ? 'red' : 'gray'">
              {{ status?.restartLimitExceeded ? '已触发（停止自动拉起）' : '未触发' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="心跳文件" :span="2">
            {{ status?.heartbeatFile ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="看门狗日志">
            {{ status?.watchdogLog ?? '-' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-card>

    <a-card :bordered="false" title="最近一次完整自检" style="margin-bottom: 12px">
      <a-table
        v-if="checks.length"
        row-key="name"
        :data="checks"
        :columns="checkColumns"
        :pagination="false"
        size="small"
      >
        <template #ok="{ record }">
          <a-tag :color="record.ok ? 'green' : 'red'">
            {{ record.ok ? '通过' : '未通过' }}
          </a-tag>
        </template>
      </a-table>
      <a-empty v-else description="点击「执行自检」查看数据源、端口、暂存与上报检查结果" />
    </a-card>

    <a-card :bordered="false" title="重启记录" style="margin-bottom: 12px">
      <a-table
        row-key="time"
        :data="status?.restarts ?? []"
        :columns="restartColumns"
        :pagination="{ pageSize: 10 }"
        size="small"
      >
        <template #eventType="{ record }">
          <a-tag :color="record.eventType === 'REBOOT_LIMIT' ? 'red' : 'orange'">
            {{ record.eventType }}
          </a-tag>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" title="看门狗日志尾部">
      <pre style="max-height: 320px; overflow: auto; margin: 0">{{ (status?.logTail ?? []).join('\n') }}</pre>
    </a-card>
  </GiPageLayout>
</template>
