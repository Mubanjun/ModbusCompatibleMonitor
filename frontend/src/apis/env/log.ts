import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/log'

/** @desc 查询受管日志器级别 */
export function listLogLevel() {
  return http.get<T.LogLevelResp[]>(`${BASE_URL}/level`)
}

/** @desc 在线切换日志级别 */
export function setLogLevel(data: { logger: string, level: string }) {
  return http.put<T.LogLevelResp>(`${BASE_URL}/level`, data)
}

/** @desc 日志文件列表 */
export function listLogFile() {
  return http.get<T.LogFileResp[]>(`${BASE_URL}/file`)
}

/** @desc 下载日志文件 */
export function downloadLogFile(path: string) {
  return http.download(`${BASE_URL}/file/download?path=${encodeURIComponent(path)}`)
}

/** @desc 导出诊断包 */
export function exportDiagnosticPackage() {
  return http.download('/monitor/diagnostic/package')
}
