import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/sync/task'

/** @desc 查询同步任务列表 */
export function listSyncTask(query?: T.SyncTaskQuery) {
  return http.get<T.SyncTaskResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询同步任务详情 */
export function getSyncTask(id: string) {
  return http.get<T.SyncTaskResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增同步任务 */
export function addSyncTask(data: T.SyncTaskReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改同步任务 */
export function updateSyncTask(data: T.SyncTaskReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除同步任务 */
export function deleteSyncTask(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询SyncTask列表 */
export function pageSyncTask(query?: T.SyncTaskQuery) {
  return http.get<PageRes<T.SyncTaskResp[]>>(`${BASE_URL}`, query)
}

/** @desc 一键同步（全量从头、增量从水位） */
export function startSyncTask(id: string) {
  return http.post<string>(`${BASE_URL}/${id}/start`)
}

/** @desc 暂停同步 */
export function pauseSyncTask(id: string) {
  return http.post<boolean>(`${BASE_URL}/${id}/pause`)
}

/** @desc 续传同步 */
export function resumeSyncTask(id: string) {
  return http.post<string>(`${BASE_URL}/${id}/resume`)
}

/** @desc 查询同步进度 */
export function getSyncProgress(id: string) {
  return http.get<T.SyncProgressResp>(`${BASE_URL}/${id}/progress`)
}
