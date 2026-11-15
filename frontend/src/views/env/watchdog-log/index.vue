<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1100 }"
      @refresh="search"
    >
      <template #toolbar-left>
        <a-input-search
          v-model="queryForm.description"
          placeholder="搜索进程名/事件详情"
          allow-clear
          style="width: 230px"
          @search="search"
        />
        <a-select v-model="queryForm.eventType" placeholder="事件类型" allow-clear style="width: 160px" @change="search">
          <a-option value="HEARTBEAT_TIMEOUT">心跳超时</a-option>
          <a-option value="CRASH">崩溃</a-option>
          <a-option value="RESTART">重启</a-option>
          <a-option value="SELF_CHECK">自检</a-option>
          <a-option value="REBOOT_LIMIT">重启超限</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #eventType="{ record }">
        <a-tag :color="eventTypeMap[String(record.eventType)]?.[1] ?? 'gray'">
          {{ eventTypeMap[String(record.eventType)]?.[0] ?? record.eventType }}
        </a-tag>
      </template>
    </GiTable>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { type WatchdogLogQuery, pageWatchdogLog } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvWatchdogLog' })

const eventTypeMap: Record<string, [string, string]> = {
  HEARTBEAT_TIMEOUT: ['心跳超时', 'orangered'],
  CRASH: ['崩溃', 'red'],
  RESTART: ['重启', 'arcoblue'],
  SELF_CHECK: ['自检', 'green'],
  REBOOT_LIMIT: ['重启超限', 'purple'],
}

const queryForm = reactive<WatchdogLogQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageWatchdogLog({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '进程', dataIndex: 'processName', width: 160 },
  { title: '事件类型', dataIndex: 'eventType', slotName: 'eventType', width: 150, align: 'center' },
  { title: '事件时间', dataIndex: 'eventTime', width: 180 },
  { title: '事件详情', dataIndex: 'detail', minWidth: 300, ellipsis: true, tooltip: true },
  { title: '累计重启', dataIndex: 'restartCount', width: 100, align: 'center' },
]

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.eventType = undefined
  search()
}
</script>
