import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/channel'

/** @desc 查询通道配置列表 */
export function listChannel(query?: T.ChannelQuery) {
  return http.get<T.ChannelResp[]>(`${BASE_URL}/list`, query)
}

/** @desc 查询通道配置详情 */
export function getChannel(id: string) {
  return http.get<T.ChannelResp>(`${BASE_URL}/${id}`)
}

/** @desc 新增通道配置 */
export function addChannel(data: T.ChannelReq) {
  return http.post(`${BASE_URL}`, data)
}

/** @desc 修改通道配置 */
export function updateChannel(data: T.ChannelReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除通道配置 */
export function deleteChannel(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

/** @desc 分页查询Channel列表 */
export function pageChannel(query?: T.ChannelQuery) {
  return http.get<PageRes<T.ChannelResp[]>>(`${BASE_URL}`, query)
}
