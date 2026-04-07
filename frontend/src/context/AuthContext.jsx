import { createContext, useContext, useState, useEffect } from 'react'
import { getOwnProfile } from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState({
    token: null,
    role: null,
    userId: null,
    firstName: null,
    lastName: null,
    profilePhoto: null,
  })
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('auth_token')
    const role = localStorage.getItem('auth_role')
    const userId = localStorage.getItem('auth_userId')
    if (token) {
      setAuth(prev => ({ ...prev, token, role, userId }))
      getOwnProfile().then(data => {
        setAuth(prev => ({
          ...prev,
          firstName: data.firstName,
          lastName: data.lastName,
          profilePhoto: data.profilePhoto,
        }))
      }).catch(() => {}).finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [])

  function login(token, role, userId) {
    localStorage.setItem('auth_token', token)
    localStorage.setItem('auth_role', role)
    localStorage.setItem('auth_userId', String(userId))
    setAuth(prev => ({ ...prev, token, role, userId: String(userId) }))
    getOwnProfile().then(data => {
      setAuth(prev => ({
        ...prev,
        firstName: data.firstName,
        lastName: data.lastName,
        profilePhoto: data.profilePhoto,
      }))
    }).catch(() => {})
  }

  function logout() {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_role')
    localStorage.removeItem('auth_userId')
    setAuth({ token: null, role: null, userId: null, firstName: null, lastName: null, profilePhoto: null })
  }

  function setProfileData(firstName, lastName, profilePhoto) {
    setAuth(prev => ({ ...prev, firstName, lastName, profilePhoto }))
  }

  return (
    <AuthContext.Provider value={{ ...auth, isLoading, login, logout, setProfileData }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
