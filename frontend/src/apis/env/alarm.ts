import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/alarm'

/** @desc 查询报警规则列表 */
export function listAlarmRule(query?: T.AlarmRuleQuery) {
  return http.get<T.AlarmRuleResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询报警规则详情 */
export function getAlarmRule(id: string) {
  return http.get<T.AlarmRuleResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增报警规则 */
export function addAlarmRule(data: T.AlarmRuleReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改报警规则 */
export function updateAlarmRule(data: T.AlarmRuleReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除报警规则 */
export function deleteAlarmRule(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询AlarmRule列表 */
export function pageAlarmRule(query?: T.AlarmRuleQuery) {
  return http.get<PageRes<T.AlarmRuleResp[]>>(`${BASE_URL}`, query)
}
