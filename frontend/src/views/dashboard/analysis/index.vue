<template>
  <div class="gi_page">
    <a-space direction="vertical" :size="14" fill>
      <a-card :bordered="false">
        <template #extra>
          <a-radio-group v-model="days" type="button" @change="load">
            <a-radio :value="7">近 7 天</a-radio>
            <a-radio :value="30">近 30 天</a-radio>
          </a-radio-group>
        </template>
        <a-grid :cols="24" :col-gap="14" :row-gap="14">
          <a-grid-item :span="{ xs: 24, lg: 12 }">
            <a-card title="采样量趋势" :bordered="false"><Chart :option="sampleOption" height="280px" /></a-card>
          </a-grid-item>
          <a-grid-item :span="{ xs: 24, lg: 12 }">
            <a-card title="报警数趋势" :bordered="false"><Chart :option="alarmOption" height="280px" /></a-card>
          </a-grid-item>
        </a-grid>
      </a-card>

      <a-grid :cols="24" :col-gap="14" :row-gap="14">
        <a-grid-item :span="{ xs: 24, lg: 10 }">
          <a-card title="要素分布 Top 10" :bordered="false"><Chart :option="elementOption" height="300px" /></a-card>
        </a-grid-item>
        <a-grid-item :span="{ xs: 24, lg: 14 }">
          <a-card title="端口健康" :bordered="false">
            <a-table :columns="portColumns" :data="data.portHealth ?? []" :pagination="false" size="small">
              <template #empty><a-empty description="暂无端口数据" /></template>
            </a-table>
          </a-card>
        </a-grid-item>
      </a-grid>

      <a-grid :cols="24" :col-gap="14" :row-gap="14">
        <a-grid-item :span="{ xs: 24, lg: 12 }">
          <a-card title="上报质量" :bordered="false">
            <a-grid :cols="3">
              <a-grid-item><a-statistic title="死信" :value="data.reportQuality?.deadLetters ?? 0" /></a-grid-item>
              <a-grid-item><a-statistic title="已重放" :value="data.reportQuality?.replayed ?? 0" /></a-grid-item>
              <a-grid-item><a-statistic title="重放失败" :value="data.reportQuality?.replayFailed ?? 0" /></a-grid-item>
            </a-grid>
          </a-card>
        </a-grid-item>
        <a-grid-item :span="{ xs: 24, lg: 12 }">
          <a-card title="异常排行 TOP 10" :bordered="false">
            <a-table :columns="abnormalColumns" :data="data.abnormalTop ?? []" :pagination="false" size="small">
              <template #empty><a-empty description="暂无报警数据" /></template>
            </a-table>
          </a-card>
        </a-grid-item>
      </a-grid>
    </a-space>
  </div>
</template>

<script setup lang="ts">
import { getPlatformAnalysis } from '@/apis'

defineOptions({ name: 'Analysis' })

const days = ref<7 | 30>(7)
const loading = ref(false)
const data = ref<any>({})

const lineOption = (rows: any[], name: string, color: string) => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 42, right: 16, top: 30, bottom: 30 },
  xAxis: { type: 'category', data: (rows ?? []).map((item) => String(item.day).slice(5)) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{ name, type: 'line', smooth: true, areaStyle: { opacity: 0.12 }, itemStyle: { color }, data: (rows ?? []).map((item) => item.value) }],
})

const sampleOption = computed(() => lineOption(data.value.sampleTrend, '采样量', '#165DFF'))
const alarmOption = computed(() => lineOption(data.value.alarmTrend, '报警数', '#F53F3F'))
const elementOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie',
    radius: ['36%', '70%'],
    data: (data.value.elementTop ?? []).map((item: any) => ({ name: item.name, value: item.value })),
  }],
}))

const portColumns = [
  { title: '端口', dataIndex: 'name', ellipsis: true },
  { title: '设备数', dataIndex: 'devices', width: 110 },
]
const abnormalColumns = [
  { title: '设备', dataIndex: 'name', ellipsis: true },
  { title: '报警数', dataIndex: 'value', width: 110 },
]

const load = async () => {
  loading.value = true
  try {
    const res = await getPlatformAnalysis(days.value)
    data.value = res.data ?? {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
