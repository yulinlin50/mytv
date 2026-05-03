const TOKEN_KEY = 'http_server_token'
const TOKEN_TIME_KEY = 'http_server_token_time'
const TOKEN_EXPIRE_MS = 3600000

export function getAccessToken(): string | null {
  const hash = window.location.hash
  if (hash && hash.startsWith('#token=')) {
    const token = hash.substring(7)
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(TOKEN_TIME_KEY, Date.now().toString())
      window.location.hash = ''
      return token
    }
  }

  const savedToken = localStorage.getItem(TOKEN_KEY)
  const savedTime = parseInt(localStorage.getItem(TOKEN_TIME_KEY) || '0')

  if (savedToken && Date.now() - savedTime < TOKEN_EXPIRE_MS) {
    return savedToken
  }

  clearToken()
  return null
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_TIME_KEY)
}
