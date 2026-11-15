import type * as T from './type'
import http from '@/utils/http'

const BASE_URL = '/monitor/sensor'

export function listSensor(query?: T.SensorQuery) {
  return http.get<T.SensorResp[]>(`${BASE_URL}/list`, query)
}

export function getSensor(id: string) {
  return http.get<T.SensorResp>(`${BASE_URL}/${id}`)
}

export function addSensor(data: T.SensorReq) {
  return http.post(`${BASE_URL}`, data)
}

export function updateSensor(data: T.SensorReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

export function deleteSensor(id: string) {
  return http.del(`${BASE_URL}`, { ids: [id] })
}

export function pageSensor(query?: T.SensorQuery) {
  return http.get<PageRes<T.SensorResp[]>>(`${BASE_URL}`, query)
}
