import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/collect/order'

/** @desc 查询采集顺序列表 */
export function listCollectOrder(query?: T.CollectOrderQuery) {
  return http.get<T.CollectOrderResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询采集顺序详情 */
export function getCollectOrder(id: string) {
  return http.get<T.CollectOrderResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增采集顺序 */
export function addCollectOrder(data: T.CollectOrderReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改采集顺序 */
export function updateCollectOrder(data: T.CollectOrderReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除采集顺序 */
export function deleteCollectOrder(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询CollectOrder列表 */
export function pageCollectOrder(query?: T.CollectOrderQuery) {
  return http.get<PageRes<T.CollectOrderResp[]>>(`${BASE_URL}`, query)
}
