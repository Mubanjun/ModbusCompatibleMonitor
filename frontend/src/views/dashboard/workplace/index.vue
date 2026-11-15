<template>
  <div class="gi_page">
    <a-spin :loading="loading" style="width: 100%">
      <a-space direction="vertical" :size="14" fill>
        <a-grid :cols="24" :col-gap="14" :row-gap="14">
          <a-grid-item :span="{ xs: 24, md: 8 }">
            <a-card title="后端进程运行时长" :bordered="false"><div class="runtime">{{ formatDuration(data.runtimes?.processUptimeMs) }}</div></a-card>
          </a-grid-item>
          <a-grid-item :span="{ xs: 24, md: 8 }">
            <a-card title="管理器连续运行时长" :bordered="false"><div class="runtime">{{ formatDuration(data.runtimes?.managerUptimeMs) }}</div></a-card>
          </a-grid-item>
          <a-grid-item :span="{ xs: 24, md: 8 }">
            <a-card title="服务可用时长" :bordered="false"><div class="runtime">{{ formatDuration(data.runtimes?.serviceUptimeMs) }}</div></a-card>
          </a-grid-item>
        </a-grid>

        <a-grid :cols="24" :col-gap="14" :row-gap="14">
          <a-grid-item :span="{ xs: 24, lg: 12 }">
            <a-card title="采集概览" :bordered="false">
              <a-grid :cols="4">
                <a-grid-item><a-statistic title="设备" :value="data.acquisition?.devices ?? 0" /></a-grid-item>
                <a-grid-item><a-statistic title="传感器" :value="data.acquisition?.sensors ?? 0" /></a-grid-item>
                <a-grid-item><a-statistic title="今日读数" :value="data.acquisition?.todayReadings ?? 0" /></a-grid-item>
                <a-grid-item><a-statistic title="在线设备" :value="data.acquisition?.onlineDevices ?? 0" /></a-grid-item>
              </a-grid>
            </a-card>
          </a-grid-item>
          <a-grid-item :span="{ xs: 24, lg: 12 }">
            <a-card title="自检与待办" :bordered="false">
              <p>自检：{{ data.selfCheck?.passed ?? 0 }} / {{ data.selfCheck?.total ?? 0 }} 通过</p>
              <p>未处理报警：{{ data.todos?.unhandledAlarms ?? 0 }}；上报死信：{{ data.todos?.deadLetters ?? 0 }}</p>
              <a-progress :percent="checkPercent" :show-text="false" />
            </a-card>
          </a-grid-item>
        </a-grid>
        <a-grid :cols="24" :col-gap="14" :row-gap="14">
          <a-grid-item :span="{ xs: 24, lg: 12 }">
            <a-card title="分组分布 Top 10" :bordered="false">
              <div v-for="group in data.groups ?? []" :key="group.name" class="group-row">
                <span class="group-name">{{ group.name }}</span>
                <a-progress :percent="groupPercent(group.value)" :show-text="false" />
                <span>{{ group.value }}</span>
              </div>
              <a-empty v-if="(data.groups ?? []).length === 0" description="暂无分组数据" />
            </a-card>
          </a-grid-item>
          <a-grid-item :span="{ xs: 24, lg: 12 }">
            <a-card title="最近 10 条报警" :bordered="false">
              <a-table :columns="alarmColumns" :data="data.recentAlarms ?? []" :pagination="false" size="small">
                <template #empty><a-empty description="暂无报警" /></template>
              </a-table>
            </a-card>
          </a-grid-item>
        </a-grid>
      </a-space>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { getPlatformOverview } from '@/apis'

defineOptions({ name: 'Workplace' })

const loading = ref(false)
const data = ref<any>({})
const timer = ref<ReturnType<typeof setInterval>>()

const checkPercent = computed(() => {
  const total = Number(data.value.selfCheck?.total ?? 0)
  return total === 0 ? 0 : Math.round((Number(data.value.selfCheck?.passed ?? 0) / total) * 100)
})
const maxGroup = computed(() => Math.max(1, ...((data.value.groups ?? []).map((item: any) => Number(item.value ?? 0)) as number[])))
const groupPercent = (value: number) => Math.round((Number(value ?? 0) / maxGroup.value) * 100)

const formatDuration = (ms?: number) => {
  const seconds = Math.max(0, Math.floor(Number(ms ?? 0) / 1000))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return days > 0 ? `${days}天 ${hours}小时` : `${hours}小时 ${minutes}分`
}

const alarmColumns = [
  { title: '设备', dataIndex: 'deviceName', ellipsis: true },
  { title: '类型', dataIndex: 'alarmType', width: 110 },
  { title: '信息', dataIndex: 'alarmMessage', ellipsis: true },
  { title: '时间', dataIndex: 'recordTime', width: 170 },
]

const load = async () => {
  loading.value = true
  try {
    const res = await getPlatformOverview()
    data.value = res.data ?? {}
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer.value = setInterval(load, 30_000)
})
onBeforeUnmount(() => timer.value && clearInterval(timer.value))
</script>

<style scoped>
.runtime {
  font-size: 24px;
  font-weight: 600;
  line-height: 38px;
}
.group-row {
  display: grid;
  grid-template-columns: 140px 1fr 40px;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
.group-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
