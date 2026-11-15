import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

/** 自动刷新可选频率（秒，FR-WEB-07「指定刷新频率」） */
export const AUTO_REFRESH_INTERVALS = [3, 5, 10, 30, 60, 300]

/** 自动刷新记忆（按 key 存 localStorage） */
interface AutoRefreshState {
  paused?: boolean
  intervalSeconds?: number
}

/** useAutoRefresh 选项 */
export interface UseAutoRefreshOptions {
  /** 记忆键：同一页面多次进入保持上次的暂停状态与频率 */
  key: string
  /** 可选刷新频率（秒），默认 3/5/10/30/60/300 */
  intervals?: number[]
  /** 默认刷新频率（秒），默认取 intervals 第一项 */
  defaultInterval?: number
  /** 是否默认暂停 */
  defaultPaused?: boolean
}

/**
 * 手动刷新控制（FR-WEB-07）
 *
 * - 暂停后**不再发起自动请求**（定时器被清除），手动刷新与页面首次加载不受影响；
 * - 刷新频率可在页面上切换，并与暂停状态一起记忆到 localStorage；
 * - 组件卸载时自动清理定时器，避免离开页面后仍在轮询。
 *
 * @param handler 每次刷新执行的加载函数
 * @param options 选项
 * @returns 刷新控制状态与操作
 */
export function useAutoRefresh(handler: () => Promise<unknown> | unknown, options: UseAutoRefreshOptions) {
  const intervals = options.intervals ?? [...AUTO_REFRESH_INTERVALS]
  const storageKey = `jdrk:auto-refresh:${options.key}`
  const stored = readState(storageKey)

  const intervalSeconds = ref(
    intervals.includes(Number(stored?.intervalSeconds))
      ? Number(stored?.intervalSeconds)
      : (options.defaultInterval ?? intervals[0]),
  )
  const paused = ref(typeof stored?.paused === 'boolean' ? stored.paused : options.defaultPaused ?? false)
  const refreshing = ref(false)
  const lastRefreshAt = ref<number>()
  const refreshCount = ref(0)
  const lastError = ref<string>()

  let timer: ReturnType<typeof setInterval> | undefined

  const persist = () => {
    try {
      localStorage.setItem(storageKey, JSON.stringify({
        paused: paused.value,
        intervalSeconds: intervalSeconds.value,
      } satisfies AutoRefreshState))
    } catch {
      // localStorage 不可用（隐私模式等）时静默降级，不影响刷新功能
    }
  }

  /** 停止自动刷新（暂停或卸载时调用） */
  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = undefined
    }
  }

  /** 立即刷新一次（手动刷新也走这里，保证与自动刷新同一路径） */
  const refresh = async () => {
    if (refreshing.value) {
      // 上一次刷新还没回来（慢查询/网络抖动）时不叠加请求，避免定时器堆积
      return
    }
    refreshing.value = true
    try {
      await handler()
      lastRefreshAt.value = Date.now()
      refreshCount.value += 1
      lastError.value = undefined
    } catch (error) {
      // 请求失败已由 http 层统一提示，这里只记录，避免定时器里抛出未处理的 Promise 拒绝
      lastError.value = error instanceof Error ? error.message : String(error)
    } finally {
      refreshing.value = false
    }
  }

  /** 按当前暂停状态与频率启动定时器 */
  const start = () => {
    stop()
    if (paused.value) {
      return
    }
    timer = setInterval(() => {
      refresh()
    }, intervalSeconds.value * 1000)
  }

  const intervalLabel = computed(() => {
    if (intervalSeconds.value >= 60) {
      return `${intervalSeconds.value / 60} 分钟`
    }
    return `${intervalSeconds.value} 秒`
  })

  watch([paused, intervalSeconds], () => {
    persist()
    start()
  })

  onMounted(() => {
    start()
  })

  onUnmounted(stop)

  return {
    /** 是否暂停自动刷新 */
    paused,
    /** 当前刷新频率（秒） */
    intervalSeconds,
    /** 可选频率（秒） */
    intervalOptions: intervals,
    /** 是否正在刷新 */
    refreshing,
    /** 最近一次刷新完成时间（毫秒时间戳） */
    lastRefreshAt,
    /** 已自动/手动刷新次数 */
    refreshCount,
    /** 最近一次刷新失败原因（成功即清空） */
    lastError,
    /** 频率展示文案 */
    intervalLabel,
    /** 立即刷新一次 */
    refresh,
    /** 手动启动（一般不需要调用） */
    start,
    /** 停止自动刷新 */
    stop,
  }
}

/** 读取记忆（读取失败或格式不对时返回 undefined） */
function readState(storageKey: string): AutoRefreshState | undefined {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) {
      return undefined
    }
    return JSON.parse(raw) as AutoRefreshState
  } catch {
    return undefined
  }
}
