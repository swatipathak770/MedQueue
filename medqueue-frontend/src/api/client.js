import axios from 'axios'

const apiBase = import.meta.env.VITE_API_URL === undefined ? 'http://localhost:8080' : import.meta.env.VITE_API_URL
export const api = axios.create({ baseURL: apiBase })
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('medqueue.token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
export function errorMessage(error) {
  const data = error?.response?.data
  if (typeof data?.message === 'string' && data.message.trim()) return data.message
  const status = error?.response?.status
  const statusMessages = {
    400: 'Please check the information and try again.',
    401: 'Your session has expired. Please sign in again.',
    403: 'You do not have permission to do that.',
    404: 'The requested item could not be found.',
    409: 'This request conflicts with the current state. Refresh and try again.',
    500: 'The server could not complete the request. Please try again later.',
  }
  if (statusMessages[status]) return statusMessages[status]
  if (!error?.response) return 'Cannot reach the server. Check your connection and try again.'
  return 'Something went wrong. Please try again.'
}
