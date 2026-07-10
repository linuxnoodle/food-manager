import axios from 'axios'

const api = axios.create({ baseURL: 'http://localhost:8080/api', withCredentials: true })

export default api

export const authApi = {
  login: (identifier, password) => api.post('/auth/login', { identifier, password }),
  register: (username, email, password) => api.post('/user/register', { username, email, password }),
}

export const foodApi = {
  search: (include, exclude, macros = {}, page = 1, size = 20) =>
    api.get('/food/search', { params: { include: include.join(','), exclude: exclude.join(','), ...macros, page, size } }),
  localSearch: (include, exclude, macros = {}, page = 1, size = 20) =>
    api.get('/food/local-search', { params: { include: include.join(','), exclude: exclude.join(','), ...macros, page, size } }),
  autocomplete: (q) => api.get('/ingredients/autocomplete', { params: { q } }),
  detail: (code) => api.get(`/food/${code}`),
}

export const logApi = {
  add: (e) => api.post('/log', e),
  list: (date) => api.get('/log', { params: date ? { date } : {} }),
  delete: (id) => api.delete(`/log/${id}`),
}

export const recipeApi = {
  create: (r) => api.post('/recipes', r),
  list: () => api.get('/recipes'),
  delete: (id) => api.delete(`/recipes/${id}`),
}