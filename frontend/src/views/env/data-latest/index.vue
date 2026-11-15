<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import type { DataLatestQuery, DeviceResp } from '@/apis'
import { listDevice, pageDataLatest } from '@/apis'
import { useAutoRefresh, useTable } from '@/hooks'

defineOptions({ name: 'EnvDataLatest' })

const deviceOptions = ref<DeviceResp[]>([])

const statusColorMap: Record<string, string> = {
  ONLINE: 'green',
  OFFLINE: 'red',
  DISABLED: 'gray',
  ALARM: 'orangered',
  COMM_ERROR: 'orange',
  UNKNOWN: 'gray',
}

const qualityColorMap: Record<string, string> = {
  GOOD: 'green',
  BAD: 'red',
  STALE: 'orange',
}

const queryForm = reactive<DataLatestQuery>({
  sort: ['collectTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageDataLatest({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '设备', dataIndex: 'deviceName', width: 180, ellipsis: true, tooltip: true },
  { title: '设备地址', dataIndex: 'deviceAddr', width: 100, align: 'center' },
  { title: '通道', dataIndex: 'nodeId', width: 80, align: 'center' },
  { title: '模拟量', dataIndex: 'analogNo', width: 90, align: 'center' },
  { title: '要素', dataIndex: 'elementName', width: 130, ellipsis: true, tooltip: true },
  { title: '实时值', dataIndex: 'dataValue', slotName: 'dataValue', width: 170, align: 'right' },
  { title: '原始值', dataIndex: 'rawValue', width: 120, align: 'right' },
  { title: '设备状态', dataIndex: 'deviceStatus', slotName: 'deviceStatus', width: 120, align: 'center' },
  { title: '数据质量', dataIndex: 'quality', slotName: 'quality', width: 120, align: 'center' },
  { title: '超量程', dataIndex: 'overRange', slotName: 'overRange', width: 100, align: 'center' },
  { title: '采集时间', dataIndex: 'collectTime', width: 180 },
]

const loadDevices = async () => {
  const res = await listDevice({})
  deviceOptions.value = (res.data ?? []) as DeviceResp[]
}

const reset = () => {
  queryForm.description = undefined
  queryForm.deviceId = undefined
  queryForm.deviceStatus = undefined
  queryForm.quality = undefined
  queryForm.overRange = undefined
  search()
}

/**
 * 手动刷新控制（FR-WEB-07）：默认 3 秒自动刷新，可暂停、可改频率，暂停后不再发起任何请求。
 */
const {
  paused,
  intervalSeconds,
  intervalOptions,
  refreshing,
  lastRefreshAt,
  refresh,
} = useAutoRefresh(() => search(), {
  key: 'env/data-latest',
  defaultInterval: 3,
})

const onPausedChange = (value: boolean) => {
  paused.value = value
}

const onIntervalChange = (value: number) => {
  intervalSeconds.value = value
}

onMounted(async () => {
  await loadDevices()
})
</script>

<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1500 }"
      @refresh="search"
    >
      <template #toolbar-left>
        <a-input-search
          v-model="queryForm.description"
          placeholder="搜索设备/要素"
          allow-clear
          style="width: 200px"
          @search="search"
        />
        <a-select v-model="queryForm.deviceId" placeholder="设备" allow-clear style="width: 180px" @change="search">
          <a-option v-for="item in deviceOptions" :key="String(item.id)" :value="String(item.id)">
            {{ item.name }}
          </a-option>
        </a-select>
        <a-select v-model="queryForm.deviceStatus" placeholder="设备状态" allow-clear style="width: 140px" @change="search">
          <a-option value="ONLINE">在线</a-option>
          <a-option value="OFFLINE">离线</a-option>
          <a-option value="COMM_ERROR">通讯异常</a-option>
          <a-option value="DISABLED">停用</a-option>
        </a-select>
        <a-select v-model="queryForm.quality" placeholder="数据质量" allow-clear style="width: 130px" @change="search">
          <a-option value="GOOD">正常</a-option>
          <a-option value="BAD">异常</a-option>
          <a-option value="STALE">陈旧</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <RefreshControl
          :paused="paused"
          :interval-seconds="intervalSeconds"
          :interval-options="intervalOptions"
          :refreshing="refreshing"
          :last-refresh-at="lastRefreshAt"
          @update:paused="onPausedChange"
          @update:interval-seconds="onIntervalChange"
          @refresh="refresh"
        />
      </template>
      <template #dataValue="{ record }">
        <strong>{{ record.dataValue }}</strong>
        <span style="margin-left: 4px">{{ record.unit }}</span>
      </template>
      <template #deviceStatus="{ record }">
        <a-tag :color="statusColorMap[record.deviceStatus] ?? 'gray'">
          {{ record.deviceStatus }}
        </a-tag>
      </template>
      <template #quality="{ record }">
        <a-tag :color="qualityColorMap[record.quality] ?? 'gray'">
          {{ record.quality }}
        </a-tag>
      </template>
      <template #overRange="{ record }">
        <a-tag v-if="record.overRange" color="red">是</a-tag>
        <span v-else>否</span>
      </template>
    </GiTable>
  </GiPageLayout>
</template>
