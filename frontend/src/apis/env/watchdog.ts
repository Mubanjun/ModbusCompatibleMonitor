import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/watchdog'

/** @desc 看门狗状态（心跳新鲜度、重启记录、最近自检） */
export function getWatchdogStatus() {
  return http.get<T.WatchdogStatusResp>(`${BASE_URL}/status`)
}

/** @desc 执行自检 */
export function runSelfCheck() {
  return http.post<T.SelfCheckItemResp[]>(`${BASE_URL}/self-check`)
}

/** @desc 把看门狗进程写的事件落库 */
export function ingestWatchdogEvents() {
  return http.post<number>(`${BASE_URL}/ingest`)
}
