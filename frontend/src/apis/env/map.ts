import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor'

export function getDeviceMapConfig() {
  return http.get<T.DeviceMapConfig>(`${BASE_URL}/device-map/config`)
}

export function updateDeviceLocation(id: string, data: T.DeviceLocationReq) {
  return http.put(`${BASE_URL}/device/${id}/location`, data)
}

export function batchUpdateDeviceLocation(data: T.DeviceLocationReq[]) {
  return http.post<{ successCount: number, errors: string[] }>(`${BASE_URL}/device/location/batch`, data)
}
