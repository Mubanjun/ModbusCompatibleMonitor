<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { ReportDeadLetterQuery, ReportDeadLetterResp } from '@/apis'
import { getReportDeadLetter, pageReportDeadLetter, replayReportDeadLetter } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvReportDeadLetter' })

const queryForm = reactive<ReportDeadLetterQuery>({
  sort: ['firstFailTime,desc'],
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageReportDeadLetter({ ...queryForm, ...page }))

const stateColorMap: Record<string, string> = {
  DEAD: 'red',
  PENDING: 'orange',
  REPLAYED: 'green',
  REPLAY_FAILED: 'red',
}

const stateTextMap: Record<string, string> = {
  DEAD: '已死信',
  PENDING: '待重试',
  REPLAYED: '重放成功',
  REPLAY_FAILED: '重放失败',
}

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '上报目标', dataIndex: 'targetName', width: 180, ellipsis: true, tooltip: true },
  { title: '上报规则', dataIndex: 'ruleName', width: 160, ellipsis: true, tooltip: true },
  { title: '目标类型', dataIndex: 'targetType', width: 110, align: 'center' },
  { title: '报文格式', dataIndex: 'formatType', width: 110, align: 'center' },
  { title: '数据条数', dataIndex: 'itemCount', width: 100, align: 'right' },
  { title: '重试次数', dataIndex: 'retryCount', width: 100, align: 'right' },
  { title: '状态', dataIndex: 'state', slotName: 'state', width: 110, align: 'center' },
  { title: '首次失败时间', dataIndex: 'firstFailTime', width: 180 },
  { title: '最近重放时间', dataIndex: 'replayTime', width: 180 },
  { title: '失败原因', dataIndex: 'errorMsg', ellipsis: true, tooltip: true },
  { title: '操作', dataIndex: 'action', slotName: 'action', width: 140, align: 'center', fixed: 'right' },
]

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ReportDeadLetterResp>()

const reset = () => {
  queryForm.description = undefined
  queryForm.targetType = undefined
  queryForm.state = undefined
  search()
}

const onDetail = async (record: ReportDeadLetterResp) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getReportDeadLetter(String(record.id))
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

const onReplay = (record: ReportDeadLetterResp) => {
  Modal.confirm({
    title: '重放死信',
    content: `将按原目标与原报文重新上报「${record.targetName ?? record.id}」，确定继续吗？`,
    async onOk() {
      const res = await replayReportDeadLetter([String(record.id)])
      Message.success(res.data || '已提交重放')
      await search()
    },
  })
}
</script>

<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1700 }"
      @refresh="search"
    >
      <template #toolbar-left>
        <a-input-search
          v-model="queryForm.description"
          placeholder="搜索目标/规则/失败原因"
          allow-clear
          style="width: 220px"
          @search="search"
        />
        <a-select v-model="queryForm.targetType" placeholder="目标类型" allow-clear style="width: 140px" @change="search">
          <a-option value="MQTT">MQTT</a-option>
          <a-option value="DB">数据库</a-option>
          <a-option value="HTTP">HTTP</a-option>
        </a-select>
        <a-select v-model="queryForm.state" placeholder="状态" allow-clear style="width: 140px" @change="search">
          <a-option value="DEAD">已死信</a-option>
          <a-option value="PENDING">待重试</a-option>
          <a-option value="REPLAYED">重放成功</a-option>
          <a-option value="REPLAY_FAILED">重放失败</a-option>
        </a-select>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #state="{ record }">
        <a-tag :color="stateColorMap[record.state] ?? 'gray'">
          {{ stateTextMap[record.state] ?? record.state }}
        </a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link @click="onDetail(record)">详情</a-link>
          <a-link v-permission="['monitor:deadLetter:replay']" @click="onReplay(record)">重放</a-link>
        </a-space>
      </template>
    </GiTable>

    <a-drawer v-model:visible="detailVisible" title="上报死信详情" :width="620" :loading="detailLoading">
      <a-descriptions v-if="detail" :column="1" bordered size="medium">
        <a-descriptions-item label="上报目标">{{ detail.targetName ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="上报规则">{{ detail.ruleName ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="目标类型">{{ detail.targetType ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="报文格式">{{ detail.formatType ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="数据条数">{{ detail.itemCount ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="重试次数">{{ detail.retryCount ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="stateColorMap[detail.state ?? ''] ?? 'gray'">
            {{ stateTextMap[detail.state ?? ''] ?? detail.state }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="首次失败时间">{{ detail.firstFailTime ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="最近重放时间">{{ detail.replayTime ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="失败原因">{{ detail.errorMsg ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="重放结果">{{ detail.replayResult ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="失败报文">
          <pre class="payload">{{ detail.payload ?? '-' }}</pre>
        </a-descriptions-item>
      </a-descriptions>
      <template #footer>
        <a-button v-if="detail" type="primary" @click="onReplay(detail)">重放这条死信</a-button>
      </template>
    </a-drawer>
  </GiPageLayout>
</template>

<style scoped lang="less">
.payload {
  max-height: 320px;
  margin: 0;
  padding: 8px;
  overflow: auto;
  border-radius: 4px;
  background: var(--color-fill-1);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
