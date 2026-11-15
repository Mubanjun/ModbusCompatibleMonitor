import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/data'
const LATEST_URL = '/monitor/data/latest'

/** @desc 分页查询历史数据 */
export function pageData(query?: T.DataQuery) {
  return http.get<PageRes<T.DataResp[]>>(`${BASE_URL}`, query)
}

/** @desc 查询历史数据详情 */
export function getData(id: string) {
  return http.get<T.DataResp>(`${BASE_URL}/${id}`)
}

/** @desc 按时间桶聚合历史数据 */
export function aggregateData(query?: T.DataQuery) {
  return http.get<T.DataAggregateResp[]>(`${BASE_URL}/aggregate`, query)
}

/** @desc 导出历史数据 */
export function exportData(query?: T.DataQuery) {
  return http.download(`${BASE_URL}/export`, query)
}

/** @desc 单点修正历史数据（FR-WEB-08） */
export function correctData(data: {
  id?: string
  deviceId?: string
  nodeId?: number
  analogNo?: number
  recordTime?: string
  dataValue?: number
  rawValue?: number
  reason?: string
}) {
  return http.post<T.DataCorrectResp>(`${BASE_URL}/correct`, data)
}

/** @desc 批量补算历史数据（FR-WEB-08） */
export function recalculateData(data: {
  deviceId?: string
  nodeId?: number
  analogNo?: number
  startTime: string
  endTime: string
  dryRun?: boolean
}) {
  return http.post<T.DataRecalculateResp>(`${BASE_URL}/recalculate`, data)
}

/** @desc 分页查询通道实时值 */
export function pageDataLatest(query?: T.DataLatestQuery) {
  return http.get<PageRes<T.DataLatestResp[]>>(`${LATEST_URL}`, query)
}

/** @desc 查询通道实时值详情 */
export function getDataLatest(id: string) {
  return http.get<T.DataLatestResp>(`${LATEST_URL}/${id}`)
}

/** @desc 不分页查询全部通道实时值（多视图大屏取数，FR-WEB-04） */
export function listDataLatest(query?: T.DataLatestQuery) {
  return http.get<T.DataLatestResp[]>(`${LATEST_URL}/list`, query)
}

/** @desc 按时间窗口升序读取实时曲线点（FR-WEB-02） */
export function curveData(query?: T.DataQuery & { limit?: number }) {
  return http.get<T.DataResp[]>(`${BASE_URL}/curve`, query)
}
