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
          placeholder="搜索通知目标/内容"
          allow-clear
          style="width: 230px"
          @search="search"
        />
        <a-select v-model="queryForm.notifyType" placeholder="通知方式" allow-clear style="width: 160px" @change="search">
          <a-option value="MQTT">MQTT</a-option>
          <a-option value="EMAIL">邮件</a-option>
          <a-option value="SMS">短信</a-option>
        </a-select>
        <a-select v-model="queryForm.sendStatus" placeholder="发送状态" allow-clear style="width: 160px" @change="search">
          <a-option value="0">待发送</a-option>
          <a-option value="1">成功</a-option>
          <a-option value="2">失败</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #sendStatus="{ record }">
        <a-tag :color="sendStatusMap[String(record.sendStatus)]?.[1] ?? 'gray'">
          {{ sendStatusMap[String(record.sendStatus)]?.[0] ?? record.sendStatus }}
        </a-tag>
      </template>
    </GiTable>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { type AlarmNotifyQuery, pageAlarmNotify } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvAlarmNotify' })

const sendStatusMap: Record<string, [string, string]> = {
  0: ['待发送', 'gray'],
  1: ['成功', 'green'],
  2: ['失败', 'red'],
}

const queryForm = reactive<AlarmNotifyQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageAlarmNotify({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '报警记录', dataIndex: 'recordId', width: 150, align: 'center' },
  { title: '方式', dataIndex: 'notifyType', width: 100, align: 'center' },
  { title: '目标', dataIndex: 'target', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '内容', dataIndex: 'content', minWidth: 220, ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'sendStatus', slotName: 'sendStatus', width: 100, align: 'center' },
  { title: '重试', dataIndex: 'retryCount', width: 80, align: 'center' },
  { title: '发送时间', dataIndex: 'sendTime', width: 180 },
  { title: '失败原因', dataIndex: 'errorMsg', minWidth: 180, ellipsis: true, tooltip: true },
]

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.notifyType = undefined
  queryForm.sendStatus = undefined
  search()
}
</script>
