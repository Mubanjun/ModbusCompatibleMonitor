import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/config'

/** @desc 查询业务系统配置列表 */
export function listSysConfig(query?: T.SysConfigQuery) {
  return http.get<T.SysConfigResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询业务系统配置详情 */
export function getSysConfig(id: string) {
  return http.get<T.SysConfigResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增业务系统配置 */
export function addSysConfig(data: T.SysConfigReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改业务系统配置 */
export function updateSysConfig(data: T.SysConfigReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除业务系统配置 */
export function deleteSysConfig(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询SysConfig列表 */
export function pageSysConfig(query?: T.SysConfigQuery) {
  return http.get<PageRes<T.SysConfigResp[]>>(`${BASE_URL}`, query)
}
