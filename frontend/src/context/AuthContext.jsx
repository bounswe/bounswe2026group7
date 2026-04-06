import { createContext, useContext, useState, useEffect } from 'react'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState({
    token: null,
    role: null,
    userId: null,
  })
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('auth_token')
    const role = localStorage.getItem('auth_role')
    const userId = localStorage.getItem('auth_userId')
    if (token) {
      setAuth({ token, role, userId })
    }
    setIsLoading(false)
  }, [])

  function login(token, role, userId) {
    localStorage.setItem('auth_token', token)
    localStorage.setItem('auth_role', role)
    localStorage.setItem('auth_userId', String(userId))
    setAuth({ token, role, userId: String(userId) })
  }

  function logout() {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_role')
    localStorage.removeItem('auth_userId')
    setAuth({ token: null, role: null, userId: null })
  }

  return (
    <AuthContext.Provider value={{ ...auth, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
