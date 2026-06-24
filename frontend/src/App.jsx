import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import FoodSearch from './pages/FoodSearch'
import RecipeCreation from './pages/RecipeCreation'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/food-search" element={<FoodSearch />} />
        <Route path="/recipe-creation" element={<RecipeCreation />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App