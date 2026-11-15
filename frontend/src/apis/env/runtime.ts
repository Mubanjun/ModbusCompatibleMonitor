import type * as T from './type'
import http from '@/utils/http'

/** @desc 查询AlarmRecord列表（运行时数据，只读） */
export function listAlarmRecord(query?: T.AlarmRecordQuery) {
  return http.get<T.AlarmRecordResp[]>(`/monitor/alarm/record/list`, query)
}

/** @desc 查询AlarmRecord详情 */
export function getAlarmRecord(id: string) {
  return http.get<T.AlarmRecordResp>(`/monitor/alarm/record/${id}`)
}

/** @desc 登记报警处理结果（FR-ALM-03） */
export function handleAlarmRecord(data: { handlingFlag: number, handlingMethod?: string }, id: string) {
  return http.patch(`/monitor/alarm/record/${id}/handle`, data)
}

/** @desc 查询报警引擎与通知状态 */
export function getAlarmEngineStatus() {
  return http.get<Record<string, any>>(`/monitor/acquisition/alarm`)
}

/** @desc 查询AlarmNotify列表（运行时数据，只读） */
export function listAlarmNotify(query?: T.AlarmNotifyQuery) {
  return http.get<T.AlarmNotifyResp[]>(`/monitor/alarm/notify/list`, query)
}

/** @desc 查询AlarmNotify详情 */
export function getAlarmNotify(id: string) {
  return http.get<T.AlarmNotifyResp>(`/monitor/alarm/notify/${id}`)
}

/** @desc 查询SyncConflict列表（运行时数据，只读） */
export function listSyncConflict(query?: T.SyncConflictQuery) {
  return http.get<T.SyncConflictResp[]>(`/monitor/sync/conflict/list`, query)
}

/** @desc 查询SyncConflict详情 */
export function getSyncConflict(id: string) {
  return http.get<T.SyncConflictResp>(`/monitor/sync/conflict/${id}`)
}

/** @desc 查询SyncWatermark列表（运行时数据，只读） */
export function listSyncWatermark(query?: T.SyncWatermarkQuery) {
  return http.get<T.SyncWatermarkResp[]>(`/monitor/sync/watermark/list`, query)
}

/** @desc 查询SyncWatermark详情 */
export function getSyncWatermark(id: string) {
  return http.get<T.SyncWatermarkResp>(`/monitor/sync/watermark/${id}`)
}

/** @desc 查询WatchdogLog列表（运行时数据，只读） */
export function listWatchdogLog(query?: T.WatchdogLogQuery) {
  return http.get<T.WatchdogLogResp[]>(`/monitor/watchdog/log/list`, query)
}

/** @desc 查询WatchdogLog详情 */
export function getWatchdogLog(id: string) {
  return http.get<T.WatchdogLogResp>(`/monitor/watchdog/log/${id}`)
}

/** @desc 分页查询AlarmRecord列表 */
export function pageAlarmRecord(query?: T.AlarmRecordQuery) {
  return http.get<PageRes<T.AlarmRecordResp[]>>(`/monitor/alarm/record`, query)
}

/** @desc 分页查询AlarmNotify列表 */
export function pageAlarmNotify(query?: T.AlarmNotifyQuery) {
  return http.get<PageRes<T.AlarmNotifyResp[]>>(`/monitor/alarm/notify`, query)
}

/** @desc 分页查询SyncConflict列表 */
export function pageSyncConflict(query?: T.SyncConflictQuery) {
  return http.get<PageRes<T.SyncConflictResp[]>>(`/monitor/sync/conflict`, query)
}

/** @desc 分页查询SyncWatermark列表 */
export function pageSyncWatermark(query?: T.SyncWatermarkQuery) {
  return http.get<PageRes<T.SyncWatermarkResp[]>>(`/monitor/sync/watermark`, query)
}

/** @desc 分页查询WatchdogLog列表 */
export function pageWatchdogLog(query?: T.WatchdogLogQuery) {
  return http.get<PageRes<T.WatchdogLogResp[]>>(`/monitor/watchdog/log`, query)
}
