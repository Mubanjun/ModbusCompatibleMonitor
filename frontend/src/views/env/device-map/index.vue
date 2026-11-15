<template>
  <GiPageLayout>
    <a-card :bordered="false">
      <template #extra>
        <a-space>
          <a-tree-select v-model="selectedGroupId" :data="groupOptions" placeholder="按分组筛选" allow-clear style="width: 220px" />
          <a-tag :color="config.enabled && !fallback ? 'green' : 'orange'">
            {{ config.enabled && !fallback ? '高德地图' : '降级模式' }}
          </a-tag>
          <a-button @click="loadAll">刷新</a-button>
        </a-space>
      </template>
      <a-alert v-if="fallback" type="warning" show-icon style="margin-bottom: 12px">
        未配置高德 Key 或地图加载失败，已降级为坐标清单 + 本地静态底图；设备位置仍可正常查看。
      </a-alert>
      <div v-show="config.enabled && !fallback" ref="mapRef" class="device-map" />
      <div v-if="!config.enabled || fallback" class="fallback-map">
        <div class="fallback-grid">
          <a-card v-for="device in filteredDevices" :key="device.id" size="small" :title="device.name" hoverable @click="openDevice(device)">
            <p>{{ device.location || device.address || '未填写位置' }}</p>
            <p class="coord">{{ device.longitude }}, {{ device.latitude }}</p>
          </a-card>
          <a-empty v-if="filteredDevices.length === 0" description="暂无带坐标的设备" />
        </div>
      </div>
    </a-card>

    <a-drawer v-model:visible="drawerVisible" :width="420" :title="selectedDevice?.name">
      <a-descriptions v-if="selectedDevice" :column="1" bordered>
        <a-descriptions-item label="分组">{{ groupNameOf(selectedDevice.groupId) }}</a-descriptions-item>
        <a-descriptions-item label="位置">{{ selectedDevice.location || '暂无' }}</a-descriptions-item>
        <a-descriptions-item label="地址">{{ selectedDevice.address || '暂无' }}</a-descriptions-item>
        <a-descriptions-item label="坐标(WGS-84)">{{ selectedDevice.longitude }}, {{ selectedDevice.latitude }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ selectedDevice.enabled ? '启用' : '停用' }}</a-descriptions-item>
      </a-descriptions>
      <a-divider>最新值</a-divider>
      <a-list :data="latestValues" size="small">
        <template #item="{ item }">
          <a-list-item>
            <span>{{ item.elementType || item.nodeId }} / A{{ item.analogNo }}</span>
            <strong>{{ item.dataValue ?? item.value ?? '—' }} {{ item.unit || '' }}</strong>
          </a-list-item>
        </template>
      </a-list>
      <a-empty v-if="latestValues.length === 0" description="暂无实时值" />
    </a-drawer>
  </GiPageLayout>
</template>

<script setup lang="ts">
import AMapLoader from '@amap/amap-jsapi-loader'
import {
  type DeviceGroupNode,
  type DeviceMapConfig,
  type DeviceResp,
  getDeviceMapConfig,
  listDataLatest,
  listDevice,
  treeDeviceGroup,
} from '@/apis'
import { wgs84ToGcj02 } from '@/utils/coordinate'

defineOptions({ name: 'EnvDeviceMap' })

const mapRef = ref<HTMLDivElement>()
const map = shallowRef<any>(null)
const cluster = shallowRef<any>(null)
const config = reactive<DeviceMapConfig>({
  enabled: false,
  key: '',
  securityCode: '',
  coordinateSystem: 'WGS84',
  renderCoordinateSystem: 'GCJ02',
})
const groups = ref<DeviceGroupNode[]>([])
const devices = ref<DeviceResp[]>([])
const selectedGroupId = ref<string>()
const fallback = ref(false)
const drawerVisible = ref(false)
const selectedDevice = ref<DeviceResp>()
const latestValues = ref<any[]>([])

const flattenGroups = (nodes: DeviceGroupNode[]): DeviceGroupNode[] =>
  nodes.flatMap((node) => [node, ...flattenGroups(node.children ?? [])])
