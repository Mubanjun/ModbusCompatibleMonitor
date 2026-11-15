<template>
  <div class="refresh-control">
    <a-switch
      :model-value="paused"
      :disabled="disabled"
      @change="onPausedChange"
    >
      <template #checked>自动刷新已暂停</template>
      <template #unchecked>自动刷新中</template>
    </a-switch>
    <a-select
      :model-value="intervalSeconds"
      :style="{ width: '118px' }"
      :disabled="disabled"
      @change="onIntervalChange"
    >
      <a-option
        v-for="item in intervalOptions"
        :key="item"
        :value="item"
      >
        {{ intervalText(item) }}
      </a-option>
    </a-select>
    <a-button
      :loading="refreshing"
      :disabled="disabled"
      @click="emit('refresh')"
    >
      <template #icon><icon-refresh /></template>
      刷新
    </a-button>
    <span class="refresh-control__tip">{{ tip }}</span>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RefreshControl' })

const props = defineProps<{
  /** 是否暂停自动刷新 */
  paused?: boolean
  /** 刷新频率（秒） */
  intervalSeconds?: number
  /** 可选频率（秒） */
  intervalOptions?: number[]
  /** 是否正在刷新 */
  refreshing?: boolean
  /** 最近一次刷新完成时间（毫秒时间戳） */
  lastRefreshAt?: number
  /** 是否禁用整个控件 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:paused', value: boolean): void
  (e: 'update:intervalSeconds', value: number): void
  (e: 'refresh'): void
}>()

const intervalText = (value: number) => (value >= 60 ? `${value / 60} 分钟` : `${value} 秒`)

const onPausedChange = (value: string | number | boolean) => {
  emit('update:paused', Boolean(value))
}

const onIntervalChange = (value: string | number | boolean) => {
  emit('update:intervalSeconds', Number(value))
}

/**
 * 暂停时明确提示「不再请求」，便于现场确认手动刷新控制是否生效（FR-WEB-07）。
 */
const tip = computed(() => {
  if (props.paused) {
    return '已暂停自动刷新，点击「刷新」可手动获取一次'
  }
  if (!props.lastRefreshAt) {
    return '加载中…'
  }
  return `最近刷新 ${new Date(props.lastRefreshAt).toLocaleTimeString('zh-CN', { hour12: false })}`
})
</script>

<style scoped lang="less">
.refresh-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-control__tip {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
