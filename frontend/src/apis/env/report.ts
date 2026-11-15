import type * as T from './type'
import http from '@/utils/http'

const TARGET_URL = '/monitor/report/target'
const TEMPLATE_URL = '/monitor/report/template'

/** @desc 查询上报目标列表 */
export function listReportTarget(query?: T.ReportTargetQuery) {
  return http.get<T.ReportTargetResp[]>(`${TARGET_URL}/list`, query)
}

/** @desc 查询上报目标详情 */
export function getReportTarget(id: string) {
  return http.get<T.ReportTargetResp>(`${TARGET_URL}/${id}`)
}

/** @desc 新增上报目标 */
export function addReportTarget(data: T.ReportTargetReq) {
  return http.post(`${TARGET_URL}`, data)
}

/** @desc 修改上报目标 */
export function updateReportTarget(data: T.ReportTargetReq, id: string) {
  return http.put(`${TARGET_URL}/${id}`, data)
}

/** @desc 删除上报目标 */
export function deleteReportTarget(id: string) {
  return http.del(`${TARGET_URL}`, { ids: [id] })
}

/** @desc 查询上报模板列表 */
export function listReportTemplate(query?: T.ReportTemplateQuery) {
  return http.get<T.ReportTemplateResp[]>(`${TEMPLATE_URL}/list`, query)
}

/** @desc 查询上报模板详情 */
export function getReportTemplate(id: string) {
  return http.get<T.ReportTemplateResp>(`${TEMPLATE_URL}/${id}`)
}

/** @desc 新增上报模板 */
export function addReportTemplate(data: T.ReportTemplateReq) {
  return http.post(`${TEMPLATE_URL}`, data)
}

/** @desc 修改上报模板 */
export function updateReportTemplate(data: T.ReportTemplateReq, id: string) {
  return http.put(`${TEMPLATE_URL}/${id}`, data)
}

/** @desc 删除上报模板 */
export function deleteReportTemplate(id: string) {
  return http.del(`${TEMPLATE_URL}`, { ids: [id] })
}
const RULE_URL = '/monitor/report/rule'

/** @desc 查询上报规则列表 */
export function listReportRule(query?: T.ReportRuleQuery) {
  return http.get<T.ReportRuleResp[]>(`${RULE_URL}/list`, query)
}

/** @desc 查询上报规则详情 */
export function getReportRule(id: string) {
  return http.get<T.ReportRuleResp>(`${RULE_URL}/${id}`)
}

/** @desc 新增上报规则 */
export function addReportRule(data: T.ReportRuleReq) {
  return http.post(`${RULE_URL}`, data)
}

/** @desc 修改上报规则 */
export function updateReportRule(data: T.ReportRuleReq, id: string) {
  return http.put(`${RULE_URL}/${id}`, data)
}

/** @desc 删除上报规则 */
export function deleteReportRule(id: string) {
  return http.del(`${RULE_URL}`, { ids: [id] })
}

/** @desc 分页查询ReportTarget列表 */
export function pageReportTarget(query?: T.ReportTargetQuery) {
  return http.get<PageRes<T.ReportTargetResp[]>>(`${TARGET_URL}`, query)
}

/** @desc 分页查询ReportTemplate列表 */
export function pageReportTemplate(query?: T.ReportTemplateQuery) {
  return http.get<PageRes<T.ReportTemplateResp[]>>(`${TEMPLATE_URL}`, query)
}

/** @desc 分页查询ReportRule列表 */
export function pageReportRule(query?: T.ReportRuleQuery) {
  return http.get<PageRes<T.ReportRuleResp[]>>(`${RULE_URL}`, query)
}

const DEAD_LETTER_URL = '/monitor/report/dead-letter'

/** @desc 分页查询上报死信（FR-RPT-05） */
export function pageReportDeadLetter(query?: T.ReportDeadLetterQuery) {
  return http.get<PageRes<T.ReportDeadLetterResp[]>>(`${DEAD_LETTER_URL}`, query)
}

/** @desc 查询上报死信详情 */
export function getReportDeadLetter(id: string) {
  return http.get<T.ReportDeadLetterResp>(`${DEAD_LETTER_URL}/${id}`)
}

/** @desc 人工重放上报死信（FR-RPT-05） */
export function replayReportDeadLetter(ids: string[]) {
  return http.post<string>(`${DEAD_LETTER_URL}/replay`, { ids })
}
