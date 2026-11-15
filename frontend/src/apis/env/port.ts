import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/port'

/** @desc 查询采集端口列表 */
export function listPort(query?: T.PortQuery) {
  return http.get<T.PortResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询采集端口详情 */
export function getPort(id: string) {
  return http.get<T.PortResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增采集端口 */
export function addPort(data: T.PortReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改采集端口 */
export function updatePort(data: T.PortReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除采集端口 */
export function deletePort(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询Port列表 */
export function pagePort(query?: T.PortQuery) {
  return http.get<PageRes<T.PortResp[]>>(`${BASE_URL}`, query)
}
