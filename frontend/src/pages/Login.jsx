import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Box, Button, Container, TextField, Typography, Alert, Paper } from '@mui/material'
import { authApi } from '../api'

function Login() {
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function handleSubmit() {
    try {
      await authApi.login(identifier, password)
      navigate('/dashboard')
    } catch (err) {
      setError('Invalid username or password')
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
            label="Username or Email"
            value={identifier}
            onChange={e => setIdentifier(e.target.value)}
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
            Login
          </Button>
          <Typography textAlign="center" mt={2} variant="body2">
            No account?{' '}
            <Box component="span" onClick={() => navigate('/register')} sx={{ color: 'primary.main', cursor: 'pointer', fontWeight: 'bold' }}>
              Register
            </Box>
          </Typography>
        </Paper>
      </Container>
    </Box>
  )
}

export default Login