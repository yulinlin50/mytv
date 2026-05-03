import { getAccessToken, clearToken } from './auth'

const BASE_URL = ''

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function requestApi(
  url: string,
  config: RequestInit = {}
): Promise<Response> {
  const headers: Record<string, string> = {
    ...(config.headers as Record<string, string>),
  }

  if (
    config.body &&
    typeof config.body === 'string' &&
    !headers['Content-Type']
  ) {
    headers['Content-Type'] = 'application/json'
  }

  const token = getAccessToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const resp = await fetch(`${BASE_URL}${url}`, { ...config, headers })

  if (resp.status === 401) {
    clearToken()
    throw new ApiError('Unauthorized: Token expired', 401)
  }

  if (resp.status !== 200) {
    throw new ApiError(`请求失败：${resp.status}`, resp.status)
  }

  return resp
}

export async function getJson<T>(url: string): Promise<T> {
  const resp = await requestApi(url)
  return resp.json()
}

export async function postJson<T>(url: string, data: unknown): Promise<T> {
  const resp = await requestApi(url, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
  })
  return resp.json()
}

export async function getText(url: string): Promise<string> {
  const resp = await requestApi(url)
  return resp.text()
}

export async function postText(url: string, data: string): Promise<void> {
  await requestApi(url, {
    method: 'POST',
    body: data,
  })
}
