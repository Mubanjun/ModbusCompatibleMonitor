<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import type { DataLatestResp, DeviceResp, PortResp } from '@/apis'
import { listDataLatest, listDevice, listPort } from '@/apis'
import { useAutoRefresh } from '@/hooks'

defineOptions({ name: 'EnvMultiView' })

/** 视图组织方式（FR-WEB-04：按分组、端口、区域） */
type GroupBy = 'groupName' | 'portId' | 'location'

interface ViewDevice {
  device: DeviceResp
  rows: DataLatestResp[]
}

interface ViewGroup {
  key: string
  title: string
  subtitle: string
  devices: ViewDevice[]
}

const GROUP_OPTIONS: { label: string, value: GroupBy }[] = [
  { label: '按分组', value: 'groupName' },
  { label: '按端口', value: 'portId' },
  { label: '按区域', value: 'location' },
]

/** 大屏设计尺寸（缩放基准） */
const SCREEN_WIDTH = 1920
const SCREEN_HEIGHT = 1080
/** 大屏每屏展示的分组数 */
const GROUPS_PER_PAGE = 4

const groupBy = ref<GroupBy>('groupName')
const devices = ref<DeviceResp[]>([])
const ports = ref<PortResp[]>([])
const latestRows = ref<DataLatestResp[]>([])
const screenMode = ref(false)
const carouselRunning = ref(true)
const carouselSeconds = ref(10)
const pageIndex = ref(0)

const { width, height } = useWindowSize()

/** 大屏等比缩放：按窗口与设计尺寸的比例取小值，保证不变形、不出现滚动条 */
const screenScale = computed(() => {
  if (!screenMode.value) {
    return 1
  }
  const scale = Math.min(width.value / SCREEN_WIDTH, height.value / SCREEN_HEIGHT)
  return Number.isFinite(scale) && scale > 0 ? Number(scale.toFixed(4)) : 1
})

const qualityColor = (row: DataLatestResp) => {
  if (row.overRange) {
    return 'red'
  }
  if (row.quality === 'BAD') {
    return 'red'
  }
  if (row.quality === 'STALE') {
    return 'orange'
  }
  return 'arcoblue'
}

const statusColorMap: Record<string, string> = {
  ONLINE: 'green',
  OFFLINE: 'red',
  DISABLED: 'gray',
  ALARM: 'orangered',
  COMM_ERROR: 'orange',
  UNKNOWN: 'gray',
}

const portName = (portId?: string) =>
  ports.value.find((item) => String(item.id) === String(portId))?.name ?? String(portId ?? '未指定端口')

/** 按当前组织方式分组 */
const groups = computed<ViewGroup[]>(() => {
  const map = new Map<string, ViewDevice[]>()
  devices.value.forEach((device) => {
    let title = '未分组'
    if (groupBy.value === 'groupName') {
      title = device.groupName || '未分组'
    } else if (groupBy.value === 'location') {
      title = device.location || '未设置区域'
    } else {
      title = portName(device.portId)
    }
    const list = map.get(title) ?? []
    list.push({
      device,
      rows: latestRows.value.filter((row) => String(row.deviceId) === String(device.id)),
    })
    map.set(title, list)
  })
  return [...map.entries()].map(([title, list]) => ({
    key: title,
    title,
    subtitle: `${list.length} 台设备 · ${list.reduce((sum, item) => sum + item.rows.length, 0)} 个通道`,
    devices: list,
  }))
})

/** 大屏轮播页数 */
const pageCount = computed(() => Math.max(1, Math.ceil(groups.value.length / GROUPS_PER_PAGE)))

/** 当前页展示的分组（非大屏模式展示全部分组） */
const visibleGroups = computed(() => {
  if (!screenMode.value) {
    return groups.value
  }
  const start = (pageIndex.value % pageCount.value) * GROUPS_PER_PAGE
  return groups.value.slice(start, start + GROUPS_PER_PAGE)
})

