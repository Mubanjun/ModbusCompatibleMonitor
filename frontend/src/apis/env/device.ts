import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/device'

/** @desc 查询采集设备列表 */
export function listDevice(query?: T.DeviceQuery) {
  return http.get<T.DeviceResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询采集设备详情 */
export function getDevice(id: string) {
  return http.get<T.DeviceResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增采集设备 */
export function addDevice(data: T.DeviceReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改采集设备 */
export function updateDevice(data: T.DeviceReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除采集设备 */
export function deleteDevice(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询Device列表 */
export function pageDevice(query?: T.DeviceQuery) {
  return http.get<PageRes<T.DeviceResp[]>>(`${BASE_URL}`, query)
}
