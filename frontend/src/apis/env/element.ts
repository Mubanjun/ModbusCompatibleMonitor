import http from '@/utils/http'

/** @desc 查询要素量程字典 */
export function listElementCatalog() {
  return http.get<any[]>('/jdrk/elements')
}
