import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box, Button, Container, Typography, TextField, Card, CardContent,
  List, ListItem, ListItemText, Divider, Select, MenuItem,
  FormControl, InputLabel, Alert, AppBar, Toolbar
} from '@mui/material'
import { foodApi, logApi } from '../api'

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
      const res = await foodApi.localSearch([tag], [], {}, 1, 20)
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

      <Container maxWidth="sm" sx={{ mt: 4 }}>
        <Typography variant="h5" fontWeight="bold" color="primary" mb={3}>
          Food Search
        </Typography>

        {successMessage && <Alert severity="success" sx={{ mb: 2 }}>{successMessage}</Alert>}

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

        {results.length > 0 && (
          <List disablePadding>
            {results.map((food, index) => (
              <Card key={food.code} elevation={2} sx={{ mb: 2, borderRadius: 2 }}>
                <CardContent>
                  <Typography fontWeight="bold">{food.name}</Typography>
                  {food.brand && (
                    <Typography variant="body2" color="text.secondary" mb={2}>
                      {food.brand}
                    </Typography>
                  )}
                  <Box display="flex" gap={1} alignItems="center" flexWrap="wrap">
                    <TextField
                      label="Quantity"
                      type="number"
                      size="small"
                      sx={{ width: 100 }}
                      value={logForm[food.code]?.quantity || ''}
                      onChange={e => handleFormChange(food.code, 'quantity', e.target.value)}
                    />
                    <FormControl size="small" sx={{ width: 80 }}>
                      <InputLabel>Unit</InputLabel>
                      <Select
                        value={logForm[food.code]?.unit || 'g'}
                        label="Unit"
                        onChange={e => handleFormChange(food.code, 'unit', e.target.value)}
                      >
                        <MenuItem value="g">g</MenuItem>
                        <MenuItem value="ml">ml</MenuItem>
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ width: 120 }}>
                      <InputLabel>Meal</InputLabel>
                      <Select
                        value={logForm[food.code]?.meal || 'lunch'}
                        label="Meal"
                        onChange={e => handleFormChange(food.code, 'meal', e.target.value)}
                      >
                        <MenuItem value="breakfast">Breakfast</MenuItem>
                        <MenuItem value="lunch">Lunch</MenuItem>
                        <MenuItem value="dinner">Dinner</MenuItem>
                        <MenuItem value="snack">Snack</MenuItem>
                      </Select>
                    </FormControl>
                    <Button variant="contained" size="small" onClick={() => handleAddToLog(food)}>
                      Add to Log
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            ))}
          </List>
        )}
      </Container>
    </Box>
  )
}

export default FoodSearch