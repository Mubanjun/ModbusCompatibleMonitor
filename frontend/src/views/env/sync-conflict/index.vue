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
          placeholder="搜索表名/记录主键/详情"
          allow-clear
          style="width: 230px"
          @search="search"
        />
        <a-select v-model="queryForm.tableName" placeholder="表名" allow-clear style="width: 160px" @change="search">
          <a-option value="tbalarm">tbalarm</a-option>
          <a-option value="tbhistory">tbhistory</a-option>
          <a-option value="tbnoticelog">tbnoticelog</a-option>
          <a-option value="tbsyslog">tbsyslog</a-option>
        </a-select>
        <a-select v-model="queryForm.policy" placeholder="冲突策略" allow-clear style="width: 160px" @change="search">
          <a-option value="NEWER_WINS">新数据优先</a-option>
          <a-option value="OVERWRITE">覆盖</a-option>
          <a-option value="SKIP">跳过</a-option>
          <a-option value="MANUAL">人工确认</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #keptSource="{ record }">
        <a-tag :color="keptSourceMap[String(record.keptSource)]?.[1] ?? 'gray'">
          {{ keptSourceMap[String(record.keptSource)]?.[0] ?? record.keptSource }}
        </a-tag>
      </template>
    </GiTable>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { type SyncConflictQuery, pageSyncConflict } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvSyncConflict' })

const keptSourceMap: Record<string, [string, string]> = {
  true: ['是', 'green'],
  false: ['否', 'orangered'],
}

const queryForm = reactive<SyncConflictQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageSyncConflict({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '任务', dataIndex: 'taskId', width: 150, align: 'center' },
  { title: '表名', dataIndex: 'tableName', width: 140 },
  { title: '记录主键', dataIndex: 'recordId', minWidth: 140, ellipsis: true, tooltip: true },
  { title: '来源时间', dataIndex: 'sourceTime', width: 180 },
  { title: '目标时间', dataIndex: 'targetTime', width: 180 },
  { title: '保留来源', dataIndex: 'keptSource', slotName: 'keptSource', width: 100, align: 'center' },
  { title: '策略', dataIndex: 'policy', width: 140, align: 'center' },
  { title: '冲突详情', dataIndex: 'detail', minWidth: 220, ellipsis: true, tooltip: true },
]

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.tableName = undefined
  queryForm.policy = undefined
  search()
}
</script>
