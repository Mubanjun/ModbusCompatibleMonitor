<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { DeviceResp, RelayLogQuery, RelayStatusResp } from '@/apis'
import { batchRelay, controlRelay, getRelayStatus, getRelayWriteEnabled, listDevice, pageRelayLog } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvRelay' })

const deviceOptions = ref<DeviceResp[]>([])
const activeDeviceId = ref<string>()
const status = ref<RelayStatusResp>()
const writeEnabled = ref(true)
const loading = ref(false)
const durationMs = ref(0)

const logQuery = reactive<RelayLogQuery>({ sort: ['operateTime,desc'] })
const {
  tableData: logList,
  loading: logLoading,
  pagination: logPagination,
  search: searchLog,
} = useTable((page) => pageRelayLog({ ...logQuery, ...page }))

const logColumns: TableInstance['columns'] = [
  { title: '操作时间', dataIndex: 'operateTime', width: 180 },
  { title: '设备', dataIndex: 'deviceName', width: 170, ellipsis: true, tooltip: true },
  { title: '继电器', dataIndex: 'relayNo', width: 90, align: 'center' },
  { title: '动作', dataIndex: 'action', slotName: 'action', width: 120, align: 'center' },
  { title: '请求值', dataIndex: 'requestValue', width: 140, ellipsis: true, tooltip: true },
  { title: '操作前', dataIndex: 'previousValue', width: 140, ellipsis: true, tooltip: true },
  { title: '回读', dataIndex: 'readbackValue', width: 140, ellipsis: true, tooltip: true },
  { title: '回读一致', dataIndex: 'readbackOk', slotName: 'readbackOk', width: 100, align: 'center' },
  { title: '结果', dataIndex: 'resultStatus', slotName: 'resultStatus', width: 100, align: 'center' },
  { title: '操作人', dataIndex: 'operatorName', width: 120 },
  { title: '失败原因', dataIndex: 'errorMsg', minWidth: 180, ellipsis: true, tooltip: true },
]

const actionMap: Record<string, [string, string]> = {
  CLOSE: ['闭合', 'green'],
  OPEN: ['断开', 'gray'],
  BATCH: ['批量', 'arcoblue'],
  AUTO_RESTORE: ['定时恢复', 'orange'],
}

const loadDevices = async () => {
  const res = await listDevice({})
  deviceOptions.value = (res.data ?? []) as DeviceResp[]
  if (!activeDeviceId.value && deviceOptions.value.length) {
    activeDeviceId.value = String(deviceOptions.value[0].id)
  }
}

const loadStatus = async () => {
  if (!activeDeviceId.value) {
    return
  }
  loading.value = true
  try {
    const res = await getRelayStatus(activeDeviceId.value)
    status.value = res.data
    if (res.data?.online === false && res.data?.errorMsg) {
      Message.warning(res.data.errorMsg)
    }
  } finally {
    loading.value = false
  }
}

const loadWriteEnabled = async () => {
  const res = await getRelayWriteEnabled()
  writeEnabled.value = res.data !== false
}

const onToggle = (relayNo: number, closed: boolean) => {
  Modal.confirm({
    title: '继电器操作确认',
    content: `确定将第 ${relayNo} 路继电器${closed ? '闭合' : '断开'}吗？`,
    async onOk() {
      const res = await controlRelay(String(activeDeviceId.value), {
        relayNo,
        closed,
        durationMs: durationMs.value,
      })
      const data = res.data
      if (data?.success) {
        Message.success(data.message ?? '操作成功')
      } else {
        Message.error(data?.message ?? '操作失败')
      }
      await loadStatus()
      await searchLog()
    },
  })
}

const onBatch = (value: number) => {
  const relayCount = status.value?.relayCount ?? 8
  const values = Array.from({ length: relayCount }, () => value)
  Modal.confirm({
    title: '批量操作确认',
    content: `确定将所有继电器${value === 1 ? '闭合' : '断开'}吗？`,
    async onOk() {
      const res = await batchRelay(String(activeDeviceId.value), values)
      Message.info(res.data?.message ?? '已提交')
      await loadStatus()
      await searchLog()
    },
  })
}

