<script setup lang="ts">
import type { EChartsOption } from 'echarts'
import { Message } from '@arco-design/web-vue'
import { LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import type { ChannelResp, DeviceResp } from '@/apis'
import { curveData, listChannel, listDevice } from '@/apis'
import { useAutoRefresh } from '@/hooks'

defineOptions({ name: 'EnvRealtimeCurve' })

use([LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

/** 单条曲线 */
interface CurveSeries {
  key: string
  name: string
  unit?: string
  points: [number, number][]
  latest?: number
  min?: number
  max?: number
}

/** 可选时间窗口（分钟） */
const WINDOW_OPTIONS = [
  { label: '最近 5 分钟', value: 5 },
  { label: '最近 15 分钟', value: 15 },
  { label: '最近 30 分钟', value: 30 },
  { label: '最近 1 小时', value: 60 },
  { label: '最近 2 小时', value: 120 },
]

const devices = ref<DeviceResp[]>([])
const channels = ref<ChannelResp[]>([])
const deviceId = ref<string>()
const selectedKeys = ref<string[]>([])
const windowMinutes = ref(15)
const series = ref<Record<string, CurveSeries>>({})
const emptyTip = ref('')

/** 与后端一致的日期格式（YYYY-MM-DD HH:mm:ss） */
const TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const metricKey = (row: Pick<ChannelResp, 'channelNo' | 'analogNo'>) => `${row.channelNo}-${row.analogNo}`

const metricOptions = computed(() =>
  channels.value.filter((item) => item.enabled).map((item) => ({
    key: metricKey(item),
    label: `${item.channelNo} 号通道 · ${item.analogNo} 路 ${item.elementName ?? item.elementType ?? ''}`,
    unit: item.unit,
  })),
)

const loadDevices = async () => {
  const res = await listDevice({})
  devices.value = (res.data ?? []) as DeviceResp[]
  if (!deviceId.value && devices.value.length) {
    deviceId.value = String(devices.value[0].id)
  }
}

const loadChannels = async () => {
  selectedKeys.value = []
  series.value = {}
  channels.value = []
  if (!deviceId.value) {
    return
  }
  const res = await listChannel({ deviceId: deviceId.value })
  channels.value = (res.data ?? []) as ChannelResp[]
  // 默认最多展示 4 条曲线，避免同屏曲线过多不可读
  selectedKeys.value = metricOptions.value.slice(0, 4).map((item) => item.key)
}

/** 加载全部已选指标在当前窗口内的曲线点 */
const loadCurve = async () => {
  const keys = selectedKeys.value
  if (!deviceId.value || keys.length === 0) {
    series.value = {}
    emptyTip.value = keys.length === 0 ? '请选择至少一个指标' : '请选择设备'
    return
  }
  const endTime = dayjs().format(TIME_FORMAT)
  const startTime = dayjs().subtract(windowMinutes.value, 'minute').format(TIME_FORMAT)
  const rows = await Promise.all(keys.map((key) => {
    const option = metricOptions.value.find((item) => item.key === key)
    // O-4 修复：通道号取自 biz_channel.channelNo，接口查询参数名仍是 nodeId（后端 CurveQuery 的定义）
    const [channelNo, analogNo] = key.split('-').map(Number)
    return curveData({
      deviceId: deviceId.value,
      nodeId: channelNo,
      analogNo,
      startTime,
      endTime,
      limit: 5000,
    }).then((res) => ({ key, option, list: res.data ?? [] }))
  }))

  const next: Record<string, CurveSeries> = {}
  rows.forEach((item) => {
    const points: [number, number][] = []
    item.list.forEach((row) => {
      const value = row.dataValue == null ? undefined : Number(row.dataValue)
      if (value === undefined || Number.isNaN(value)) {
        return
      }
      points.push([dayjs(row.recordTime).valueOf(), value])
    })
    points.sort((a, b) => a[0] - b[0])
    const values = points.map((point) => point[1])
    next[item.key] = {
      key: item.key,
      name: item.option?.label ?? item.key,
      unit: item.option?.unit,
      points,
      latest: values.length ? values[values.length - 1] : undefined,
      min: values.length ? Math.min(...values) : undefined,
      max: values.length ? Math.max(...values) : undefined,
    }
  })
  series.value = next
  emptyTip.value = Object.values(next).every((item) => item.points.length === 0)
    ? '当前窗口内没有采样点（设备离线或尚未产生数据）'
    : ''
}

const {
  paused,
  intervalSeconds,
  intervalOptions,
  refreshing,
  lastRefreshAt,
  refresh,
} = useAutoRefresh(loadCurve, {
  key: 'env/realtime-curve',
  defaultInterval: 5,
})

const onPausedChange = (value: boolean) => {
  paused.value = value
}

const onIntervalChange = (value: number) => {
  intervalSeconds.value = value
}

const onDeviceChange = async () => {
  await loadChannels()
  await loadCurve()
}

const axisLabelFormatter = (value: number) => {
  if (windowMinutes.value <= 5) {
    return dayjs(value).format('HH:mm:ss')
  }
  if (windowMinutes.value <= 120) {
    return dayjs(value).format('HH:mm')
  }
  return dayjs(value).format('MM-DD HH:mm')
}

const yAxisName = computed(() => {
  const units = [...new Set(Object.values(series.value).map((item) => item.unit).filter(Boolean))]
  return units.length === 1 ? String(units[0]) : ''
})

const chartOption = computed<EChartsOption>(() => {
  const list = Object.values(series.value)
  return {
    animation: false,
    color: ['#165dff', '#00b42a', '#ff7d00', '#f53f3f', '#722ed1', '#0fc6c2'],
    grid: { left: 60, right: 24, top: 44, bottom: 62 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      valueFormatter: (value: unknown) => (value == null ? '-' : String(value)),
    },
    legend: { type: 'scroll', top: 6, data: list.map((item) => item.name) },
    xAxis: {
      type: 'time',
      boundaryGap: false,
      axisLabel: { formatter: axisLabelFormatter, hideOverlap: true },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      name: yAxisName.value,
      scale: true,
      splitLine: { lineStyle: { type: 'dashed' } },
    },
    dataZoom: [
      { type: 'inside' },
      { type: 'slider', height: 18, bottom: 10 },
    ],
    series: list.map((item) => ({
      name: item.name,
      type: 'line' as const,
      showSymbol: false,
      connectNulls: false,
      sampling: 'lttb' as const,
      lineStyle: { width: 1.6 },
      data: item.points,
    })),
  }
})

const formatValue = (value?: number) => (value == null ? '-' : value.toFixed(2))

onMounted(async () => {
  await loadDevices()
  await loadChannels()
  await loadCurve()
  if (metricOptions.value.length === 0) {
    Message.info('当前设备没有启用中的通道配置，请先在「采集通道」中配置')
  }
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false" class="curve-card">
      <div class="curve-toolbar">
        <a-select
          v-model="deviceId"
          placeholder="选择设备"
          style="width: 220px"
          @change="onDeviceChange"
        >
          <a-option
            v-for="item in devices"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.name }}
          </a-option>
        </a-select>
        <a-select
          v-model="selectedKeys"
          placeholder="选择指标（可多选）"
          multiple
          allow-clear
          :max-tag-count="3"
          style="width: 460px"
          @change="loadCurve"
        >
          <a-option
            v-for="item in metricOptions"
            :key="item.key"
            :value="item.key"
          >
            {{ item.label }}
          </a-option>
        </a-select>
        <a-select
          v-model="windowMinutes"
          style="width: 150px"
          @change="loadCurve"
        >
          <a-option
            v-for="item in WINDOW_OPTIONS"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </a-option>
        </a-select>
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
      </div>

      <div
        class="curve-panel"
        data-test="realtime-curve"
      >
        <a-empty
          v-if="emptyTip"
          :description="emptyTip"
          class="curve-empty"
        />
        <VChart
          v-else
          :option="chartOption"
          autoresize
          style="height: 420px"
        />
      </div>

      <div
        v-if="!emptyTip"
        class="curve-stats"
        data-test="curve-stats"
      >
        <a-card
          v-for="item in Object.values(series)"
          :key="item.key"
          :bordered="true"
          size="small"
          class="curve-stats__item"
        >
          <div class="curve-stats__name">{{ item.name }}</div>
          <div class="curve-stats__value">
            {{ formatValue(item.latest) }}
            <span class="curve-stats__unit">{{ item.unit }}</span>
          </div>
          <div class="curve-stats__meta">
            样本 {{ item.points.length }} 点 · 最小 {{ formatValue(item.min) }} · 最大 {{ formatValue(item.max) }}
          </div>
        </a-card>
      </div>
    </a-card>
  </GiPageLayout>
</template>

<style scoped lang="less">
.curve-card {
  min-height: 560px;
}

.curve-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.curve-panel {
  position: relative;
  min-height: 420px;
}

.curve-empty {
  padding-top: 120px;
}

.curve-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.curve-stats__item {
  min-width: 220px;
  flex: 1 1 220px;
}

.curve-stats__name {
  color: var(--color-text-2);
  font-size: 13px;
}

.curve-stats__value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 600;
}

.curve-stats__unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-3);
}

.curve-stats__meta {
  margin-top: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
