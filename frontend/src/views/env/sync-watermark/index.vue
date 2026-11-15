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
          placeholder="搜索表名/主键水位"
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
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
    </GiTable>
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { type SyncWatermarkQuery, pageSyncWatermark } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvSyncWatermark' })

const queryForm = reactive<SyncWatermarkQuery>({
  sort: ['createTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageSyncWatermark({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '目标库', dataIndex: 'targetId', width: 160, align: 'center' },
  { title: '表名', dataIndex: 'tableName', width: 160 },
  { title: '记录时间水位', dataIndex: 'lastRecordTime', width: 200 },
  { title: '主键水位', dataIndex: 'lastId', minWidth: 200, ellipsis: true, tooltip: true },
]

// 重置查询
const reset = () => {
  queryForm.description = undefined
  queryForm.tableName = undefined
  search()
}
</script>
