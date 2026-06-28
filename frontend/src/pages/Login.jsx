import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'

function Login() {
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function handleSubmit() {
    try {
      await api.post('/auth/login', { identifier, password })
      navigate('/dashboard')
    } catch (err) {
      setError('Invalid username or password')
    }
  }

  return (
    <div>
      <h1>Login</h1>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      <input
        placeholder="Username or Email"
        value={identifier}
        onChange={e => setIdentifier(e.target.value)}
      />
      <br />
      <input
        placeholder="Password"
        type="password"
        value={password}
        onChange={e => setPassword(e.target.value)}
      />
      <br />
      <button onClick={handleSubmit}>Login</button>
      <p>No account? <span onClick={() => navigate('/register')} style={{ cursor: 'pointer', color: 'blue' }}>Register</span></p>
    </div>
  )
}

export default Login