import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { foodApi, recipeApi } from '../api'

function RecipeCreation() {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [servings, setServings] = useState(1)
  const [instructions, setInstructions] = useState('')
  const [ingredients, setIngredients] = useState([])
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [searchResults, setSearchResults] = useState([])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const navigate = useNavigate()

  async function handleQueryChange(e) {
    const val = e.target.value
    setQuery(val)
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
    setQuery(name)
    setSuggestions([])
    try {
      const res = await foodApi.search([tag], [], 1, 20)
      setSearchResults(res.data.items)
    } catch (err) {
      console.error('search failed', err)
    }
  }

  function handleAddIngredient(food) {
    if (ingredients.find(i => i.code === food.code)) return
    setIngredients(prev => [...prev, {
      code: food.code,
      name: food.name,
      quantity: '',
      unit: 'g'
    }])
    setSearchResults([])
    setQuery('')
  }

  function handleIngredientChange(code, field, value) {
    setIngredients(prev => prev.map(i =>
      i.code === code ? { ...i, [field]: value } : i
    ))
  }

  function handleRemoveIngredient(code) {
    setIngredients(prev => prev.filter(i => i.code !== code))
  }

  async function handleSubmit() {
    if (!name.trim()) return setError('Recipe name is required')
    if (ingredients.length === 0) return setError('Add at least one ingredient')
    for (const i of ingredients) {
      if (!i.quantity || parseFloat(i.quantity) <= 0) {
        return setError(`Enter a valid quantity for ${i.name}`)
      }
    }

    try {
      await recipeApi.create({
        name,
        description,
        servings: parseInt(servings),
        instructions,
        ingredients: ingredients.map(i => ({
          code: i.code,
          quantity: parseFloat(i.quantity),
          unit: i.unit
        }))
      })
      setSuccess('Recipe created!')
      setTimeout(() => navigate('/dashboard'), 1500)
    } catch (err) {
      setError('Failed to create recipe')
    }
  }

  return (
    <div>
      <h1>Create Recipe</h1>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      {success && <p style={{ color: 'green' }}>{success}</p>}

      <div>
        <input placeholder="Recipe name" value={name} onChange={e => setName(e.target.value)} />
      </div>
      <div>
        <input placeholder="Description (optional)" value={description} onChange={e => setDescription(e.target.value)} />
      </div>
      <div>
        <input type="number" placeholder="Servings" value={servings} min={1} onChange={e => setServings(e.target.value)} />
      </div>
      <div>
        <textarea placeholder="Instructions (optional)" value={instructions} onChange={e => setInstructions(e.target.value)} />
      </div>

      <h2>Ingredients</h2>
      {ingredients.length > 0 && (
        <ul>
          {ingredients.map(i => (
            <li key={i.code}>
              <strong>{i.name}</strong>
              <input
                type="number"
                placeholder="Quantity"
                value={i.quantity}
                onChange={e => handleIngredientChange(i.code, 'quantity', e.target.value)}
              />
              <select value={i.unit} onChange={e => handleIngredientChange(i.code, 'unit', e.target.value)}>
                <option value="g">g</option>
                <option value="ml">ml</option>
              </select>
              <button onClick={() => handleRemoveIngredient(i.code)}>Remove</button>
            </li>
          ))}
        </ul>
      )}

      <h3>Search for ingredients</h3>
      <input
        placeholder="Search ingredients..."
        value={query}
        onChange={handleQueryChange}
      />
      {suggestions.length > 0 && (
        <ul>
          {suggestions.map(s => (
            <li key={s.tag} onClick={() => handleSelectTag(s.tag, s.name ?? s.tag)} style={{ cursor: 'pointer' }}>
              {s.name ?? s.tag}
            </li>
          ))}
        </ul>
      )}
      {searchResults.length > 0 && (
        <ul>
          {searchResults.map(food => (
            <li key={food.code}>
              <strong>{food.name}</strong> {food.brand && `— ${food.brand}`}
              <button onClick={() => handleAddIngredient(food)}>Add</button>
            </li>
          ))}
        </ul>
      )}

      <br />
      <button onClick={handleSubmit}>Create Recipe</button>
    </div>
  )
}

export default RecipeCreation