<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import type { DataAggregateResp, DataQuery, DataRecalculateResp, DataResp, DeviceResp } from '@/apis'
import { aggregateData, correctData, exportData, listDevice, pageData, recalculateData } from '@/apis'
import { useDownload, useTable } from '@/hooks'

defineOptions({ name: 'EnvData' })

const deviceOptions = ref<DeviceResp[]>([])
const aggregateVisible = ref(false)
const aggregateLoading = ref(false)
const aggregateRows = ref<DataAggregateResp[]>([])

const queryForm = reactive<DataQuery>({
  sort: ['recordTime,desc'],
  recalculate: false,
})

const correctVisible = ref(false)
const correctSaving = ref(false)
const correctRow = ref<DataResp>()
const correctForm = reactive<{ dataValue?: number, rawValue?: number, reason?: string }>({})

const recalcVisible = ref(false)
const recalcLoading = ref(false)
const recalcResult = ref<DataRecalculateResp>()

const timeRange = ref<string[]>([])

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageData({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '记录时间', dataIndex: 'recordTime', width: 180 },
  { title: '设备', dataIndex: 'deviceName', width: 170, ellipsis: true, tooltip: true },
  { title: '设备地址', dataIndex: 'deviceAddr', width: 100, align: 'center' },
  { title: '节点', dataIndex: 'nodeId', width: 80, align: 'center' },
  { title: '模拟量', dataIndex: 'analogNo', width: 90, align: 'center' },
  { title: '要素', dataIndex: 'elementName', width: 120, ellipsis: true, tooltip: true },
  { title: '原始值', dataIndex: 'rawValue', width: 120, align: 'right' },
  { title: '工程值', dataIndex: 'dataValue', slotName: 'dataValue', width: 130, align: 'right' },
  { title: '单位', dataIndex: 'unit', width: 90 },
  { title: '报警', dataIndex: 'alarmFlag', slotName: 'alarmFlag', width: 90, align: 'center' },
  { title: '操作', dataIndex: 'action', slotName: 'action', width: 90, align: 'center' },
]

const loadDevices = async () => {
  const res = await listDevice({})
  deviceOptions.value = (res.data ?? []) as DeviceResp[]
}

const searchWithTime = () => {
  queryForm.startTime = timeRange.value?.[0]
  queryForm.endTime = timeRange.value?.[1]
  search()
}

const reset = () => {
  queryForm.description = undefined
  queryForm.deviceId = undefined
  queryForm.nodeId = undefined
  queryForm.alarmFlag = undefined
  queryForm.startTime = undefined
  queryForm.endTime = undefined
  queryForm.recalculate = false
  timeRange.value = []
  search()
}

const onExport = () => {
  useDownload(() => exportData(queryForm))
}

const onAggregate = async () => {
  if (!queryForm.startTime || !queryForm.endTime) {
    Message.warning('请先选择时间范围再聚合')
    return
  }
  aggregateVisible.value = true
  aggregateLoading.value = true
  try {
    const res = await aggregateData({ ...queryForm, granularity: queryForm.granularity ?? 'HOUR' })
    aggregateRows.value = res.data ?? []
  } finally {
    aggregateLoading.value = false
  }
}

const openCorrect = (record: DataResp) => {
  correctRow.value = record
  correctForm.dataValue = record.dataValue
  correctForm.rawValue = record.rawValue
  correctForm.reason = undefined
  correctVisible.value = true
}

const onCorrect = async () => {
  if (!correctRow.value) {
    return
  }
  if (correctForm.dataValue == null && correctForm.rawValue == null) {
    Message.warning('请至少填写工程值或原始值')
    return
  }
  correctSaving.value = true
  try {
    const res = await correctData({
      id: String(correctRow.value.id),
      dataValue: correctForm.dataValue,
      rawValue: correctForm.rawValue,
      reason: correctForm.reason,
    })
    Message.success(`已修正：${res.data?.beforeValue} → ${res.data?.afterValue}`)
    correctVisible.value = false
    await search()
  } finally {
    correctSaving.value = false
  }
}

const onRecalculate = async (dryRun: boolean) => {
  if (!queryForm.startTime || !queryForm.endTime) {
    Message.warning('请先选择时间范围再补算')
    return
  }
  recalcLoading.value = true
  try {
    const res = await recalculateData({
      deviceId: queryForm.deviceId,
      nodeId: queryForm.nodeId,
      startTime: queryForm.startTime,
      endTime: queryForm.endTime,
      dryRun,
    })
    recalcResult.value = res.data
    Message.success(`${dryRun ? '预演' : '补算'}完成：更新 ${res.data?.updated}，无需变更 ${res.data?.unchanged}`)
    if (!dryRun) {
      await search()
    }
  } finally {
    recalcLoading.value = false
  }
}

onMounted(loadDevices)
</script>