const toTreeData = (nodes: DeviceGroupNode[]): any[] =>
  nodes.map((node) => ({ key: node.id, title: node.name, children: toTreeData(node.children ?? []) }))
const groupOptions = computed(() => toTreeData(groups.value))
const groupNameOf = (id?: string) => (id ? flattenGroups(groups.value).find((item) => item.id === id)?.name ?? id : '未分组')

const filteredDevices = computed(() => {
  const all = devices.value.filter((item) => item.longitude != null && item.latitude != null)
  if (!selectedGroupId.value) return all
  const group = flattenGroups(groups.value).find((item) => item.id === selectedGroupId.value)
  if (!group) return all
  const ids = new Set(flattenGroups(groups.value).filter((item) => item.path?.startsWith(group.path)).map((item) => item.id))
  return all.filter((item) => ids.has(item.groupId))
})

const openDevice = async (device: DeviceResp) => {
  selectedDevice.value = device
  drawerVisible.value = true
  latestValues.value = []
  const res = await listDataLatest({ deviceId: device.id, page: 1, size: 5 })
  latestValues.value = res.data?.list ?? []
}

const renderMarkers = () => {
  if (!map.value || !cluster.value) return
  const AMap = (window as any).AMap
  const markers = filteredDevices.value.map((device) => {
    const color = device.enabled ? '#165DFF' : '#86909C'
    const marker = new AMap.Marker({
      position: wgs84ToGcj02(Number(device.longitude), Number(device.latitude)),
      title: device.name,
      content: `<div style="width:14px;height:14px;border:2px solid #fff;border-radius:50%;background:${color};box-shadow:0 0 4px rgba(0,0,0,.35)"></div>`,
      offset: new AMap.Pixel(-9, -9),
    })
    marker.on('click', () => openDevice(device))
    return marker
  })
  if (typeof cluster.value.setData === 'function') {
    cluster.value.setData(markers)
  }
}

const initMap = async () => {
  if (!config.enabled || !mapRef.value) {
    fallback.value = true
    return
  }
  try {
    ;(window as any)._AMapSecurityConfig = { securityJsCode: config.securityCode }
    const AMap = await AMapLoader.load({ key: config.key, version: '2.0', plugins: ['AMap.MarkerCluster'] })
    ;(window as any).AMap = AMap
    const first = filteredDevices.value[0]
    const center = first ? wgs84ToGcj02(Number(first.longitude), Number(first.latitude)) : [116.397428, 39.90923]
    map.value = new AMap.Map(mapRef.value, { zoom: 11, center, mapStyle: 'amap://styles/whitesmoke' })
    cluster.value = new AMap.MarkerCluster(map.value, [], { gridSize: 60, maxZoom: 15 })
    renderMarkers()
  } catch (error) {
    console.error('高德地图加载失败，已降级', error)
    fallback.value = true
  }
}

const loadAll = async () => {
  const [groupRes, deviceRes, mapRes] = await Promise.all([treeDeviceGroup(), listDevice(), getDeviceMapConfig()])
  groups.value = groupRes.data ?? []
  devices.value = deviceRes.data ?? []
  Object.assign(config, mapRes.data)
  fallback.value = !config.enabled
  await nextTick()
  if (config.enabled) {
    await initMap()
  }
}

watch(selectedGroupId, renderMarkers)
onMounted(loadAll)
onBeforeUnmount(() => {
  cluster.value?.setData?.([])
  map.value?.destroy?.()
})
</script>

<style scoped>
.device-map {
  width: 100%;
  height: calc(100vh - 220px);
  min-height: 520px;
  border-radius: 8px;
}
.fallback-map {
  min-height: 520px;
  border-radius: 8px;
  padding: 16px;
  background-color: #eef3f8;
  background-image: linear-gradient(#d6e0ea 1px, transparent 1px), linear-gradient(90deg, #d6e0ea 1px, transparent 1px);
  background-size: 40px 40px;
}
.fallback-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.coord {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
