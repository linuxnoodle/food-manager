import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box, Button, Container, Typography, TextField, Card, CardContent,
  List, ListItem, ListItemText, Divider, Select, MenuItem,
  FormControl, InputLabel, Alert, AppBar, Toolbar, IconButton
} from '@mui/material'
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
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" fontWeight="bold" sx={{ flexGrow: 1 }}>
            CalorieTracker
          </Typography>
          <Button color="inherit" onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </Button>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ mt: 4, pb: 4 }}>
        <Typography variant="h5" fontWeight="bold" color="primary" mb={3}>
          Create Recipe
        </Typography>

        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {success && <Alert severity="success" sx={{ mb: 2 }}>{success}</Alert>}

        <Card elevation={2} sx={{ borderRadius: 3, mb: 3 }}>
          <CardContent>
            <Typography variant="subtitle1" fontWeight="bold" mb={2}>Recipe Details</Typography>
            <TextField fullWidth label="Recipe Name" value={name} onChange={e => setName(e.target.value)} sx={{ mb: 2 }} />
            <TextField fullWidth label="Description (optional)" value={description} onChange={e => setDescription(e.target.value)} sx={{ mb: 2 }} />
            <TextField fullWidth type="number" label="Servings" value={servings} onChange={e => setServings(e.target.value)} inputProps={{ min: 1 }} sx={{ mb: 2 }} />
            <TextField fullWidth multiline rows={3} label="Instructions (optional)" value={instructions} onChange={e => setInstructions(e.target.value)} />
          </CardContent>
        </Card>

        <Typography variant="subtitle1" fontWeight="bold" color="primary" mb={2}>
          Ingredients
        </Typography>

        {ingredients.length > 0 && (
          <Card elevation={2} sx={{ borderRadius: 3, mb: 3 }}>
            <List disablePadding>
              {ingredients.map((ing, index) => (
                <Box key={ing.code}>
                  <ListItem>
                    <ListItemText primary={ing.name} />
                    <Box display="flex" gap={1} alignItems="center">
                      <TextField
                        type="number"
                        size="small"
                        label="Qty"
                        sx={{ width: 80 }}
                        value={ing.quantity}
                        onChange={e => handleIngredientChange(ing.code, 'quantity', e.target.value)}
                      />
                      <FormControl size="small" sx={{ width: 80 }}>
                        <InputLabel>Unit</InputLabel>
                        <Select
                          value={ing.unit}
                          label="Unit"
                          onChange={e => handleIngredientChange(ing.code, 'unit', e.target.value)}
                        >
                          <MenuItem value="g">g</MenuItem>
                          <MenuItem value="ml">ml</MenuItem>
                        </Select>
                      </FormControl>
                      <Button size="small" color="error" onClick={() => handleRemoveIngredient(ing.code)}>
                        Remove
                      </Button>
                    </Box>
                  </ListItem>
                  {index < ingredients.length - 1 && <Divider />}
                </Box>
              ))}
            </List>
          </Card>
        )}

        <Typography variant="subtitle1" fontWeight="bold" mb={1}>
          Search Ingredients
        </Typography>
        <TextField
          fullWidth
          label="Search ingredients..."
          value={query}
          onChange={handleQueryChange}
          sx={{ mb: 1 }}
        />

        {suggestions.length > 0 && (
          <Card elevation={2} sx={{ mb: 2, borderRadius: 2 }}>
            <List disablePadding>
              {suggestions.map((s, index) => (
                <Box key={s.tag}>
                  <ListItem
                    onClick={() => handleSelectTag(s.tag, s.name ?? s.tag)}
                    sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
                  >
                    <ListItemText primary={s.name ?? s.tag} />
                  </ListItem>
                  {index < suggestions.length - 1 && <Divider />}
                </Box>
              ))}
            </List>
          </Card>
        )}

        {searchResults.length > 0 && (
          <Card elevation={2} sx={{ borderRadius: 2, mb: 3 }}>
            <List disablePadding>
              {searchResults.map((food, index) => (
                <Box key={food.code}>
                  <ListItem>
                    <ListItemText
                      primary={food.name}
                      secondary={food.brand}
                    />
                    <Button variant="outlined" size="small" onClick={() => handleAddIngredient(food)}>
                      Add
                    </Button>
                  </ListItem>
                  {index < searchResults.length - 1 && <Divider />}
                </Box>
              ))}
            </List>
          </Card>
        )}

        <Button fullWidth variant="contained" size="large" onClick={handleSubmit}>
          Create Recipe
        </Button>
      </Container>
    </Box>
  )
}

export default RecipeCreation