import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/time-sync'

/** @desc 查询校时状态与设备时间偏差 */
export function getTimeSyncStatus() {
  return http.get<T.TimeSyncStatusResp>(`${BASE_URL}/status`)
}

/** @desc 单设备一键校时 */
export function syncDeviceTime(deviceId: string) {
  return http.post<T.TimeSyncResultResp>(`${BASE_URL}/device/${deviceId}`)
}

/** @desc 批量一键校时（设备列表为空表示全部启用设备；mode=ALL 全部下发，TOLERANT 仅偏差超差下发） */
export function syncAllDeviceTime(deviceIds?: string[], mode?: 'ALL' | 'TOLERANT') {
  return http.post<T.TimeSyncResultResp[]>(`${BASE_URL}/batch`, { deviceIds, mode })
}
