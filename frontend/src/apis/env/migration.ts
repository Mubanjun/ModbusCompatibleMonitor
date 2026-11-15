import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/migration'

/** @desc 检测原库（第 1 步） */
export function detectSource(data: T.MigrationReq) {
  return http.post<T.MigrationDetectResp>(`${BASE_URL}/detect`, data)
}

/** @desc 映射设备（第 2 步） */
export function importDevices(data: T.MigrationReq) {
  return http.post<T.MigrationImportResp>(`${BASE_URL}/import-devices`, data)
}

/** @desc 导入历史数据（第 3 步） */
export function importHistory(data: T.MigrationReq) {
  return http.post<T.MigrationImportResp>(`${BASE_URL}/import-history`, data)
}

/** @desc 一致性校验（第 4 步） */
export function verifyMigration(data: T.MigrationReq) {
  return http.post<T.MigrationVerifyResp>(`${BASE_URL}/verify`, data)
}

/** @desc 最近一次一致性报告 */
export function getLastVerify() {
  return http.get<T.MigrationVerifyResp>(`${BASE_URL}/verify/last`)
}

/** @desc 迁移状态 */
export function getMigrationStatus() {
  return http.get<Record<string, any>>(`${BASE_URL}/status`)
}

/** @desc 切换运行模式（OFF / COEXIST / SWITCHED） */
export function setMigrationMode(mode: string) {
  return http.put<string>(`${BASE_URL}/mode`, { mode })
}

/** @desc 回退指引 */
export function getRollbackGuide() {
  return http.get<string[]>(`${BASE_URL}/rollback-guide`)
}