const loadData = async () => {
  const [deviceRes, portRes, latestRes] = await Promise.all([
    listDevice({}),
    listPort({}),
    listDataLatest({}),
  ])
  devices.value = (deviceRes.data ?? []) as DeviceResp[]
  ports.value = (portRes.data ?? []) as PortResp[]
  latestRows.value = (latestRes.data ?? []) as DataLatestResp[]
}

const {
  paused,
  intervalSeconds,
  intervalOptions,
  refreshing,
  lastRefreshAt,
  refresh,
} = useAutoRefresh(loadData, {
  key: 'env/multi-view',
  defaultInterval: 5,
})

const onPausedChange = (value: boolean) => {
  paused.value = value
}

const onIntervalChange = (value: number) => {
  intervalSeconds.value = value
}

/** 大屏轮播定时器：只在「大屏 + 运行 + 多于一屏」时转动 */
let carouselTimer: ReturnType<typeof setInterval> | undefined

const stopCarousel = () => {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = undefined
  }
}

const startCarousel = () => {
  stopCarousel()
  if (!screenMode.value || !carouselRunning.value || pageCount.value <= 1) {
    return
  }
  carouselTimer = setInterval(() => {
    pageIndex.value = (pageIndex.value + 1) % pageCount.value
  }, carouselSeconds.value * 1000)
}

const toggleScreenMode = () => {
  screenMode.value = !screenMode.value
  pageIndex.value = 0
  if (!screenMode.value) {
    stopCarousel()
    return
  }
  startCarousel()
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && screenMode.value) {
    toggleScreenMode()
  }
}

watch([screenMode, carouselRunning, pageCount, carouselSeconds], startCarousel)

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  await loadData()
  startCarousel()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  stopCarousel()
})
</script>

