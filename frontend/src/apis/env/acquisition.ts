import type * as T from './type'
import http from '@/utils/http'

/** @desc 查询采集内核运行状态 */
export function getAcquisitionStatus() {
  return http.get<T.AcquisitionStatusResp>(`/monitor/acquisition/status`)
}

/** @desc 开始采集 */
export function startAcquisition() {
  return http.post<void>(`/monitor/acquisition/start`)
}

/** @desc 停止采集 */
export function stopAcquisition() {
  return http.post<void>(`/monitor/acquisition/stop`)
}

/** @desc 重新加载采集配置 */
export function reloadAcquisition() {
  return http.post<void>(`/monitor/acquisition/reload`)
}

/** @desc 暂停采集（portId 为空表示全部暂停） */
export function pauseAcquisition(portId?: string) {
  return http.post<void>(`/monitor/acquisition/pause`, undefined, { params: { portId } })
}

/** @desc 恢复采集（portId 为空表示全部恢复） */
export function resumeAcquisition(portId?: string) {
  return http.post<void>(`/monitor/acquisition/resume`, undefined, { params: { portId } })
}

/** @desc 单设备通讯测试 */
export function testDevice(deviceId: string) {
  return http.post<T.DeviceTestResp>(`/monitor/acquisition/device/${deviceId}/test`)
}

/** @desc 查询本地暂存与落地状态 */
export function getBufferStatus() {
  return http.get<T.StagingStatusResp>(`/monitor/acquisition/buffer`)
}

/** @desc 立即执行一次暂存落地 */
export function flushBuffer() {
  return http.post<void>(`/monitor/acquisition/buffer/flush`)
}

/** @desc 增量拉取实时信令 */
export function listRecentSignal(since?: number, limit = 200) {
  return http.get<T.SignalFrameResp[]>(`/monitor/acquisition/signal/recent`, { since, limit })
}

/** @desc 查询信令游标 */
export function getSignalCursor() {
  return http.get<number>(`/monitor/acquisition/signal/cursor`)
}

/** @desc 查询信令记录列表 */
export function listSignalLog(query?: T.SignalLogQuery) {
  return http.get<T.SignalLogResp[]>(`/monitor/signal/log/list`, query)
}

/** @desc 查询信令记录详情 */
export function getSignalLog(id: string) {
  return http.get<T.SignalLogResp>(`/monitor/signal/log/${id}`)
}

/** @desc 分页查询SignalLog列表 */
export function pageSignalLog(query?: T.SignalLogQuery) {
  return http.get<PageRes<T.SignalLogResp[]>>(`/monitor/signal/log`, query)
}
