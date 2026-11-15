<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import type { SignalFrameResp, SignalLogQuery } from '@/apis'
import { getSignalCursor, listRecentSignal, pageSignalLog } from '@/apis'
import { useTable } from '@/hooks'

defineOptions({ name: 'EnvSignal' })

const directionMap: Record<string, [string, string]> = {
  OUT: ['发送', 'arcoblue'],
  IN: ['接收', 'green'],
  ERROR: ['异常', 'red'],
}

const live = ref(false)
const liveLoading = ref(false)
const liveList = ref<SignalFrameResp[]>([])
let cursor = 0
let timer: ReturnType<typeof setInterval> | undefined

const queryForm = reactive<SignalLogQuery>({
  sort: ['traceTime,desc'],
})

const timeRange = ref<string[]>([])

const {
  tableData: dataList,
  loading,
  pagination,
  search,
} = useTable((page) => pageSignalLog({ ...queryForm, ...page }))

const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
  },
  { title: '报文时间', dataIndex: 'traceTime', width: 180 },
  { title: '端口', dataIndex: 'portName', width: 130 },
  { title: '设备', dataIndex: 'deviceName', slotName: 'deviceName', width: 150, ellipsis: true, tooltip: true },
  { title: '方向', dataIndex: 'direction', slotName: 'direction', width: 90, align: 'center' },
  { title: '报文', dataIndex: 'frame', minWidth: 240, ellipsis: true, tooltip: true },
  { title: '解析', dataIndex: 'parsed', minWidth: 220, ellipsis: true, tooltip: true },
  { title: '耗时(ms)', dataIndex: 'durationMs', width: 100, align: 'center' },
  { title: '结果', dataIndex: 'success', slotName: 'success', width: 90, align: 'center' },
]

const liveColumns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 80,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1),
  },
  {
    title: '时间',
    width: 150,
    render: ({ record }: { record: SignalFrameResp }) =>
      h('span', {}, new Date(record.timeMillis).toLocaleTimeString('zh-CN', { hour12: false })),
  },
  { title: '设备', dataIndex: 'deviceName', width: 140, ellipsis: true, tooltip: true },
  { title: '方向', dataIndex: 'direction', slotName: 'liveDirection', width: 80, align: 'center' },
  { title: '报文', dataIndex: 'frame', minWidth: 220, ellipsis: true, tooltip: true },
  { title: '解析', dataIndex: 'parsed', minWidth: 200, ellipsis: true, tooltip: true },
]

const poll = async () => {
  if (liveLoading.value) {
    return
  }
  liveLoading.value = true
  try {
    const res = await listRecentSignal(cursor, 200)
    const list = res.data ?? []
    if (list.length) {
      cursor = list[list.length - 1].sequence
      liveList.value = [...list.reverse(), ...liveList.value].slice(0, 300)
    }
  } finally {
    liveLoading.value = false
  }
}

const toggleLive = async (value: boolean | string | number) => {
  // 缺陷 64：开关是受控的（:model-value="live"），这里必须回写 live，否则开关永远弹回「关闭」、
  // v-if="live" 的实时表格永不渲染、且因 value 恒为 false 导致 else 分支不可达（关不掉）。
  live.value = Boolean(value)
  if (value) {
    // 重复点击时先清掉旧定时器，避免多个 2 秒轮询叠加
    if (timer) {
      clearInterval(timer)
      timer = undefined
    }
    if (!cursor) {
      const res = await getSignalCursor()
      cursor = Number(res.data ?? 0)
      liveList.value = []
    }
    await poll()
    timer = setInterval(poll, 2000)
    Message.info('实时信令已开启（每 2 秒增量拉取）')
  } else if (timer) {
    clearInterval(timer)
    timer = undefined
    Message.info('实时信令已暂停')
  }
}

const searchWithTime = () => {
  queryForm.startTime = timeRange.value?.[0]
  queryForm.endTime = timeRange.value?.[1]
  search()
}

const reset = () => {
  queryForm.description = undefined
  queryForm.direction = undefined
  queryForm.success = undefined
  queryForm.portId = undefined
  queryForm.deviceId = undefined
  queryForm.startTime = undefined
  queryForm.endTime = undefined
  timeRange.value = []
  search()
}

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" style="margin-bottom: 12px">
      <a-space>
        <a-switch :model-value="live" @change="toggleLive">
          <template #checked>实时流已开启</template>
          <template #unchecked>实时流已关闭</template>
        </a-switch>
        <a-tag color="arcoblue">本次会话已接收 {{ liveList.length }} 条</a-tag>
        <a-button size="small" @click="liveList = []">清空实时流</a-button>
      </a-space>
      <a-table
        v-if="live"
        row-key="sequence"
        :data="liveList"
        :columns="liveColumns"
        :pagination="false"
        :loading="liveLoading"
        size="small"
        style="margin-top: 12px"
        :scroll="{ y: 260 }"
      >
        <template #liveDirection="{ record }">
          <a-tag :color="directionMap[record.direction]?.[1] ?? 'gray'">
            {{ directionMap[record.direction]?.[0] ?? record.direction }}
          </a-tag>
        </template>
      </a-table>
    </a-card>

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
          placeholder="搜索报文/解析/端口"
          allow-clear
          style="width: 220px"
          @search="search"
        />
        <a-select v-model="queryForm.direction" placeholder="方向" allow-clear style="width: 120px" @change="search">
          <a-option value="OUT">发送</a-option>
          <a-option value="IN">接收</a-option>
          <a-option value="ERROR">异常</a-option>
        </a-select>
        <a-select v-model="queryForm.success" placeholder="结果" allow-clear style="width: 110px" @change="search">
          <a-option :value="true">成功</a-option>
          <a-option :value="false">失败</a-option>
        </a-select>
        <a-range-picker
          v-model="timeRange"
          show-time
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 380px"
          @change="searchWithTime"
        />
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #deviceName="{ record }">
        {{ record.deviceName ?? record.deviceId ?? '-' }}
      </template>
      <template #direction="{ record }">
        <a-tag :color="directionMap[record.direction]?.[1] ?? 'gray'">
          {{ directionMap[record.direction]?.[0] ?? record.direction }}
        </a-tag>
      </template>
      <template #success="{ record }">
        <a-tag :color="record.success ? 'green' : 'red'">{{ record.success ? '成功' : '失败' }}</a-tag>
      </template>
    </GiTable>
  </GiPageLayout>
</template>
