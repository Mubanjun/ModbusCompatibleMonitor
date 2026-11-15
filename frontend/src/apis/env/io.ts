import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/io'

/** @desc 导入前校验（表头/行数/样例） */
export function previewImport(type: string, file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<T.ImportPreviewResp>(`${BASE_URL}/import/preview?type=${type}`, data)
}

/** @desc 执行导入（dryRun=true 仅校验） */
export function executeImport(type: string, file: File, dryRun = false) {
  const data = new FormData()
  data.append('file', file)
  return http.post<T.ImportReportResp>(`${BASE_URL}/import/execute?type=${type}&dryRun=${dryRun}`, data)
}

/** @desc 流式导出历史数据 CSV */
export function exportDataCsv(query: {
  deviceId?: string
  nodeId?: number
  analogNo?: number
  startTime?: string
  endTime?: string
}) {
  return http.download(`${BASE_URL}/export/data`, query)
}

/** @desc 导出单表 SQL */
export function exportTableSql(table: string) {
  return http.download(`${BASE_URL}/export/table`, { table })
}

/** @desc 业务表清单 */
export function listIoTables() {
  return http.get<string[]>(`${BASE_URL}/tables`)
}

/** @desc 备份包列表 */
export function listBackups() {
  return http.get<T.BackupFileResp[]>(`${BASE_URL}/backup`)
}

/** @desc 一键打包备份 */
export function createBackup(data: { password?: string, includeLogs?: boolean }) {
  return http.post<T.BackupFileResp>(`${BASE_URL}/backup`, data)
}

/** @desc 下载备份包 */
export function downloadBackup(name: string) {
  return http.download(`${BASE_URL}/backup/download?name=${encodeURIComponent(name)}`)
}

/** @desc 校验备份包 */
export function verifyBackup(data: { name: string, password?: string }) {
  return http.post<T.BackupVerifyResp>(`${BASE_URL}/backup/verify`, data)
}

/** @desc 从备份包恢复 */
export function restoreBackup(data: { name: string, password?: string, override?: boolean }) {
  return http.post<T.BackupRestoreResp>(`${BASE_URL}/backup/restore`, data)
}

/** @desc 清理过期备份 */
export function purgeBackups() {
  return http.post<number>(`${BASE_URL}/backup/purge`)
}

/** @desc 备份运行状态 */
export function getBackupStatus() {
  return http.get<Record<string, any>>(`${BASE_URL}/backup/status`)
}
