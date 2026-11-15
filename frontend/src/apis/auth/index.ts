import type * as T from './type'
import http from '@/utils/http'

export type * from './type'

const BASE_URL = '/auth'

const login = (req: T.AccountLoginReq, tenantCode?: string) => {
  const headers = {}
  if (tenantCode) {
    headers['X-Tenant-Code'] = tenantCode
  }
  return http.post<T.LoginResp>(`${BASE_URL}/login`, req, {
    headers,
  })
}

/** @desc 账号登录（本平台仅开放账号 + 密码登录） */
export function accountLogin(req: T.AccountLoginReq, tenantCode?: string) {
  return login(req, tenantCode)
}

/** @desc 退出登录 */
export function logout() {
  return http.post(`${BASE_URL}/logout`)
}

/** @desc 签发一次性登录票据（FR-QT-09，需已登录；60 秒有效、仅可使用一次） */
export const createOneTimeTicket = () => {
  return http.post<string>(`${BASE_URL}/one-time-ticket`)
}

/** @desc 用一次性票据免密登录（FR-QT-09） */
export const ticketLogin = (ticket: string) => {
  return http.post<T.LoginResp>(`${BASE_URL}/one-time-login?ticket=${encodeURIComponent(ticket)}`)
}

/** @desc 获取用户信息 */
export const getUserInfo = () => {
  return http.get<T.UserInfo>(`${BASE_URL}/user/info`)
}

/** @desc 获取路由信息 */
export const getUserRoute = () => {
  return http.get<T.RouteItem[]>(`${BASE_URL}/user/route`)
}
