import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearToken, getToken, getTokenName } from '../utils/token'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers[getTokenName()] = token
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      clearToken()
      if (router.currentRoute.value.name !== 'login') {
        await router.replace({ name: 'login' })
      }
    } else {
      ElMessage.error(error.response?.data?.message || '请求失败，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default http

