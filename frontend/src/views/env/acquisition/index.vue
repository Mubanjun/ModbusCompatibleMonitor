<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { DeviceStatusResp, DeviceTestResp, PortStatusResp } from '@/apis'
import {
  flushBuffer,
  getAcquisitionStatus,
  getAlarmEngineStatus,
  getBufferStatus,
  pauseAcquisition,
  reloadAcquisition,
  resumeAcquisition,
  startAcquisition,
  stopAcquisition,
  testDevice,
} from '@/apis'

defineOptions({ name: 'EnvAcquisition' })

const loading = ref(false)
const status = ref<Awaited<ReturnType<typeof getAcquisitionStatus>>['data']>()
const buffer = ref<Awaited<ReturnType<typeof getBufferStatus>>['data']>()
const alarm = ref<Record<string, any>>()
const activePortId = ref<string>()
const testVisible = ref(false)
const testLoading = ref(false)
const testResult = ref<DeviceTestResp>()

const statusColorMap: Record<string, string> = {
  ONLINE: 'green',
  OFFLINE: 'red',
  DISABLED: 'gray',
  ALARM: 'orangered',
  COMM_ERROR: 'orange',
  UNKNOWN: 'gray',
}

const circuitColorMap: Record<string, string> = {
  CLOSED: 'green',
  HALF_OPEN: 'orange',
  OPEN: 'red',
}

const activePort = computed<PortStatusResp | undefined>(() =>
  status.value?.ports?.find((port) => String(port.id) === String(activePortId.value)),
)

const devices = computed<DeviceStatusResp[]>(() => activePort.value?.devices ?? [])

const formatTime = (value?: number) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

const formatRate = (value?: number) => {
  if (value === undefined || value === null) {
    return '-'
  }
  return `${(value * 100).toFixed(1)}%`
}

