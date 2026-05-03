import { ref } from 'vue'
import { requestApi, ApiError } from '@/utils/api'
import { useAuth } from './useAuth'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'

export function useApi() {
  const loading = ref(false)
  const { logout } = useAuth()

  async function withLoading<T>(fn: () => Promise<T>): Promise<T | null> {
    loading.value = true
    showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
    try {
      return await fn()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        showFailToast('授权已过期，请重新扫描二维码')
        logout()
      }
      throw e
    } finally {
      loading.value = false
      closeToast()
    }
  }

  async function withToast<T>(
    fn: () => Promise<T>,
    successMsg: string,
    errorMsg: string
  ): Promise<T | null> {
    try {
      const result = await withLoading(fn)
      showSuccessToast(successMsg)
      return result
    } catch (e) {
      showFailToast(errorMsg)
      console.error(e)
      return null
    }
  }

  return {
    loading,
    withLoading,
    withToast,
    requestApi,
  }
}
