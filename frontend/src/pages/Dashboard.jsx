import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { logApi } from '../api'

function Dashboard() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
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
    fetchLog()
  }, [])

  const totalCalories = logs.reduce((sum, entry) => sum + (entry.kcal ?? 0), 0)

  if (loading) return <p>Loading...</p>

  return (
    <div>
      <h1>Today's Log</h1>
      <h2>Total Calories: {totalCalories.toFixed(0)} kcal</h2>
      <button onClick={() => navigate('/food-search')}>Add Food</button>
      {logs.length === 0 ? (
        <p>No food logged today.</p>
      ) : (
        <ul>
          {logs.map(entry => (
            <li key={entry.id}>
              <strong>{entry.name}</strong> — {entry.quantity}{entry.unit} ({entry.meal})
              <span> {entry.kcal != null ? `${entry.kcal.toFixed(0)} kcal` : 'no calorie data'}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default Dashboard