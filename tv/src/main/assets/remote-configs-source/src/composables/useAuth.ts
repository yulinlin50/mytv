import { ref, computed } from 'vue'
import { getAccessToken, clearToken } from '@/utils/auth'

const token = ref<string | null>(getAccessToken())

export function useAuth() {
  const isAuthenticated = computed(() => token.value !== null)

  function setToken(newToken: string | null) {
    token.value = newToken
    if (newToken === null) {
      clearToken()
    }
  }

  function logout() {
    setToken(null)
  }

  return {
    token,
    isAuthenticated,
    setToken,
    logout,
  }
}
