import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box, Button, Container, Typography, Card, CardContent,
  List, ListItem, ListItemText, Divider, Chip, CircularProgress, AppBar, Toolbar
} from '@mui/material'
import { logApi } from '../api'

function Dashboard() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  async function fetchLog() {
    const today = new Date().toISOString().split('T')[0]
    try {
      const res = await logApi.list(today)
      setLogs(res.data)
    } catch (err) {
      console.error('failed to fetch log', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchLog() }, [])

  async function handleDelete(id) {
    try {
      await logApi.delete(id)
      setLogs(prev => prev.filter(e => e.id !== id))
    } catch (err) {
      console.error('failed to delete entry', err)
    }
  }

  const totalCalories = logs.reduce((sum, entry) => sum + (entry.kcal ?? 0), 0)

  const mealColor = {
    breakfast: 'success',
    lunch: 'primary',
    dinner: 'warning',
    snack: 'default'
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" fontWeight="bold" sx={{ flexGrow: 1 }}>
            CalorieTracker
          </Typography>
          <Button color="inherit" onClick={() => navigate('/food-search')}>
            Add Food
          </Button>
          <Button color="inherit" onClick={() => navigate('/recipe-creation')}>
            Recipes
          </Button>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ mt: 4 }}>
        <Card elevation={3} sx={{ mb: 3, borderRadius: 3, bgcolor: 'primary.main' }}>
          <CardContent sx={{ textAlign: 'center' }}>
            <Typography variant="h6" color="white">
              Today's Calories
            </Typography>
            <Typography variant="h2" color="white" fontWeight="bold">
              {totalCalories.toFixed(0)}
            </Typography>
            <Typography variant="body2" color="white" sx={{ opacity: 0.8 }}>
              kcal
            </Typography>
          </CardContent>
        </Card>

        <Typography variant="h6" fontWeight="bold" color="primary" mb={2}>
          Today's Log
        </Typography>

        {loading ? (
          <Box display="flex" justifyContent="center" mt={4}>
            <CircularProgress color="primary" />
          </Box>
        ) : logs.length === 0 ? (
          <Card elevation={1} sx={{ borderRadius: 3 }}>
            <CardContent sx={{ textAlign: 'center', py: 4 }}>
              <Typography color="text.secondary">No food logged today.</Typography>
              <Button variant="contained" sx={{ mt: 2 }} onClick={() => navigate('/food-search')}>
                Add your first meal
              </Button>
            </CardContent>
          </Card>
        ) : (
          <Card elevation={2} sx={{ borderRadius: 3 }}>
            <List disablePadding>
              {logs.map((entry, index) => (
                <Box key={entry.id}>
                  <ListItem sx={{ py: 2 }}>
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography fontWeight="bold">{entry.name}</Typography>
                          <Chip
                            label={entry.meal}
                            size="small"
                            color={mealColor[entry.meal] ?? 'default'}
                          />
                        </Box>
                      }
                      secondary={`${entry.quantity}${entry.unit}`}
                    />
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography fontWeight="bold" color="primary">
                        {entry.kcal != null ? `${entry.kcal.toFixed(0)} kcal` : '—'}
                      </Typography>
                      <Button
                        size="small"
                        color="error"
                        onClick={() => handleDelete(entry.id)}
                      >
                        Remove
                      </Button>
                    </Box>
                  </ListItem>
                  {index < logs.length - 1 && <Divider />}
                </Box>
              ))}
            </List>
          </Card>
        )}
      </Container>
    </Box>
  )
}

export default Dashboard