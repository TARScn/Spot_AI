import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const phonePattern = /^1[3-9]\d{9}$/
const tokenKey = 'spotai_token'

function App() {
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [countdown, setCountdown] = useState(0)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const token = localStorage.getItem(tokenKey)
    if (!token) return
    fetch('/user/me', {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then((res) => res.json())
      .then((body) => {
        if (body.success) setUser(body.data)
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (countdown <= 0) return
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [countdown])

  const validatePhone = () => {
    if (!phonePattern.test(phone)) {
      setError('请输入正确的手机号')
      setMessage('')
      return false
    }
    return true
  }

  const sendCode = async () => {
    if (!validatePhone() || countdown > 0) return
    setLoading(true)
    setError('')
    try {
      const params = new URLSearchParams({ phone })
      const response = await fetch(`/user/code?${params.toString()}`, { method: 'POST' })
      const body = await response.json()
      if (!body.success) {
        setError(body.errorMsg || '发送失败')
        return
      }
      setCountdown(60)
      setMessage('验证码已发送，请查看后端日志')
    } catch (e) {
      setError('无法连接后端服务')
    } finally {
      setLoading(false)
    }
  }

  const login = async (event) => {
    event.preventDefault()
    if (!validatePhone()) return
    if (!/^\d{6}$/.test(code)) {
      setError('请输入 6 位验证码')
      setMessage('')
      return
    }

    setLoading(true)
    setError('')
    try {
      const response = await fetch('/user/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, code })
      })
      const body = await response.json()
      if (!body.success) {
        setError(body.errorMsg || '登录失败')
        return
      }
      localStorage.setItem(tokenKey, body.data)
      const meResponse = await fetch('/user/me', {
        headers: { Authorization: `Bearer ${body.data}` }
      })
      const meBody = await meResponse.json()
      if (meBody.success) setUser(meBody.data)
      setMessage('登录成功')
    } catch (e) {
      setError('无法连接后端服务')
    } finally {
      setLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem(tokenKey)
    setUser(null)
    setCode('')
    setMessage('')
  }

  return (
    <main className="shell">
      <section className="panel">
        <div className="brand">
          <span className="mark">S</span>
          <div>
            <h1>Spot AI</h1>
            <p>本地生活智能点评系统</p>
          </div>
        </div>

        {user ? (
          <div className="profile">
            <div className="avatar">{user.nickName?.slice(0, 1) || 'U'}</div>
            <div>
              <p className="label">当前登录</p>
              <h2>{user.nickName}</h2>
              <p className="muted">ID {user.id}</p>
            </div>
            <button className="secondary" onClick={logout}>退出</button>
          </div>
        ) : (
          <form className="form" onSubmit={login}>
            <label>
              手机号
              <input
                value={phone}
                onChange={(event) => setPhone(event.target.value.trim())}
                placeholder="请输入手机号"
                inputMode="tel"
                maxLength={11}
              />
            </label>

            <label>
              验证码
              <div className="code-row">
                <input
                  value={code}
                  onChange={(event) => setCode(event.target.value.trim())}
                  placeholder="6 位验证码"
                  inputMode="numeric"
                  maxLength={6}
                />
                <button type="button" className="secondary" onClick={sendCode} disabled={loading || countdown > 0}>
                  {countdown > 0 ? `${countdown}s` : '发送'}
                </button>
              </div>
            </label>

            {error && <div className="notice error">{error}</div>}
            {message && <div className="notice success">{message}</div>}

            <button className="primary" disabled={loading} type="submit">
              {loading ? '处理中...' : '登录 / 注册'}
            </button>
          </form>
        )}
      </section>
    </main>
  )
}

createRoot(document.getElementById('root')).render(<App />)