onMounted(async () => {
  await loadDevices()
  await loadWriteEnabled()
  await loadStatus()
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" style="margin-bottom: 12px">
      <a-space wrap>
        <span>设备：</span>
        <a-select v-model="activeDeviceId" style="width: 260px" placeholder="请选择设备" @change="loadStatus">
          <a-option v-for="item in deviceOptions" :key="String(item.id)" :value="String(item.id)">
            {{ item.name }}
          </a-option>
        </a-select>
        <a-tag v-if="status?.online" color="green">在线</a-tag>
        <a-tag v-else color="red">离线</a-tag>
        <a-tag v-if="!writeEnabled" color="orangered">写入已禁用（RELAY_WRITE_ENABLED=0）</a-tag>
        <span>执行时长(ms)：</span>
        <a-input-number v-model="durationMs" :min="0" :step="1000" style="width: 140px" />
        <a-button @click="loadStatus">刷新状态</a-button>
        <a-button v-permission="['monitor:relay:control']" :disabled="!writeEnabled" @click="onBatch(1)">全部闭合</a-button>
        <a-button v-permission="['monitor:relay:control']" :disabled="!writeEnabled" @click="onBatch(0)">全部断开</a-button>
      </a-space>
      <a-divider style="margin: 12px 0" />
      <a-spin :loading="loading" style="width: 100%">
        <a-empty v-if="!status?.relays?.length" description="暂无继电器数据" />
        <a-space v-else wrap>
          <a-card v-for="relay in status?.relays ?? []" :key="relay.relayNo" size="small" style="width: 150px">
            <div style="text-align: center">
              <div>第 {{ relay.relayNo }} 路</div>
              <div style="color: var(--color-text-3); font-size: 12px">寄存器 {{ relay.register }}</div>
              <a-switch
                v-permission="['monitor:relay:control']"
                :model-value="relay.closed"
                :disabled="!writeEnabled"
                checked-text="吸合"
                unchecked-text="断开"
                style="margin-top: 8px"
                @change="(v: any) => onToggle(relay.relayNo, Boolean(v))"
              />
            </div>
          </a-card>
        </a-space>
      </a-spin>
    </a-card>

    <GiTable
      row-key="id"
      :data="logList"
      :columns="logColumns"
      :loading="logLoading"
      :pagination="logPagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1400 }"
      @refresh="searchLog"
    >
      <template #toolbar-left>
        <a-input-search
          v-model="logQuery.description"
          placeholder="搜索设备/失败原因"
          allow-clear
          style="width: 220px"
          @search="searchLog"
        />
        <a-select v-model="logQuery.action" placeholder="动作" allow-clear style="width: 140px" @change="searchLog">
          <a-option value="CLOSE">闭合</a-option>
          <a-option value="OPEN">断开</a-option>
          <a-option value="BATCH">批量</a-option>
          <a-option value="AUTO_RESTORE">定时恢复</a-option>
        </a-select>
        <a-select v-model="logQuery.resultStatus" placeholder="结果" allow-clear style="width: 140px" @change="searchLog">
          <a-option value="SUCCESS">成功</a-option>
          <a-option value="FAILED">失败</a-option>
          <a-option value="REJECTED">被拒绝</a-option>
        </a-select>
      </template>
      <template #action="{ record }">
        <a-tag :color="actionMap[record.action]?.[1] ?? 'gray'">
          {{ actionMap[record.action]?.[0] ?? record.action }}
        </a-tag>
      </template>
      <template #readbackOk="{ record }">
        <a-tag :color="record.readbackOk ? 'green' : 'red'">{{ record.readbackOk ? '一致' : '不一致' }}</a-tag>
      </template>
      <template #resultStatus="{ record }">
        <a-tag :color="record.resultStatus === 'SUCCESS' ? 'green' : (record.resultStatus === 'REJECTED' ? 'orange' : 'red')">
          {{ record.resultStatus }}
        </a-tag>
      </template>
    </GiTable>
  </GiPageLayout>
</template>
