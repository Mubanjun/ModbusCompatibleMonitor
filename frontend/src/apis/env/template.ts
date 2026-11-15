import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/template'

/** @desc 查询设备模板列表 */
export function listTemplate(query?: T.DeviceTemplateQuery) {
  return http.get<T.DeviceTemplateResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询设备模板详情 */
export function getTemplate(id: string) {
  return http.get<T.DeviceTemplateResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增设备模板 */
export function addTemplate(data: T.DeviceTemplateReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改设备模板 */
export function updateTemplate(data: T.DeviceTemplateReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除设备模板 */
export function deleteTemplate(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询Template列表 */
export function pageTemplate(query?: T.DeviceTemplateQuery) {
  return http.get<PageRes<T.DeviceTemplateResp[]>>(`${BASE_URL}`, query)
}
