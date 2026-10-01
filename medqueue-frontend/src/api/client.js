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
  return data?.message || data?.error || error?.message || 'Something went wrong. Please try again.'
}