<template>
  <GiPageLayout>
    <a-card :bordered="false">
      <div class="view-toolbar">
        <a-radio-group v-model="groupBy" type="button">
          <a-radio
            v-for="item in GROUP_OPTIONS"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </a-radio>
        </a-radio-group>
        <a-button type="primary" @click="toggleScreenMode">
          <template #icon><icon-fullscreen /></template>
          {{ screenMode ? '退出大屏' : '进入大屏' }}
        </a-button>
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
      <div class="view-tip">
        共 {{ groups.length }} 个视图分组、{{ devices.length }} 台设备、{{ latestRows.length }} 个通道实时值；
        大屏模式每 {{ carouselSeconds }} 秒自动轮播并按窗口等比缩放（Esc 退出）。
      </div>
    </a-card>

    <div
      class="view-grid"
      :class="{ 'view-grid--screen': screenMode }"
      data-test="multi-view-grid"
    >
      <a-card
        v-for="group in visibleGroups"
        :key="group.key"
        :bordered="true"
        :title="group.title"
        class="view-group"
      >
        <template #extra>
          <span class="view-group__extra">{{ group.subtitle }}</span>
        </template>
        <div
          v-for="item in group.devices"
          :key="String(item.device.id)"
          class="view-device"
        >
          <div class="view-device__head">
            <span class="view-device__name">{{ item.device.name }}</span>
            <a-tag :color="statusColorMap[item.rows[0]?.deviceStatus ?? 'UNKNOWN'] ?? 'gray'">
              {{ item.rows[0]?.deviceStatus ?? 'UNKNOWN' }}
            </a-tag>
            <span class="view-device__addr">地址 {{ item.device.slaveAddr }}</span>
          </div>
          <div
            v-if="item.rows.length === 0"
            class="view-device__empty"
          >
            暂无实时值
          </div>
          <div
            v-else
            class="view-values"
          >
            <div
              v-for="row in item.rows"
              :key="row.id"
              class="view-value"
              :class="{ 'view-value--bad': row.quality !== 'GOOD' || row.overRange }"
            >
              <span class="view-value__label">{{ row.elementName ?? row.elementType ?? `${row.nodeId}-${row.analogNo}` }}</span>
              <span
                class="view-value__number"
                :style="{ color: `var(--color-${qualityColor(row)}-6)` }"
              >
                {{ row.dataValue == null ? '-' : Number(row.dataValue).toFixed(2) }}
              </span>
              <span class="view-value__unit">{{ row.unit }}</span>
            </div>
          </div>
        </div>
      </a-card>
    </div>

    <div
      v-if="screenMode"
      class="screen-bar"
      data-test="screen-bar"
    >
      <span data-test="carousel-indicator">{{ pageIndex + 1 }} / {{ pageCount }}</span>
      <a-select
        v-model="carouselSeconds"
        size="small"
        style="width: 110px"
        data-test="carousel-interval"
      >
        <a-option :value="5">
          每 5 秒
        </a-option>
        <a-option :value="10">
          每 10 秒
        </a-option>
        <a-option :value="30">
          每 30 秒
        </a-option>
      </a-select>
      <a-button
        size="small"
        data-test="carousel-toggle"
        @click="carouselRunning = !carouselRunning"
      >
        {{ carouselRunning ? '暂停轮播' : '开始轮播' }}
      </a-button>
      <span data-test="screen-scale">缩放 {{ screenScale }}</span>
      <a-button size="small" @click="toggleScreenMode">
        退出大屏
      </a-button>
    </div>

    <div
      v-if="screenMode"
      class="screen-mask"
    >
      <div
        class="screen-canvas"
        data-test="screen-canvas"
        :style="{ transform: `scale(${screenScale})` }"
      >
        <div class="screen-canvas__title">
          {{ GROUP_OPTIONS.find((item) => item.value === groupBy)?.label }} · 环境监测大屏
        </div>
        <div class="screen-canvas__grid">
          <a-card
            v-for="group in visibleGroups"
            :key="group.key"
            :bordered="true"
            :title="group.title"
            class="view-group screen-group"
          >
            <div
              v-for="item in group.devices"
              :key="String(item.device.id)"
              class="view-device"
            >
              <div class="view-device__head">
                <span class="view-device__name">{{ item.device.name }}</span>
                <span class="view-device__addr">地址 {{ item.device.slaveAddr }}</span>
              </div>
              <div class="view-values">
                <div
                  v-for="row in item.rows"
                  :key="row.id"
                  class="view-value screen-value"
                >
                  <span class="view-value__label">{{ row.elementName ?? row.elementType ?? `${row.nodeId}-${row.analogNo}` }}</span>
                  <span
                    class="view-value__number"
                    :style="{ color: `var(--color-${qualityColor(row)}-6)` }"
                  >
                    {{ row.dataValue == null ? '-' : Number(row.dataValue).toFixed(2) }}
                  </span>
                  <span class="view-value__unit">{{ row.unit }}</span>
                </div>
              </div>
            </div>
          </a-card>
        </div>
      </div>
    </div>
  </GiPageLayout>
</template>

<style scoped lang="less">
.view-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.view-tip {
  margin-top: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}

.view-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.view-grid--screen {
  display: none;
}

.view-group__extra {
  color: var(--color-text-3);
  font-size: 12px;
}

.view-device + .view-device {
  margin-top: 10px;
}

.view-device__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.view-device__name {
  font-weight: 600;
}

.view-device__addr,
.view-device__empty {
  color: var(--color-text-3);
  font-size: 12px;
}

.view-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 6px;
}

.view-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--color-fill-1);
}

.view-value--bad {
  outline: 1px solid var(--color-danger-3);
}

.view-value__label {
  color: var(--color-text-2);
  font-size: 12px;
}

.view-value__number {
  font-size: 16px;
  font-weight: 600;
}

.view-value__unit {
  color: var(--color-text-3);
  font-size: 12px;
}

.screen-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  overflow: hidden;
  background: #0b1220;
}

.screen-canvas {
  width: 1920px;
  height: 1080px;
  padding: 24px 32px;
  transform-origin: top left;
  background: radial-gradient(circle at 20% 0%, #16233c 0%, #0b1220 60%);
}

.screen-canvas__title {
  margin-bottom: 16px;
  color: #e8f0ff;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 4px;
}

.screen-canvas__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.screen-group {
  background: rgb(255 255 255 / 92%);
}

.screen-value .view-value__number {
  font-size: 22px;
}

.screen-bar {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 1001;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  color: #e8f0ff;
  background: rgb(0 0 0 / 55%);
  font-size: 13px;
}
</style>
