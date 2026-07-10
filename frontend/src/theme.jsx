import { createTheme } from '@mui/material'

const theme = createTheme({
  palette: {
    primary: {
      main: '#2e7d32',
      light: '#4caf50',
      dark: '#1b5e20',
    },
    background: {
      default: '#f1f8e9',
    }
  },
  shape: {
    borderRadius: 8,
  }
})

export default theme