<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: '100%', y: '100%', minWidth: 1300 }"
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
        <a-input-number v-model="queryForm.nodeId" placeholder="节点" style="width: 100px" @change="search" />
        <a-select v-model="queryForm.alarmFlag" placeholder="是否报警" allow-clear style="width: 120px" @change="search">
          <a-option :value="0">未报警</a-option>
          <a-option :value="1">已报警</a-option>
        </a-select>
        <a-range-picker
          v-model="timeRange"
          show-time
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 380px"
          @change="searchWithTime"
        />
        <a-tooltip content="按当前通道系数重新计算工程值，仅影响展示，不修改历史数据">
          <a-checkbox v-model="queryForm.recalculate" @change="search">重算工程值</a-checkbox>
        </a-tooltip>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button @click="onAggregate">
          <template #icon><icon-bar-chart /></template>
          <template #default>聚合</template>
        </a-button>
        <a-button v-permission="['monitor:data:correct']" @click="recalcVisible = true">
          <template #icon><icon-edit /></template>
          <template #default>批量补算</template>
        </a-button>
        <a-button v-permission="['monitor:data:export']" @click="onExport">
          <template #icon><icon-download /></template>
          <template #default>导出</template>
        </a-button>
      </template>
      <template #dataValue="{ record }">
        <span>{{ record.dataValue }}</span>
        <a-tag v-if="record.recalculated" size="small" color="orange" style="margin-left: 6px">重算</a-tag>
      </template>
      <template #alarmFlag="{ record }">
        <a-tag :color="record.alarmFlag === 1 ? 'red' : 'green'">
          {{ record.alarmFlag === 1 ? '报警' : '正常' }}
        </a-tag>
      </template>
      <template #action="{ record }">
        <a-link v-permission="['monitor:data:correct']" @click="openCorrect(record)">
          修正
        </a-link>
      </template>
    </GiTable>

    <a-modal
      v-model:visible="correctVisible"
      title="修正历史数据（落库，操作留痕）"
      :width="520"
      :ok-loading="correctSaving"
      @ok="onCorrect"
    >
      <a-form :model="correctForm" auto-label-width>
        <a-form-item label="记录">
          <span>{{ correctRow?.deviceName }} / 节点 {{ correctRow?.nodeId }} / 模拟量 {{ correctRow?.analogNo }} / {{ correctRow?.recordTime }}</span>
        </a-form-item>
        <a-form-item label="原工程值">
          <span>{{ correctRow?.dataValue }} {{ correctRow?.unit }}</span>
        </a-form-item>
        <a-form-item label="修正工程值">
          <a-input-number v-model="correctForm.dataValue" placeholder="留空表示不修改工程值" style="width: 220px" />
        </a-form-item>
        <a-form-item label="修正原始值">
          <a-input-number v-model="correctForm.rawValue" placeholder="只填原始值将按通道系数补算工程值" style="width: 220px" />
        </a-form-item>
        <a-form-item label="修正原因">
          <a-input v-model="correctForm.reason" placeholder="写入操作日志，便于追溯" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="recalcVisible" title="批量补算历史数据" :width="640" :footer="false">
      <a-alert type="info" style="margin-bottom: 12px">
        按当前通道系数与偏移，对所选时间范围内已有原始值重算工程值；预演不会写库。
      </a-alert>
      <a-space>
        <a-button :loading="recalcLoading" @click="onRecalculate(true)">
          预演
        </a-button>
        <a-button type="primary" :loading="recalcLoading" @click="onRecalculate(false)">
          执行补算
        </a-button>
      </a-space>
      <a-descriptions v-if="recalcResult" :column="3" size="small" bordered style="margin-top: 12px">
        <a-descriptions-item label="扫描">
          {{ recalcResult.scanned }}
        </a-descriptions-item>
        <a-descriptions-item label="更新">
          {{ recalcResult.updated }}
        </a-descriptions-item>
        <a-descriptions-item label="无需变更">
          {{ recalcResult.unchanged }}
        </a-descriptions-item>
        <a-descriptions-item label="跳过">
          {{ recalcResult.skipped }}
        </a-descriptions-item>
        <a-descriptions-item label="失败">
          {{ recalcResult.failed }}
        </a-descriptions-item>
        <a-descriptions-item label="耗时">
          {{ recalcResult.durationMs }} ms
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <a-modal v-model:visible="aggregateVisible" title="按时间桶聚合" :width="720" :footer="false">
      <a-select v-model="queryForm.granularity" placeholder="粒度" allow-clear style="width: 160px; margin-bottom: 12px" @change="onAggregate">
        <a-option value="MINUTE">分钟</a-option>
        <a-option value="HOUR">小时</a-option>
        <a-option value="DAY">天</a-option>
      </a-select>
      <a-table
        row-key="bucket"
        :data="aggregateRows"
        :loading="aggregateLoading"
        :pagination="false"
        :bordered="{ cell: true }"
        :scroll="{ y: 380 }"
      >
        <template #columns>
          <a-table-column title="时间桶" data-index="bucket" :width="180" />
          <a-table-column title="平均" data-index="avgValue" align="right" />
          <a-table-column title="最小" data-index="minValue" align="right" />
          <a-table-column title="最大" data-index="maxValue" align="right" />
          <a-table-column title="样本数" data-index="sampleCount" align="right" />
        </template>
      </a-table>
    </a-modal>
  </GiPageLayout>
</template>
