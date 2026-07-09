import axios from 'axios'

const api = axios.create({ baseURL: 'http://localhost:8080/api', withCredentials: true })

export default api

export const authApi = {
  login: (identifier, password) => api.post('/auth/login', { identifier, password }),
  register: (username, email, password) => api.post('/user/register', { username, email, password }),
}

export const foodApi = {
  search: (include, exclude, page = 1, size = 20) =>
    api.get('/food/search', { params: { include: include.join(','), exclude: exclude.join(','), page, size } }),
  autocomplete: (q) => api.get('/ingredients/autocomplete', { params: { q } }),
}

export const logApi = {
  add: (code, quantity, unit, meal) => api.post('/log', { code, quantity, unit, meal }),
  list: (date) => api.get('/log', { params: date ? { date } : {} }),
}

export const recipeApi = {
  create: (recipe) => api.post('/recipes', recipe),
  list: () => api.get('/recipes'),
}