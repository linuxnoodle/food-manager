import { useState } from 'react'
import api from '../api'

function FoodSearch() {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [results, setResults] = useState([])
  const [selectedTag, setSelectedTag] = useState(null)

  async function handleQueryChange(e) {
    const val = e.target.value
    setQuery(val)
    if (val.length < 2) {
      setSuggestions([])
      return
    }
    try {
      const res = await api.get(`/ingredients/autocomplete?q=${val}`)
      setSuggestions(res.data)
    } catch (err) {
      console.error('autocomplete failed', err)
    }
  }

  async function handleSelectTag(tag) {
    setSelectedTag(tag)
    setQuery(tag)
    setSuggestions([])
    try {
      const res = await api.get(`/food/search?include=${tag}&page=1&size=20`)
      setResults(res.data.items)
    } catch (err) {
      console.error('search failed', err)
    }
  }

  return (
    <div>
      <h1>Food Search</h1>
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
              onClick={() => handleSelectTag(s.tag)}
              style={{ cursor: 'pointer' }}
            >
              {s.label ?? s.tag.replace(/^[a-z]{2}:/, '').replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}
            </li>
          ))}
        </ul>
      )}
      {results.length > 0 && (
        <ul>
          {results.map((food) => (
            <li key={food.code}>
              <strong>{food.name}</strong> {food.brand && `— ${food.brand}`}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default FoodSearch