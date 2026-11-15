import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/device-group'

/** 分组树 */
export function treeDeviceGroup() {
  return http.get<T.DeviceGroupNode[]>(`${BASE_URL}/tree`)
}

/** 新增分组 */
export function addDeviceGroup(data: T.DeviceGroupReq) {
  return http.post(`${BASE_URL}`, data)
}

/** 修改/移动分组 */
export function updateDeviceGroup(data: T.DeviceGroupReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** 删除分组 */
export function deleteDeviceGroup(id: string, strategy = 'MOVE_TO_PARENT') {
  return http.del(`${BASE_URL}/${id}`, { strategy })
}

/** 批量移动设备到分组 */
export function moveDevicesToGroup(groupId: string, deviceIds: string[]) {
  return http.post(`${BASE_URL}/${groupId}/devices/move`, deviceIds)
}
