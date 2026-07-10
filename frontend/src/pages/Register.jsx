import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Box, Button, Container, TextField, Typography, Alert, Paper } from '@mui/material'
import { authApi } from '../api'

function Register() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function handleSubmit() {
    try {
      await authApi.register(username, email, password)
      navigate('/')
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed')
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', display: 'flex', alignItems: 'center' }}>
      <Container maxWidth="xs">
        <Paper elevation={3} sx={{ p: 4, borderRadius: 3 }}>
          <Typography variant="h4" color="primary" fontWeight="bold" textAlign="center" mb={3}>
            CalorieTracker
          </Typography>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <TextField
            fullWidth
            label="Username"
            value={username}
            onChange={e => setUsername(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            label="Email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            label="Password"
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            sx={{ mb: 3 }}
          />
          <Button fullWidth variant="contained" size="large" onClick={handleSubmit}>
            Register
          </Button>
          <Typography textAlign="center" mt={2} variant="body2">
            Already have an account?{' '}
            <Box component="span" onClick={() => navigate('/')} sx={{ color: 'primary.main', cursor: 'pointer', fontWeight: 'bold' }}>
              Login
            </Box>
          </Typography>
        </Paper>
      </Container>
    </Box>
  )
}

export default Register