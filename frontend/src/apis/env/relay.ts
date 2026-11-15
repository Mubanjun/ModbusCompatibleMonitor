import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/relay'

/** @desc 查询继电器状态 */
export function getRelayStatus(deviceId: string) {
  return http.get<T.RelayStatusResp>(`${BASE_URL}/${deviceId}/status`)
}

/** @desc 单路继电器控制 */
export function controlRelay(deviceId: string, data: { relayNo: number, closed: boolean, durationMs?: number }) {
  return http.post<T.RelayControlResp>(`${BASE_URL}/${deviceId}/control`, data)
}

/** @desc 批量继电器控制 */
export function batchRelay(deviceId: string, values: number[]) {
  return http.post<T.RelayControlResp>(`${BASE_URL}/${deviceId}/batch`, { values })
}

/** @desc 查询继电器写入开关 */
export function getRelayWriteEnabled() {
  return http.get<boolean>(`${BASE_URL}/write-enabled`)
}

/** @desc 分页查询继电器操作日志 */
export function pageRelayLog(query?: T.RelayLogQuery) {
  return http.get<PageRes<T.RelayLogResp[]>>(`/monitor/relay/log`, query)
}