const formatUptime = (value?: number) => {
  if (!value) {
    return '-'
  }
  const seconds = Math.floor(value / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours} 小时 ${minutes} 分 ${seconds % 60} 秒`
}

const loadStatus = async () => {
  loading.value = true
  try {
    const res = await getAcquisitionStatus()
    status.value = res.data
    if (!activePortId.value && res.data?.ports?.length) {
      activePortId.value = String(res.data.ports[0].id)
    }
    const bufferRes = await getBufferStatus()
    buffer.value = bufferRes.data
    const alarmRes = await getAlarmEngineStatus()
    alarm.value = alarmRes.data
  } finally {
    loading.value = false
  }
}

const onFlushBuffer = async () => {
  await flushBuffer()
  Message.success('已触发一次暂存落地')
  await loadStatus()
}

const formatBytes = (value?: number) => {
  if (!value) {
    return '0 B'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}

const confirmAndRun = (title: string, content: string, action: () => Promise<unknown>) => {
  Modal.confirm({
    title,
    content,
    async onOk() {
      await action()
      Message.success('操作已提交')
      await loadStatus()
    },
  })
}

const onStart = () => confirmAndRun('开始采集', '确定要开始采集吗？', startAcquisition)
const onStop = () => confirmAndRun('停止采集', '停止后将不再发送任何报文，确定要停止吗？', stopAcquisition)
const onReload = () =>
  confirmAndRun('重新加载配置', '将断开并重建全部端口连接，确定继续吗？', reloadAcquisition)
const onPause = () => confirmAndRun('暂停采集', '确定要暂停全部采集吗？', () => pauseAcquisition())
const onResume = () => confirmAndRun('恢复采集', '确定要恢复全部采集吗？', () => resumeAcquisition())

const onPausePort = (portId: string) => {
  confirmAndRun('暂停端口', '确定暂停该端口的采集吗？', () => pauseAcquisition(portId))
}

const onResumePort = (portId: string) => {
  confirmAndRun('恢复端口', '确定恢复该端口的采集吗？', () => resumeAcquisition(portId))
}

const onTest = async (deviceId: string) => {
  testVisible.value = true
  testLoading.value = true
  testResult.value = undefined
  try {
    const res = await testDevice(deviceId)
    testResult.value = res.data
  } finally {
    testLoading.value = false
  }
}

const columns: TableInstance['columns'] = [
  { title: '设备名称', dataIndex: 'name', width: 180, ellipsis: true, tooltip: true },
  { title: '从站地址', dataIndex: 'slaveAddr', width: 100, align: 'center' },
  { title: '协议', dataIndex: 'protocol', width: 130 },
  { title: '状态', dataIndex: 'status', slotName: 'status', width: 110, align: 'center' },
  { title: '熔断', dataIndex: 'circuitState', slotName: 'circuitState', width: 110, align: 'center' },
  { title: '通道数', dataIndex: 'channelCount', width: 90, align: 'center' },
  { title: '间隔(ms)', dataIndex: 'intervalMs', width: 100, align: 'center' },
  { title: '成功率', dataIndex: 'successRate', slotName: 'successRate', width: 100, align: 'center' },
  { title: '平均耗时(ms)', dataIndex: 'averageDurationMs', width: 130, align: 'center' },
  { title: '超时/失败', dataIndex: 'errorCount', slotName: 'errorCount', width: 110, align: 'center' },
  { title: '最后通讯', dataIndex: 'lastSuccessTime', slotName: 'lastSuccessTime', width: 180 },
  { title: '最近错误', dataIndex: 'lastError', minWidth: 200, ellipsis: true, tooltip: true },
  { title: '操作', dataIndex: 'operation', slotName: 'operation', width: 110, align: 'center', fixed: 'right' },
]

let timer: ReturnType<typeof setInterval> | undefined

onMounted(async () => {
  await loadStatus()
  timer = setInterval(loadStatus, 5000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<template>
  <GiPageLayout>
    <a-spin :loading="loading" style="width: 100%">
      <a-card :bordered="false" style="margin-bottom: 12px">
        <a-space wrap>
          <a-tag :color="status?.running ? 'green' : 'red'" size="large">
            {{ status?.running ? '采集中' : '已停止' }}
          </a-tag>
          <a-tag v-if="status?.paused" color="orange" size="large">已暂停</a-tag>
          <a-statistic title="端口/设备" :value="`${status?.ports?.length ?? 0} / ${devices.length}`" style="width: 140px" />
          <a-statistic title="轮询轮次" :value="status?.roundCount ?? 0" style="width: 120px" />
          <a-statistic title="设备轮询" :value="status?.devicePollCount ?? 0" style="width: 120px" />
          <a-statistic title="样本条数" :value="status?.sampleCount ?? 0" style="width: 120px" />
          <a-statistic title="运行时长" :value="formatUptime(status?.uptimeMs)" style="width: 200px" />
          <a-statistic title="采集间隔(ms)" :value="status?.settings?.intervalMs ?? 0" style="width: 130px" />
          <a-statistic title="信令丢弃" :value="status?.traceDroppedCount ?? 0" style="width: 120px" />
          <a-statistic title="活动报警" :value="alarm?.activeAlarms ?? 0" style="width: 120px" />
          <a-statistic title="累计报警" :value="alarm?.raisedCount ?? 0" style="width: 120px" />
          <a-statistic title="累计恢复" :value="alarm?.recoveredCount ?? 0" style="width: 120px" />
          <a-statistic title="通知限流" :value="alarm?.notify?.throttledCount ?? 0" style="width: 120px" />
        </a-space>
        <a-divider style="margin: 12px 0" />
        <a-space wrap>
          <a-button v-permission="['monitor:acquisition:control']" type="primary" :disabled="status?.running" @click="onStart">
            <template #icon><icon-play-circle /></template>
            <template #default>开始采集</template>
          </a-button>
          <a-button v-permission="['monitor:acquisition:control']" status="danger" :disabled="!status?.running" @click="onStop">
            <template #icon><icon-poweroff /></template>
            <template #default>停止采集</template>
          </a-button>
          <a-button v-permission="['monitor:acquisition:control']" @click="onReload">
            <template #icon><icon-refresh /></template>
            <template #default>重载配置</template>
          </a-button>
          <a-button v-permission="['monitor:acquisition:control']" :disabled="!status?.running || status?.paused" @click="onPause">
            全部暂停
          </a-button>
          <a-button v-permission="['monitor:acquisition:control']" :disabled="!status?.running || !status?.paused" @click="onResume">
            全部恢复
          </a-button>
          <a-button @click="loadStatus">刷新</a-button>
        </a-space>
      </a-card>

      <a-card :bordered="false" style="margin-bottom: 12px" title="本地暂存与数据落地">
        <template #extra>
          <a-button v-permission="['monitor:acquisition:control']" size="small" @click="onFlushBuffer">
            手动落地
          </a-button>
        </template>
        <a-space wrap>
          <a-statistic title="积压条数" :value="buffer?.backlog ?? 0" style="width: 130px" />
          <a-statistic title="待上传" :value="buffer?.pendingCount ?? 0" style="width: 120px" />
          <a-statistic title="内存待写" :value="buffer?.queuedCount ?? 0" style="width: 120px" />
          <a-statistic title="累计写入" :value="buffer?.appendedCount ?? 0" style="width: 130px" />
          <a-statistic title="累计上传" :value="buffer?.uploadedRows ?? 0" style="width: 130px" />
          <a-statistic title="失败批次" :value="buffer?.failedBatches ?? 0" style="width: 120px" />
          <a-statistic title="暂存文件" :value="formatBytes(buffer?.fileSizeBytes)" style="width: 130px" />
          <a-statistic title="容量上限" :value="buffer?.bufferMaxRows ?? 0" style="width: 140px" />
          <a-statistic title="保留天数" :value="buffer?.retainDays ?? 0" style="width: 120px" />
        </a-space>
        <div style="margin-top: 8px; color: var(--color-text-2); font-size: 12px">
          暂存库：{{ buffer?.databasePath }} ；落地周期：{{ buffer?.flushIntervalMs }} ms ；单批：{{ buffer?.batchSize }} 条
          <span v-if="buffer?.oldestCollectTime">；最旧数据：{{ formatTime(buffer.oldestCollectTime) }}</span>
          <span v-if="buffer?.lastError" style="color: rgb(var(--red-6))">；最近错误：{{ buffer.lastError }}</span>
        </div>
      </a-card>

      <a-card :bordered="false">
        <a-space style="margin-bottom: 12px">
          <span>端口：</span>
          <a-select v-model="activePortId" style="width: 320px" placeholder="请选择端口">
            <a-option v-for="port in status?.ports ?? []" :key="String(port.id)" :value="String(port.id)">
              {{ port.name }}（{{ port.portType }} {{ port.portName }}，{{ port.connected ? '已连接' : '未连接' }}）
            </a-option>
          </a-select>
          <a-tag v-if="activePort" :color="activePort.connected ? 'green' : 'red'">
            {{ activePort.connected ? '已连接' : '未连接' }}
          </a-tag>
          <a-tag v-if="activePort?.paused" color="orange">已暂停</a-tag>
          <a-tag v-if="activePort?.busy" color="arcoblue">本轮执行中</a-tag>
          <a-button
            v-if="activePort && !activePort.paused"
            v-permission="['monitor:acquisition:control']"
            size="small"
            @click="onPausePort(String(activePort.id))"
          >
            暂停该端口
          </a-button>
          <a-button
            v-if="activePort?.paused"
            v-permission="['monitor:acquisition:control']"
            size="small"
            @click="onResumePort(String(activePort.id))"
          >
            恢复该端口
          </a-button>
        </a-space>

        <a-table
          row-key="id"
          :data="devices"
          :columns="columns"
          :pagination="false"
          :scroll="{ x: 1500 }"
          :bordered="{ cell: true }"
        >
          <template #status="{ record }">
            <a-tag :color="statusColorMap[record.status] ?? 'gray'">
              {{ record.statusLabel ?? record.status }}
            </a-tag>
          </template>
          <template #circuitState="{ record }">
            <a-tag :color="circuitColorMap[record.circuitState] ?? 'gray'">
              {{ record.circuitState }}
            </a-tag>
          </template>
          <template #successRate="{ record }">
            {{ formatRate(record.successRate) }}
          </template>
          <template #errorCount="{ record }">
            {{ record.timeoutCount }} / {{ record.errorCount }}
          </template>
          <template #lastSuccessTime="{ record }">
            {{ formatTime(record.lastSuccessTime) }}
          </template>
          <template #operation="{ record }">
            <a-button
              v-permission="['monitor:acquisition:test']"
              type="text"
              size="small"
              @click="onTest(String(record.id))"
            >
              通讯测试
            </a-button>
          </template>
        </a-table>
      </a-card>
    </a-spin>

    <a-drawer v-model:visible="testVisible" title="设备通讯测试" :width="860" unmount-on-close>
      <a-spin :loading="testLoading" style="width: 100%">
        <template v-if="testResult">
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item label="设备">{{ testResult.deviceName }}</a-descriptions-item>
            <a-descriptions-item label="从站地址">{{ testResult.slaveAddr }}</a-descriptions-item>
            <a-descriptions-item label="协议">{{ testResult.protocol }}</a-descriptions-item>
            <a-descriptions-item label="端口">{{ testResult.portName }}</a-descriptions-item>
            <a-descriptions-item label="结果">
              <a-tag :color="testResult.success ? 'green' : 'red'">
                {{ testResult.success ? '成功' : '失败' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="耗时">{{ testResult.durationMs }} ms</a-descriptions-item>
            <a-descriptions-item v-if="testResult.errorMsg" label="错误" :span="2">
              {{ testResult.errorMsg }}
            </a-descriptions-item>
          </a-descriptions>

          <a-divider>通道解析结果</a-divider>
          <a-table
            row-key="channelNo"
            :data="testResult.channels"
            :pagination="false"
            size="small"
            :bordered="{ cell: true }"
          >
            <template #columns>
              <a-table-column title="通道" data-index="channelNo" :width="80" align="center" />
              <a-table-column title="寄存器" data-index="regAddr" :width="90" align="center" />
              <a-table-column title="原始寄存器" :width="180">
                <template #cell="{ record }">{{ (record.registers ?? []).join(', ') }}</template>
              </a-table-column>
              <a-table-column title="模拟量" :min-width="320">
                <template #cell="{ record }">
                  <div v-for="sample in record.samples" :key="sample.analogNo">
                    模拟量{{ sample.analogNo }}：{{ sample.elementName ?? '-' }}
                    原始值 {{ sample.rawValue }} → 工程值 {{ sample.engineeringValue }}
                    {{ sample.unit ?? '' }}
                  </div>
                </template>
              </a-table-column>
              <a-table-column title="错误" data-index="errorMsg" :min-width="160" />
            </template>
          </a-table>

          <a-divider>报文留痕</a-divider>
          <a-table
            row-key="sequence"
            :data="testResult.frames"
            :pagination="false"
            size="small"
            :bordered="{ cell: true }"
          >
            <template #columns>
              <a-table-column title="方向" data-index="direction" :width="90" align="center" />
              <a-table-column title="报文" data-index="frame" :min-width="260" />
              <a-table-column title="解析" data-index="parsed" :min-width="220" />
            </template>
          </a-table>
        </template>
      </a-spin>
    </a-drawer>
  </GiPageLayout>
</template>
