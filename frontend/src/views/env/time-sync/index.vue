<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { TimeSyncDeviceItem, TimeSyncResultResp, TimeSyncStatusResp } from '@/apis'
import { getTimeSyncStatus, syncAllDeviceTime, syncDeviceTime } from '@/apis'

defineOptions({ name: 'EnvTimeSync' })

const status = ref<TimeSyncStatusResp>()
const loading = ref(false)

const deviceColumns: TableInstance['columns'] = [
  { title: '设备名称', dataIndex: 'deviceName', minWidth: 160, ellipsis: true, tooltip: true },
  { title: '从站地址', dataIndex: 'deviceAddr', width: 100, align: 'center' },
  { title: '所属端口', dataIndex: 'portName', width: 140, ellipsis: true, tooltip: true },
  { title: '协议', dataIndex: 'protocol', width: 130, align: 'center' },
  { title: '状态', dataIndex: 'online', slotName: 'online', width: 90, align: 'center' },
  { title: '设备时间', dataIndex: 'deviceTime', width: 180 },
  { title: '偏差', dataIndex: 'offsetSeconds', slotName: 'offset', width: 110, align: 'center' },
  { title: '说明', dataIndex: 'message', minWidth: 220, ellipsis: true, tooltip: true },
  { title: '操作', dataIndex: 'action', slotName: 'action', width: 100, align: 'center' },
]

// 加载校时状态与各设备时间偏差
const loadStatus = async () => {
  loading.value = true
  try {
    const res = await getTimeSyncStatus()
    status.value = res.data
  } finally {
    loading.value = false
  }
}

const describe = (data?: TimeSyncResultResp) => {
  if (!data) {
    return '校时失败'
  }
  if (data.writeSkipped) {
    return data.message ?? '偏差在容差内，已跳过写入'
  }
  return data.message ?? (data.resultStatus === 'SUCCESS' ? '校时成功' : '校时失败')
}

// 单设备一键校时
const onSyncDevice = (record: TimeSyncDeviceItem) => {
  if (record.supported === false) {
    Message.warning(record.message ?? '该协议不支持校时')
    return
  }
  Modal.confirm({
    title: '一键校时确认',
    content: `确定把平台本机时间同步到设备「${record.deviceName}」吗？该校时操作不可撤销。`,
    async onOk() {
      const res = await syncDeviceTime(record.deviceId)
      const data = res.data
      if (data?.resultStatus === 'SUCCESS') {
        Message.success(describe(data))
      } else {
        Message.error(describe(data))
      }
      await loadStatus()
    },
  })
}

// 批量一键校时（mode=ALL 全部下发；TOLERANT 仅偏差超出容差的设备下发）
const onSyncAll = (mode: 'ALL' | 'TOLERANT') => {
  const targets = (status.value?.devices ?? []).filter((item) => item.supported !== false)
  const label = mode === 'TOLERANT' ? '偏差超出容差' : '全部'
  Modal.confirm({
    title: '批量校时确认',
    content: `确定向${label}的 ${targets.length} 台支持校时的设备下发平台本机时间吗？该校时操作不可撤销。`,
    async onOk() {
      const res = await syncAllDeviceTime(undefined, mode)
      const list = res.data ?? []
      const success = list.filter((item) => item.resultStatus === 'SUCCESS').length
      const skipped = list.filter((item) => item.resultStatus === 'SKIPPED').length
      const rejected = list.filter((item) => item.resultStatus === 'REJECTED').length
      const failed = list.length - success - skipped - rejected
      if (rejected > 0 || failed > 0) {
        Message.warning(`共 ${list.length} 台：成功 ${success}，跳过 ${skipped}，失败 ${failed}，拒绝 ${rejected}`)
      } else {
        Message.success(`共 ${list.length} 台：成功 ${success}，跳过 ${skipped}`)
      }
      await loadStatus()
    },
  })
}

onMounted(loadStatus)
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" style="margin-bottom: 12px">
      <a-space wrap>
        <span>平台时间：</span>
        <a-tag color="arcoblue">{{ status?.serverTime ?? '-' }}</a-tag>
        <a-tag v-if="status?.writeEnabled === false" color="orangered">
          写入已禁用（TIME_SYNC_WRITE_ENABLED=0）
        </a-tag>
        <a-tag :color="status?.autoEnabled ? 'green' : 'gray'">
          自动校时{{ status?.autoEnabled ? '已启用' : '未启用' }}
        </a-tag>
        <span v-if="status?.autoEnabled && status?.autoCron">计划：{{ status.autoCron }}</span>
        <span>偏差容差：{{ status?.toleranceSeconds ?? '-' }} 秒</span>
        <span>时间寄存器：{{ status?.registerBase ?? '-' }} ~ {{ (status?.registerBase ?? 0) + 5 }}</span>
        <a-button @click="loadStatus">
          <template #icon><icon-refresh /></template>
          <template #default>刷新检测</template>
        </a-button>
        <a-button
          v-permission="['monitor:timeSync:sync']"
          type="primary"
          :disabled="status?.writeEnabled === false"
          @click="onSyncAll('ALL')"
        >
          <template #default>一键校时（全部）</template>
        </a-button>
        <a-button
          v-permission="['monitor:timeSync:sync']"
          :disabled="status?.writeEnabled === false"
          @click="onSyncAll('TOLERANT')"
        >
          <template #default>仅校超差设备</template>
        </a-button>
      </a-space>
      <a-divider style="margin: 12px 0" />
      <div style="color: var(--color-text-3)">
        最近一次校时：{{ status?.lastRunTime ?? '暂无' }} {{ status?.lastRunMessage ?? '' }}
      </div>
    </a-card>

    <a-card :bordered="false" title="设备时间偏差检测（NFR-REL-06）">
      <a-spin :loading="loading" style="width: 100%">
        <a-table
          row-key="deviceId"
          :data="status?.devices ?? []"
          :columns="deviceColumns"
          :pagination="false"
          size="small"
        >
          <template #online="{ record }">
            <a-tag v-if="record.supported === false" color="gray">不支持</a-tag>
            <a-tag v-else-if="record.online" color="green">在线</a-tag>
            <a-tag v-else color="red">离线</a-tag>
          </template>
          <template #offset="{ record }">
            <a-tag v-if="record.offsetSeconds === undefined || record.offsetSeconds === null" color="gray">-</a-tag>
            <a-tag v-else-if="record.withinTolerance" color="green">{{ record.offsetSeconds }} 秒</a-tag>
            <a-tag v-else color="orange">{{ record.offsetSeconds }} 秒</a-tag>
          </template>
          <template #action="{ record }">
            <a-link
              v-permission="['monitor:timeSync:sync']"
              :disabled="record.supported === false || status?.writeEnabled === false"
              @click="onSyncDevice(record)"
            >
              校时
            </a-link>
          </template>
        </a-table>
      </a-spin>
    </a-card>
  </GiPageLayout>
</template>
