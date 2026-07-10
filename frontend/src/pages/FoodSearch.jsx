import { useState } from 'react'
import { foodApi, logApi } from '../api'
import { useNavigate } from 'react-router-dom'

function FoodSearch() {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [results, setResults] = useState([])
  const [logForm, setLogForm] = useState({})
  const [successMessage, setSuccessMessage] = useState('')
  const [selectedTag, setSelectedTag] = useState(null)
  const navigate = useNavigate()

  async function handleQueryChange(e) {
    const val = e.target.value
    setQuery(val)
    setSelectedTag(null)
    if (val.length < 2) {
      setSuggestions([])
      return
    }
    try {
      const res = await foodApi.autocomplete(val)
      setSuggestions(res.data)
    } catch (err) {
      console.error('autocomplete failed', err)
    }
  }

  async function handleSelectTag(tag, name) {
    setSelectedTag(tag)
    setQuery(name)
    setSuggestions([])
    try {
      const res = await foodApi.search([tag], [], 1, 20)
      console.log(res.data.items)
      setResults(res.data.items)
    } catch (err) {
      console.error('search failed', err)
    }
  }

  function handleFormChange(code, field, value) {
    setLogForm(prev => ({
      ...prev,
      [code]: { ...prev[code], [field]: value }
    }))
  }

  async function handleAddToLog(food) {
    const form = logForm[food.code] || {}
    const quantity = parseFloat(form.quantity)
    const unit = form.unit || 'g'
    const meal = form.meal || 'lunch'

    if (!quantity || quantity <= 0) {
      alert('Please enter a valid quantity')
      return
    }

    try {
      await logApi.add(food.code, quantity, unit, meal)
      setSuccessMessage(`${food.name} added to log!`)
      setTimeout(() => setSuccessMessage(''), 3000)
    } catch (err) {
      console.error('log failed', err)
      alert('Failed to add to log')
    }
  }

  return (
    <div>
      <button onClick={() => navigate('/dashboard')}>← Back to Dashboard</button>
      <h1>Food Search</h1>
      {successMessage && <p style={{ color: 'green' }}>{successMessage}</p>}
      <input
        placeholder="Search ingredients..."
        value={query}
        onChange={handleQueryChange}
      />
      {suggestions.length > 0 && (
        <ul>
          {suggestions.map((s) => (
            <li
              key={s.tag}
              onClick={() => handleSelectTag(s.tag, s.name ?? s.tag)}
              style={{ cursor: 'pointer' }}
            >
              {s.name ?? s.tag}
            </li>
          ))}
        </ul>
      )}
      {results.length > 0 && (
        <ul>
          {results.map((food) => (
            <li key={food.code}>
              <strong>{food.name}</strong> {food.brand && `— ${food.brand}`}
              <div>
                <input
                  type="number"
                  placeholder="Quantity"
                  value={logForm[food.code]?.quantity || ''}
                  onChange={e => handleFormChange(food.code, 'quantity', e.target.value)}
                />
                <select
                  value={logForm[food.code]?.unit || 'g'}
                  onChange={e => handleFormChange(food.code, 'unit', e.target.value)}
                >
                  <option value="g">g</option>
                  <option value="ml">ml</option>
                </select>
                <select
                  value={logForm[food.code]?.meal || 'lunch'}
                  onChange={e => handleFormChange(food.code, 'meal', e.target.value)}
                >
                  <option value="breakfast">Breakfast</option>
                  <option value="lunch">Lunch</option>
                  <option value="dinner">Dinner</option>
                  <option value="snack">Snack</option>
                </select>
                <button onClick={() => handleAddToLog(food)}>Add to Log</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default FoodSearch