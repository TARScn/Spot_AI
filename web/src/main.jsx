import React, { useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { marked } from 'marked'
import './styles.css'

const tokenKey = 'spotai_token'
const visitorKey = 'spotai_visitor'
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const userLocation = {
  address: '西安电子科技大学北校区',
  x: '108.916860',
  y: '34.229210'
}

function ensureVisitor() {
  let visitor = localStorage.getItem(visitorKey)
  if (!visitor) {
    visitor = `visitor:${crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36)}`
    localStorage.setItem(visitorKey, visitor)
  }
  return visitor
}

async function api(path, options = {}) {
  const token = localStorage.getItem(tokenKey)
  const headers = { ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(path, { ...options, headers })
  let body
  try {
    body = await response.json()
  } catch {
    body = { success: false, errorMsg: `HTTP ${response.status}` }
  }
  if (!response.ok && !body.errorMsg) {
    body.errorMsg = `HTTP ${response.status}`
  }
  return body
}

function money(value) {
  if (value === null || value === undefined || value === '') return '-'
  return `¥${Number(value)}`
}

function yuan(value) {
  if (value === null || value === undefined || value === '') return '-'
  return `¥${(Number(value) / 100).toFixed(0)}`
}

function scoreText(score) {
  if (!score && score !== 0) return '暂无评分'
  return `${(Number(score) / 10).toFixed(1)} 分`
}

function distanceText(distance) {
  if (!distance && distance !== 0) return '距离未知'
  const value = Number(distance)
  return value >= 1 ? `${value.toFixed(1)}km` : `${Math.round(value * 1000)}m`
}

function hoursText(hours) {
  if (!hours) return '待补充'
  return String(hours).trim().replace(/\s+(?=\d{1,2}:\d{2})/g, '\n')
}

function dateText(value) {
  if (!value) return ''
  return String(value).slice(0, 10)
}

function timeText(value) {
  if (!value) return '长期有效'
  return String(value).replace('T', ' ').slice(0, 16)
}

function stars(score) {
  const value = Math.max(0, Math.min(5, Number(score || 0)))
  return '★★★★★'.slice(0, value) + '☆☆☆☆☆'.slice(0, 5 - value)
}

function richTextToPlainText(value) {
  if (!value) return ''
  return String(value)
    .replace(/<\s*br\s*\/?\s*>/gi, '\n')
    .replace(/<\/\s*(p|div|section|article|li|h[1-6])\s*>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function normalizeImage(images) {
  if (!images) return ''
  return String(images).split(',')[0].trim()
}

function imageList(images) {
  if (!images) return []
  return String(images).split(',').map((item) => item.trim()).filter(Boolean)
}

function uniqueShops(items) {
  const seen = new Set()
  return (items || []).filter((shop) => {
    const key = shop.name
      ? `${shop.name}|${shop.area || ''}|${shop.address || ''}`.replace(/\s+/g, '')
      : shop.id
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function App() {
  const [activeTab, setActiveTab] = useState('home')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [globalSearch, setGlobalSearch] = useState('')
  const [countdown, setCountdown] = useState(0)
  const [user, setUser] = useState(null)
  const [toast, setToast] = useState('')
  const [busy, setBusy] = useState(false)
  const [categories, setCategories] = useState([])
  const [typeId, setTypeId] = useState(1)
  const [shops, setShops] = useState([])
  const [shopLoading, setShopLoading] = useState(false)
  const [shopSearchTitle, setShopSearchTitle] = useState('')
  const [selectedShop, setSelectedShop] = useState(null)
  const [shopReviews, setShopReviews] = useState([])
  const [reviewPage, setReviewPage] = useState(1)
  const [reviewHasMore, setReviewHasMore] = useState(false)
  const [reviewLoading, setReviewLoading] = useState(false)
  const [reviewSummary, setReviewSummary] = useState(null)
  const [reviewSummaryLoading, setReviewSummaryLoading] = useState(false)
  const [shopDetailLoading, setShopDetailLoading] = useState(false)
  const [blogs, setBlogs] = useState([])
  const [feed, setFeed] = useState([])
  const [blogLoading, setBlogLoading] = useState(false)
  const [blogPage, setBlogPage] = useState(1)
  const [blogHasMore, setBlogHasMore] = useState(false)
  const [selectedBlog, setSelectedBlog] = useState(null)
  const [blogDetailLoading, setBlogDetailLoading] = useState(false)
  const [composeOpen, setComposeOpen] = useState(false)
  const [composeShopKeyword, setComposeShopKeyword] = useState('')
  const [composeShopResults, setComposeShopResults] = useState([])
  const [selectedComposeShop, setSelectedComposeShop] = useState(null)
  const [imageUploading, setImageUploading] = useState(false)
  const [signDays, setSignDays] = useState(null)
  const [siteUv, setSiteUv] = useState(null)
  const [shopUv, setShopUv] = useState(null)
  const [profileData, setProfileData] = useState(null)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profilePanel, setProfilePanel] = useState('notes')
  const [blogForm, setBlogForm] = useState({
    shopId: '1',
    title: '',
    images: '',
    content: ''
  })
  const [voucherActivities, setVoucherActivities] = useState([])
  const [voucherLoading, setVoucherLoading] = useState(false)
  const [voucherBusyId, setVoucherBusyId] = useState(null)
  const [aiChatOpen, setAiChatOpen] = useState(false)
  const [aiChatInput, setAiChatInput] = useState('')
  const [aiChatMessages, setAiChatMessages] = useState([
    {
      role: 'assistant',
      content: '你好，我是 Spot AI 助手。可以帮你理解店铺、评价和优惠信息。',
      generatedAt: new Date().toISOString()
    }
  ])
  const [aiChatLoading, setAiChatLoading] = useState(false)

  const loggedIn = Boolean(user)

  useEffect(() => {
    ensureVisitor()
    hydrateUser()
    loadCategories()
    loadShops(1)
    loadBlogs()
    loadVoucherActivities()
    recordUv('site')
  }, [])

  useEffect(() => {
    if (countdown <= 0) return
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [countdown])

  useEffect(() => {
    loadShops(typeId)
  }, [typeId])

  async function hydrateUser() {
    const token = localStorage.getItem(tokenKey)
    if (!token) return
    const body = await api('/user/me')
    if (body.success) {
      setUser(body.data)
      loadSignCount()
      loadProfile()
      loadBlogs(1)
      loadFeed()
    } else {
      localStorage.removeItem(tokenKey)
    }
  }

  async function loadCategories() {
    const body = await api('/shop-type/list')
    if (body.success && Array.isArray(body.data) && body.data.length > 0) {
      setCategories(body.data.map((item) => ({
        ...item,
        tone: item.name?.slice(0, 1) || '类'
      })))
      return
    }
    setCategories([])
    if (!body.success) setToast(body.errorMsg || '商户分类加载失败')
  }

  async function sendCode() {
    if (!emailPattern.test(email)) {
      setToast('请输入正确的邮箱地址')
      return
    }
    if (countdown > 0) return
    setBusy(true)
    const body = await api(`/user/code?${new URLSearchParams({ email })}`, { method: 'POST' })
    setBusy(false)
    if (!body.success) {
      setToast(body.errorMsg || '验证码发送失败')
      return
    }
    setCountdown(60)
    setToast('验证码已发送，请查看邮箱')
  }

  async function login(event) {
    event.preventDefault()
    if (!emailPattern.test(email)) {
      setToast('请输入正确的邮箱地址')
      return
    }
    if (!/^\d{6}$/.test(code)) {
      setToast('请输入 6 位验证码')
      return
    }
    setBusy(true)
    const body = await api('/user/login', {
      method: 'POST',
      body: JSON.stringify({ email, code })
    })
    setBusy(false)
    if (!body.success) {
      setToast(body.errorMsg || '登录失败')
      return
    }
    localStorage.setItem(tokenKey, body.data)
    setToast('登录成功')
    await hydrateUser()
    loadBlogs(1)
    loadProfile()
  }

  function logout() {
    localStorage.removeItem(tokenKey)
    setUser(null)
    setSignDays(null)
    setFeed([])
    setProfileData(null)
    setToast('已退出登录')
  }

  async function loadShops(nextTypeId = typeId) {
    setShopLoading(true)
    setShopSearchTitle('')
    const params = new URLSearchParams({
      typeId: String(nextTypeId),
      current: '1',
      x: userLocation.x,
      y: userLocation.y
    })
    const body = await api(`/shop/of/type?${params}`)
    setShopLoading(false)
    if (body.success && Array.isArray(body.data)) {
      setShops(body.data)
      setSelectedShop(body.data[0] || null)
      return
    }
    setShops([])
    setSelectedShop(null)
    setToast(body.errorMsg || '商户接口暂不可用')
  }

  async function searchShopByKeyword(keyword, { forCompose = false } = {}) {
    const value = keyword.trim()
    if (!value) {
      if (forCompose) setComposeShopResults([])
      return []
    }
    const body = await api(`/shop/search?${new URLSearchParams({ keyword: value })}`)
    if (!body.success) {
      setToast(body.errorMsg || '商户搜索失败')
      if (forCompose) setComposeShopResults([])
      return []
    }
    const result = uniqueShops(Array.isArray(body.data) ? body.data : [])
    if (forCompose) setComposeShopResults(result)
    return result
  }

  async function handleGlobalSearch(event) {
    event.preventDefault()
    const value = globalSearch.trim()
    if (!value) {
      setToast('请输入要搜索的商户名称或地址')
      return
    }
    setShopLoading(true)
    const result = await searchShopByKeyword(value)
    setShopLoading(false)
    setShops(result)
    setSelectedShop(result[0] || null)
    setShopSearchTitle(`搜索“${value}”`)
    setActiveTab('shops')
    setToast(result.length > 0 ? `找到 ${result.length} 家相关商户` : '没有找到相关商户')
  }

  async function loadShopDetail(shopOrId) {
    const summary = typeof shopOrId === 'object' ? shopOrId : null
    const id = summary?.id || shopOrId
    setShopDetailLoading(true)
    setReviewSummary(null)
    if (summary) {
      setSelectedShop(summary)
    }
    setActiveTab('shopDetail')
    const body = await api(`/shop/${id}`)
    if (body.success) {
      setSelectedShop({
        ...body.data,
        distance: summary?.distance ?? body.data.distance
      })
      recordUv('shop', id)
    } else {
      setToast(body.errorMsg || '商户详情加载失败')
    }
    await Promise.all([
      loadShopReviews(id, 1, false),
      loadReviewSummary(id)
    ])
    setShopDetailLoading(false)
  }

  async function loadReviewSummary(shopId) {
    setReviewSummaryLoading(true)
    const body = await api(`/review/summary?${new URLSearchParams({ shopId: String(shopId) })}`)
    setReviewSummaryLoading(false)
    if (body.success && body.data) {
      setReviewSummary(body.data)
      return
    }
    setReviewSummary({
      status: 'UNAVAILABLE',
      message: body.errorMsg || 'AI 总结暂不可用'
    })
  }

  async function sendAiChatMessage(event) {
    event?.preventDefault()
    const message = aiChatInput.trim()
    if (!message || aiChatLoading) return

    const userMessage = {
      role: 'user',
      content: message,
      generatedAt: new Date().toISOString()
    }
    const nextMessages = [...aiChatMessages, userMessage]
    setAiChatMessages(nextMessages)
    setAiChatInput('')
    setAiChatLoading(true)

    const history = aiChatMessages
      .slice(-8)
      .map(({ role, content }) => ({ role, content }))

    const body = await api('/ai/chat', {
      method: 'POST',
      body: JSON.stringify({
        route: 'CHAT',
        shopId: selectedShop?.id || null,
        message,
        history
      })
    })

    setAiChatLoading(false)
    if (body.success && body.data?.answer) {
      setAiChatMessages((items) => [
        ...items,
        {
          role: 'assistant',
          content: body.data.answer,
          generatedAt: body.data.generatedAt,
          agentRoute: body.data.agentRoute,
          memoryUpdated: Boolean(body.data.memoryUpdated),
          memories: Array.isArray(body.data.memories) ? body.data.memories : []
        }
      ])
      return
    }
    setAiChatMessages((items) => [
      ...items,
      {
        role: 'assistant',
        content: body.errorMsg || 'AI 助手暂不可用，请稍后再试。',
        generatedAt: new Date().toISOString(),
        error: true
      }
    ])
  }

  async function loadShopReviews(shopId, page = 1, append = false) {
    setReviewLoading(true)
    const body = await api(`/review/of/shop?${new URLSearchParams({ id: String(shopId), current: String(page) })}`)
    setReviewLoading(false)
    const pageData = body.data
    if (body.success && Array.isArray(pageData?.list)) {
      setShopReviews((items) => append ? [...items, ...pageData.list] : pageData.list)
      setReviewPage(pageData.current || page)
      setReviewHasMore(Boolean(pageData.hasMore))
      return
    }
    if (!append) setShopReviews([])
    setReviewHasMore(false)
    if (!body.success) setToast(body.errorMsg || '店铺评价加载失败')
  }

  async function warmGeo() {
    const body = await api('/shop/geo/load', { method: 'PUT' })
    setToast(body.success ? '附近商户坐标已重新加载' : body.errorMsg || 'GEO 加载失败')
  }

  async function loadBlogs(page = 1, append = false) {
    setBlogLoading(true)
    const body = await api(`/blog/hot?current=${page}`)
    setBlogLoading(false)
    if (body.success && Array.isArray(body.data)) {
      setBlogs((items) => append ? [...items, ...body.data] : body.data)
      setBlogPage(page)
      setBlogHasMore(body.data.length >= 10)
      return
    }
    if (!append) setBlogs([])
    setBlogHasMore(false)
    if (!body.success) setToast(body.errorMsg || '探店笔记加载失败')
  }

  async function loadFeed() {
    if (!localStorage.getItem(tokenKey)) {
      setToast('登录后才能查看关注流')
      setActiveTab('login')
      return
    }
    const body = await api(`/blog/of/follow?${new URLSearchParams({ lastId: String(Date.now()), offset: '0' })}`)
    if (body.success) {
      setFeed(body.data?.list || [])
    } else {
      setToast(body.errorMsg || '关注流加载失败')
    }
  }

  async function loadProfile() {
    if (!localStorage.getItem(tokenKey)) return
    setProfileLoading(true)
    const body = await api('/user/profile')
    setProfileLoading(false)
    if (body.success) {
      setProfileData(body.data)
      if (body.data?.signDays !== undefined) setSignDays(body.data.signDays)
    } else if (body.errorMsg) {
      setToast(body.errorMsg)
    }
  }

  async function openBlogDetail(blog) {
    setSelectedBlog(blog)
    setBlogDetailLoading(true)
    const body = await api(`/blog/${blog.id}`)
    setBlogDetailLoading(false)
    if (body.success) {
      setSelectedBlog(body.data)
      recordUv('blog', blog.id)
    } else {
      setToast(body.errorMsg || '探店笔记详情加载失败')
    }
  }

  function openComposeBlog() {
    if (!loggedIn) {
      setToast('登录后才能发布探店笔记')
      setActiveTab('login')
      return
    }
    setActiveTab('composeBlog')
  }

  async function searchComposeShop(event) {
    event.preventDefault()
    await searchShopByKeyword(composeShopKeyword, { forCompose: true })
  }

  function selectComposeShop(shop) {
    setSelectedComposeShop(shop)
    setComposeShopKeyword(shop.name)
    setBlogForm((form) => ({ ...form, shopId: String(shop.id) }))
  }

  async function uploadBlogImages(files) {
    const imageFiles = Array.from(files || []).filter((file) => file.type.startsWith('image/'))
    if (imageFiles.length === 0) {
      setToast('请选择图片文件')
      return
    }
    if (!loggedIn) {
      setToast('登录后才能上传图片')
      setActiveTab('login')
      return
    }
    setImageUploading(true)
    const uploaded = []
    try {
      for (const file of imageFiles.slice(0, 9)) {
        const formData = new FormData()
        formData.append('file', file)
        const body = await api('/upload/blog', { method: 'POST', body: formData })
        if (body.success && body.data?.url) {
          uploaded.push(body.data.url)
        } else {
          setToast(body.errorMsg || `${file.name} 上传失败，请确认 MinIO 和后端服务正常`)
          break
        }
      }
      if (uploaded.length > 0) {
        setBlogForm((form) => {
          const existing = form.images ? form.images.split(',').map((item) => item.trim()).filter(Boolean) : []
          return { ...form, images: [...existing, ...uploaded].join(',') }
        })
        setToast(`已上传 ${uploaded.length} 张图片`)
      }
    } catch {
      setToast('图片上传失败，请确认后端和 MinIO 已启动')
    } finally {
      setImageUploading(false)
    }
  }

  function removeBlogImage(url) {
    setBlogForm((form) => ({
      ...form,
      images: form.images
        .split(',')
        .map((item) => item.trim())
        .filter((item) => item && item !== url)
        .join(',')
    }))
  }

  async function publishBlog(event) {
    event?.preventDefault()
    if (!loggedIn) {
      setToast('登录后才能发布探店笔记')
      setActiveTab('login')
      return
    }
    const payload = {
      shopId: Number(blogForm.shopId),
      title: blogForm.title.trim(),
      images: blogForm.images.trim(),
      content: blogForm.content.trim()
    }
    if (!payload.shopId || !payload.title || !payload.content) {
      setToast('请选择店铺，并填写标题和正文')
      return
    }
    const body = await api('/blog', { method: 'POST', body: JSON.stringify(payload) })
    if (body.success) {
      setToast(`发布成功，笔记 ID：${body.data}`)
      setBlogForm({ shopId: '1', title: '', images: '', content: '' })
      setSelectedComposeShop(null)
      setComposeShopKeyword('')
      setComposeShopResults([])
      setComposeOpen(false)
      setActiveTab('blogs')
      loadBlogs(1)
    } else {
      setToast(body.errorMsg || '发布失败')
    }
  }

  async function likeBlog(id) {
    if (!loggedIn) {
      setToast('登录后才能点赞')
      setActiveTab('login')
      return
    }
    const body = await api(`/blog/like/${id}`, { method: 'PUT' })
    if (body.success) {
      const updateLiked = (item) => (
        item.id === id
          ? { ...item, isLike: !item.isLike, liked: Number(item.liked || 0) + (item.isLike ? -1 : 1) }
          : item
      )
      setBlogs((items) => items.map((item) => (
        updateLiked(item)
      )))
      setFeed((items) => items.map(updateLiked))
      setSelectedBlog((item) => item?.id === id ? updateLiked(item) : item)
      loadProfile()
    } else {
      setToast(body.errorMsg || '操作失败')
    }
  }

  async function followAuthor(blog) {
    if (!loggedIn) {
      setToast('登录后才能关注')
      setActiveTab('login')
      return
    }
    const userId = blog?.userId
    if (!userId || String(userId) === String(user?.id)) {
      setToast('这是你自己的笔记')
      return
    }
    const nextFollow = !blog.isFollow
    const body = await api(`/follow/${userId}/${nextFollow}`, { method: 'PUT' })
    if (body.success) {
      const updateFollow = (item) => (
        String(item.userId) === String(userId) ? { ...item, isFollow: nextFollow } : item
      )
      setBlogs((items) => items.map(updateFollow))
      setFeed((items) => items.map(updateFollow))
      setSelectedBlog((item) => item && String(item.userId) === String(userId) ? updateFollow(item) : item)
      setProfileData((data) => data ? {
        ...data,
        followCount: Math.max(0, Number(data.followCount || 0) + (nextFollow ? 1 : -1)),
        myBlogs: (data.myBlogs || []).map(updateFollow),
        likedBlogs: (data.likedBlogs || []).map(updateFollow)
      } : data)
      if (nextFollow) loadFeed()
    }
    setToast(body.success ? (nextFollow ? '已关注作者' : '已取消关注') : body.errorMsg || '关注失败')
  }

  async function signToday() {
    if (!loggedIn) {
      setToast('登录后才能签到')
      setActiveTab('login')
      return
    }
    const body = await api('/user/sign', { method: 'POST' })
    setToast(body.success ? '签到成功' : body.errorMsg || '签到失败')
    loadSignCount()
    loadProfile()
  }

  async function loadSignCount() {
    const body = await api('/user/sign/count')
    if (body.success) setSignDays(body.data)
  }

  async function recordUv(targetType, targetId) {
    const body = await api('/stats/uv', {
      method: 'POST',
      body: JSON.stringify({ targetType, targetId, visitor: ensureVisitor() })
    })
    if (!body.success && targetType !== 'site') setToast(body.errorMsg || 'UV 璁板綍澶辫触')
  }

  async function loadStats() {
    const today = new Date().toISOString().slice(0, 10)
    const site = await api(`/stats/uv/site?date=${today}`)
    const shop = selectedShop?.id ? await api(`/stats/uv/shop/${selectedShop.id}?date=${today}`) : null
    if (site.success) setSiteUv(site.data)
    if (shop?.success) setShopUv(shop.data)
  }

  async function loadVoucherActivities() {
    setVoucherLoading(true)
    const body = await api('/voucher/activities')
    setVoucherLoading(false)
    if (body.success && Array.isArray(body.data)) {
      setVoucherActivities(body.data)
      return
    }
    setVoucherActivities([])
    setToast(body.errorMsg || '优惠活动加载失败')
  }

  async function claimVoucher(activity) {
    if (!loggedIn) {
      setToast('登录后才能参与活动')
      setActiveTab('login')
      return
    }
    if (activity.type === 1 && activity.activityStatus !== 'ACTIVE') {
      setToast(activity.activityStatus === 'UPCOMING' ? '活动还未开始' : '当前不可抢购')
      return
    }
    setVoucherBusyId(activity.id)
    const path = activity.type === 1
      ? `/voucher-order/seckill/${activity.id}`
      : `/voucher-order/${activity.id}`
    const body = await api(path, { method: 'POST' })
    setVoucherBusyId(null)
    if (body.success) {
      setToast(activity.type === 1 ? `秒杀已受理，订单 ID：${body.data}` : `领取成功，订单 ID：${body.data}`)
      loadVoucherActivities()
    } else {
      setToast(body.errorMsg || '参与失败')
    }
  }

  const topBlogs = useMemo(() => blogs.slice(0, 3), [blogs])

  return (
    <div className="app">
      <header className="topbar">
        <button className="brand-button" onClick={() => setActiveTab('home')} aria-label="返回首页">
          <span className="brand-mark">S</span>
          <span>
            <strong>Spot AI</strong>
            <small>本地生活点评</small>
          </span>
        </button>
        <form className="search-box" onSubmit={handleGlobalSearch}>
          <input
            value={globalSearch}
            onChange={(event) => setGlobalSearch(event.target.value)}
            aria-label="搜索商户"
            placeholder="搜索商户名称、商圈或地址"
          />
          <button type="submit" aria-label="搜索">
            <span className="search-icon" aria-hidden="true" />
          </button>
        </form>
        <nav className="desktop-nav" aria-label="主导航">
          {[
            ['home', '首页'],
            ['shops', '附近'],
            ['blogs', '探店'],
            ['deals', '优惠'],
            ['profile', '我的']
          ].map(([key, label]) => (
            <button key={key} className={activeTab === key ? 'nav-active' : ''} onClick={() => {
              setActiveTab(key)
              if (key === 'profile') {
                loadStats()
                loadProfile()
                if (localStorage.getItem(tokenKey)) loadFeed()
              }
              if (key === 'blogs') loadBlogs()
              if (key === 'deals') loadVoucherActivities()
            }}>
              {label}
            </button>
          ))}
        </nav>
      </header>

      <main>
        {activeTab === 'home' && (
          <HomePage
            user={user}
            categories={categories}
            shops={shops}
            blogs={topBlogs}
            signDays={signDays}
            onCategory={(id) => {
              setTypeId(id)
              setActiveTab('shops')
            }}
            onOpenShop={(shop) => {
              loadShopDetail(shop)
            }}
            onSign={signToday}
            onLogin={() => setActiveTab('login')}
          />
        )}

        {activeTab === 'login' && (
          <LoginPage
            email={email}
            code={code}
            countdown={countdown}
            busy={busy}
            user={user}
            setEmail={setEmail}
            setCode={setCode}
            onSendCode={sendCode}
            onLogin={login}
            onLogout={logout}
          />
        )}

        {activeTab === 'shops' && (
          <ShopPage
            categories={categories}
            typeId={typeId}
            setTypeId={setTypeId}
            shops={shops}
            loading={shopLoading}
            selectedShop={selectedShop}
            searchTitle={shopSearchTitle}
            onOpenShop={loadShopDetail}
            userLocation={userLocation}
          />
        )}

        {activeTab === 'shopDetail' && (
          <ShopDetailPage
            shop={selectedShop}
            reviews={shopReviews}
            reviewSummary={reviewSummary}
            loading={shopDetailLoading}
            reviewLoading={reviewLoading}
            reviewSummaryLoading={reviewSummaryLoading}
            reviewHasMore={reviewHasMore}
            onBack={() => setActiveTab('shops')}
            onLoadMoreReviews={() => selectedShop && loadShopReviews(selectedShop.id, reviewPage + 1, true)}
          />
        )}

        {activeTab === 'blogs' && (
          <BlogPage
            user={user}
            blogs={blogs}
            loading={blogLoading}
            loggedIn={loggedIn}
            hasMore={blogHasMore}
            detailLoading={blogDetailLoading}
            selectedBlog={selectedBlog}
            onLike={likeBlog}
            onFollow={followAuthor}
            onLoadMore={() => loadBlogs(blogPage + 1, true)}
            onOpenBlog={openBlogDetail}
            onCloseBlog={() => setSelectedBlog(null)}
            onOpenCompose={openComposeBlog}
          />
        )}

        {activeTab === 'composeBlog' && (
          <ComposeBlogPage
            form={blogForm}
            setForm={setBlogForm}
            shopKeyword={composeShopKeyword}
            setShopKeyword={setComposeShopKeyword}
            shopResults={composeShopResults}
            selectedShop={selectedComposeShop}
            uploading={imageUploading}
            onSearchShop={searchComposeShop}
            onSelectShop={selectComposeShop}
            onUploadImages={uploadBlogImages}
            onRemoveImage={removeBlogImage}
            onPublish={publishBlog}
            onBack={() => setActiveTab('blogs')}
          />
        )}

        {activeTab === 'deals' && (
          <DealPage
            activities={voucherActivities}
            loading={voucherLoading}
            busyId={voucherBusyId}
            loggedIn={loggedIn}
            onRefresh={loadVoucherActivities}
            onClaim={claimVoucher}
            onLogin={() => setActiveTab('login')}
          />
        )}

        {activeTab === 'profile' && (
          <ProfilePage
            user={user}
            signDays={signDays}
            siteUv={siteUv}
            shopUv={shopUv}
            profileData={profileData}
            profileLoading={profileLoading}
            profilePanel={profilePanel}
            feed={feed}
            selectedShop={selectedShop}
            onSign={signToday}
            onPanel={setProfilePanel}
            onRefreshProfile={() => {
              loadProfile()
              loadFeed()
            }}
            onRefreshStats={loadStats}
            onOpenBlog={openBlogDetail}
            onLike={likeBlog}
            onFollow={followAuthor}
            onLogin={() => setActiveTab('login')}
            onLogout={logout}
          />
        )}
      </main>

      <nav className="mobile-tabs" aria-label="底部导航">
        {[
          ['home', '首页'],
          ['shops', '附近'],
          ['blogs', '探店'],
          ['composeBlog', '+'],
          ['deals', '优惠'],
          ['profile', '我的']
        ].map(([key, label]) => (
          <button key={key} className={activeTab === key ? 'nav-active' : ''} onClick={() => {
            if (key === 'composeBlog') {
              openComposeBlog()
              return
            }
            setActiveTab(key)
            if (key === 'deals') loadVoucherActivities()
            if (key === 'profile') {
              loadStats()
              loadProfile()
              if (localStorage.getItem(tokenKey)) loadFeed()
            }
          }}>
            {label}
          </button>
        ))}
      </nav>

      {toast && (
        <div className="toast" role="status">
          <span>{toast}</span>
          <button onClick={() => setToast('')} aria-label="关闭提示">关闭</button>
        </div>
      )}

     <AiChatWidget
        activeTab={activeTab}
        open={aiChatOpen}
        onToggle={() => setAiChatOpen((value) => !value)}
        messages={aiChatMessages}
        input={aiChatInput}
        setInput={setAiChatInput}
        loading={aiChatLoading}
        selectedShop={selectedShop}
        onSubmit={sendAiChatMessage}
      />
    </div>
  )
}

function AiChatWidget({ activeTab, open, onToggle, messages, input, setInput, loading, selectedShop, onSubmit }) {
  const messageListRef = useRef(null)

  useEffect(() => {
    if (messageListRef.current) {
      messageListRef.current.scrollTop = messageListRef.current.scrollHeight
    }
  }, [messages])

  return (
    <aside className={open ? 'ai-chat-widget open' : 'ai-chat-widget'} aria-label="Spot AI 对话助手">
      {open && (
        <section className="ai-chat-panel" aria-labelledby="ai-chat-title">
          <header className="ai-chat-header">
            <div>
              <p className="eyebrow">Spot AI</p>
              <h2 id="ai-chat-title">本地生活助手</h2>
            </div>
            <button type="button" className="ai-chat-icon-button" onClick={onToggle} aria-label="收起 AI 对话">
              ×
            </button>
          </header>
          <div className="ai-chat-context">
            {activeTab === 'shopDetail' && selectedShop?.name ? `当前店铺：${selectedShop.name}` : '当前店铺：无'}
          </div>
          <div className="ai-chat-messages" role="log" aria-live="polite" aria-relevant="additions" ref={messageListRef}>
            {messages.map((message, index) => (
              <article
                key={`${message.role}-${index}-${message.generatedAt || ''}`}
                className={`ai-chat-message ${message.role === 'user' ? 'user' : 'assistant'} ${message.error ? 'error' : ''}`}
              >
                <span>{message.role === 'user' ? '你' : 'AI'}</span>
                {message.role === 'user' ? <p>{message.content}</p> : <AiMarkdown content={message.content} />}
                {message.role !== 'user' && (message.agentRoute || message.memoryUpdated) && (
                  <div className="ai-chat-meta">
                    {agentRouteText(message.agentRoute) && <small>{agentRouteText(message.agentRoute)}</small>}
                    {message.memoryUpdated && <small>已更新偏好</small>}
                  </div>
                )}
                {message.role !== 'user' && Array.isArray(message.memories) && message.memories.length > 0 && (
                  <div className="ai-memory-tags" aria-label="本轮更新的偏好记忆">
                    {message.memories.slice(0, 3).map((memory) => (
                      <small key={`${memory.memoryKey}-${memory.summary}`}>
                        {memoryLabel(memory.memoryKey)}
                      </small>
                    ))}
                  </div>
                )}
              </article>
            ))}
            {loading && (
              <article className="ai-chat-message assistant" aria-label="AI 正在回复">
                <span>AI</span>
                <div className="ai-markdown"><p>正在思考...</p></div>
              </article>
            )}
          </div>
          <form className="ai-chat-form" onSubmit={onSubmit}>
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              maxLength={1000}
              rows={2}
              placeholder="问问这家店适合什么场景..."
              aria-label="输入 AI 对话内容"
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  onSubmit(event)
                }
              }}
            />
            <button type="submit" className="primary" disabled={loading || !input.trim()}>
              {loading ? '发送中' : '发送'}
            </button>
          </form>
        </section>
      )}
      <button
        type="button"
        className="ai-chat-fab"
        onClick={onToggle}
        aria-expanded={open}
        aria-label={open ? '收起 AI 对话' : '打开 AI 对话'}
      >
        AI
      </button>
    </aside>
  )
}

function agentRouteText(route) {
  const labels = {
    SHOP_GUIDE: '找店',
    REVIEW_RAG: '评价总结',
    COUPON: '优惠查询',
    ORDER_GUARD: '订单守护'
  }
  return labels[route] || 'Spot AI Agent'
}

function memoryLabel(memoryKey) {
  const labels = {
    'dining.preference.taste': '口味偏好',
    'dining.preference.environment': '环境偏好',
    'dining.preference.budget': '预算偏好',
    'dining.preference.area': '区域偏好',
    'dining.preference.scene': '场景偏好',
    'dining.preference.discount': '优惠偏好',
    'dining.avoid.keyword': '避雷偏好'
  }
  return labels[memoryKey] || '偏好'
}

function AiMarkdown({ content }) {
  const html = useMemo(() => {
    if (!content) return ''
    const raw = marked.parse(content, { breaks: true, gfm: true })
    return raw
      .replace(/<script[\s\S]*?<\/script>/gi, '')
      .replace(/on\w+\s*=\s*["\'][^"\']*["\']/gi, '')
  }, [content])
  return <div className="ai-markdown" dangerouslySetInnerHTML={{ __html: html }} />
}

function HomePage({ user, categories, shops, blogs, signDays, onCategory, onOpenShop, onSign, onLogin }) {
  const heroImage = normalizeImage(shops[0]?.images)
  return (
    <div className="page-grid home-grid">
      <section className="hero-band">
        <div>
          <p className="eyebrow">西安 · 本地数据库推荐</p>
          <h1>发现附近值得去的好店</h1>
          <p>查附近商户、写探店笔记、抢限时券、记录访问和签到，一套本地生活闭环。</p>
          <div className="hero-actions">
            <button className="primary" onClick={() => onCategory(1)}>看附近美食</button>
            <button className="ghost" onClick={user ? onSign : onLogin}>{user ? '今日签到' : '登录体验'}</button>
          </div>
        </div>
        <VisualPanel image={heroImage} title={shops[0]?.name || '本地数据库商户'} subtitle={shops[0]?.area || '等待商户数据'} />
      </section>

      <section className="category-strip" aria-label="商户分类">
        {categories.length === 0 ? (
          <div className="category-empty">暂无分类，请确认 `/shop-type/list` 可访问</div>
        ) : categories.map((item) => (
          <button key={item.id} onClick={() => onCategory(item.id)}>
            <span>{item.tone}</span>
            {item.name}
          </button>
        ))}
      </section>

      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">附近热店</p>
            <h2>按距离和口碑筛选</h2>
          </div>
          <button className="text-button" onClick={() => onCategory(1)}>全部商户</button>
        </div>
        <div className="shop-list compact">
          {shops.length === 0 ? (
            <EmptyState title="暂无商户数据" text="请确认后端已启动，tb_shop 已导入西安商户，并刷新 Redis GEO。" />
          ) : shops.slice(0, 3).map((shop) => (
            <ShopCard key={shop.id} shop={shop} onOpen={() => onOpenShop(shop)} />
          ))}
        </div>
      </section>

      <aside className="side-rail">
        <div className="status-panel">
          <p className="eyebrow">我的状态</p>
          <h2>{user ? user.nickName : '未登录'}</h2>
          <p>{user ? `连续签到 ${signDays ?? 0} 天` : '登录后可签到、点赞、发布探店笔记'}</p>
          <button className="primary wide" onClick={user ? onSign : onLogin}>{user ? '签到' : '登录 / 注册'}</button>
        </div>
        <div className="blog-mini-list">
          <p className="eyebrow">正在热聊</p>
          {blogs.length === 0 ? (
            <article>
              <strong>暂无探店笔记</strong>
              <span>发布第一篇真实探店内容后会显示在这里</span>
            </article>
          ) : blogs.map((blog) => (
            <article key={blog.id}>
              <strong>{blog.title}</strong>
              <span>{blog.liked || 0} 人点赞</span>
            </article>
          ))}
        </div>
      </aside>
    </div>
  )
}

function LoginPage({ email, code, countdown, busy, user, setEmail, setCode, onSendCode, onLogin, onLogout }) {
  return (
    <section className="auth-layout">
      <div className="auth-copy">
        <p className="eyebrow">邮箱快捷登录</p>
        <h1>用邮箱验证码进入 Spot AI</h1>
        <p>登录后可以完成签到、点赞、关注、发布探店笔记和秒杀下单。</p>
      </div>
      <div className="auth-panel">
        {user ? (
          <div className="profile-card">
            <div className="avatar">{user.nickName?.slice(0, 1) || 'U'}</div>
            <h2>{user.nickName}</h2>
            <p>ID {user.id}</p>
            <button className="secondary wide" onClick={onLogout}>退出登录</button>
          </div>
        ) : (
          <form className="form" onSubmit={onLogin}>
            <label>
              邮箱
              <input value={email} onChange={(event) => setEmail(event.target.value.trim())} placeholder="请输入邮箱地址" inputMode="email" />
            </label>
            <label>
              验证码
              <div className="code-row">
                <input value={code} onChange={(event) => setCode(event.target.value.trim())} placeholder="6 位验证码" inputMode="numeric" maxLength={6} />
                <button type="button" className="secondary" onClick={onSendCode} disabled={busy || countdown > 0}>
                  {countdown > 0 ? `${countdown}s` : '发送'}
                </button>
              </div>
            </label>
            <button className="primary wide" disabled={busy} type="submit">{busy ? '处理中...' : '登录 / 注册'}</button>
          </form>
        )}
      </div>
    </section>
  )
}

function ShopPage({ categories, typeId, setTypeId, shops, loading, selectedShop, searchTitle, onOpenShop, userLocation }) {
  return (
    <div className="page-grid shop-layout">
      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">附近商户</p>
            <h1>{searchTitle || '按分类查找附近好店'}</h1>
          </div>
          <p className="location-note">当前位置：{userLocation.address}</p>
        </div>
        <div className="chips">
          {categories.length === 0 ? (
            <span className="chip-note">暂无分类，请先检查 tb_shop_type</span>
          ) : categories.map((item) => (
            <button key={item.id} className={typeId === item.id ? 'chip-active' : ''} onClick={() => setTypeId(item.id)}>
              {item.name}
            </button>
          ))}
        </div>
        {loading ? <SkeletonList /> : (
          <div className="shop-list">
            {shops.length === 0 ? <EmptyState title="暂无商户" text="换个关键词或分类再试试。" /> : shops.map((shop) => (
              <ShopCard key={shop.id} shop={shop} onOpen={() => onOpenShop(shop)} />
            ))}
          </div>
        )}
      </section>
      <aside className="detail-panel">
        {selectedShop ? <ShopDetail shop={selectedShop} /> : <EmptyState title="选择一家店" text="点击左侧商户查看详情。" />}
      </aside>
    </div>
  )
}

function ShopDetailPage({
  shop,
  reviews,
  reviewSummary,
  loading,
  reviewLoading,
  reviewSummaryLoading,
  reviewHasMore,
  onBack,
  onLoadMoreReviews
}) {
  if (loading && !shop) {
    return <SkeletonList />
  }
  if (!shop) {
    return (
      <section className="content-section">
        <EmptyState title="未选择商户" text="请先从附近商户列表选择一家店。" />
        <button className="secondary" onClick={onBack}>返回列表</button>
      </section>
    )
  }

  const images = imageList(shop.images)
  const heroImage = images[0]
  return (
    <div className="shop-detail-page">
      <button className="text-button back-button" onClick={onBack}>返回附近商户</button>
      <section className="shop-detail-hero">
        {heroImage ? <img src={heroImage} alt={shop.name} /> : <ImagePlaceholder label={shop.name} large />}
        <div>
          <p className="eyebrow">商户详情</p>
          <h1>{shop.name}</h1>
          <div className="rating-line">
            <strong>{scoreText(shop.score)}</strong>
            <span>{shop.comments || 0} 条评价</span>
            <span>{distanceText(shop.distance)}</span>
          </div>
          <p>{shop.area || '热门商圈'} · {shop.address || '地址待补充'}</p>
          <div className="shop-action-row">
            <button className="primary" onClick={() => window.alert('后续可接入地图导航')}>去这里</button>
            <button className="secondary" onClick={() => window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })}>看评价</button>
          </div>
        </div>
      </section>

      <section className="detail-info-grid">
        <ShopInfoItem label="人均消费" value={money(shop.avgPrice)} />
        <ShopInfoItem label="营业时间" value={hoursText(shop.openHours)} multiline />
        <ShopInfoItem label="距西电北校区" value={distanceText(shop.distance)} />
      </section>

      <ReviewAiSummary summary={reviewSummary} loading={reviewSummaryLoading} />

      <section className="detail-metric-grid">
        <Metric title="人均消费" value={money(shop.avgPrice)} action="详情" onAction={() => {}} />
        <Metric title="营业时间" value={shop.openHours || '待补充'} action="记录" onAction={() => {}} />
        <Metric title="距离西电北校区" value={distanceText(shop.distance)} action="GEO" onAction={() => {}} />
      </section>

      {images.length > 1 && (
        <section className="content-section">
          <div className="section-head">
            <div>
              <p className="eyebrow">店铺图片</p>
              <h2>环境与菜品</h2>
            </div>
          </div>
          <div className="shop-gallery">
            {images.slice(0, 6).map((image) => <img key={image} src={image} alt={shop.name} />)}
          </div>
        </section>
      )}

      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">用户评价</p>
            <h2>探店笔记和真实反馈</h2>
          </div>
        </div>
        <div className="blog-feed">
          {reviews.length === 0 ? (
            <EmptyState title="暂无评价" text="当前店铺还没有用户评价，可以登录后发布第一条评价。" />
          ) : reviews.map((blog) => (
            <ReviewCard key={blog.id} review={blog} />
          ))}
          {reviewHasMore && (
            <button className="secondary wide" onClick={onLoadMoreReviews} disabled={reviewLoading}>
              {reviewLoading ? '加载中...' : '加载更多评价'}
            </button>
          )}
        </div>
      </section>
    </div>
  )
}

function BlogPage({
  user,
  blogs,
  loading,
  loggedIn,
  hasMore,
  detailLoading,
  selectedBlog,
  onLike,
  onFollow,
  onLoadMore,
  onOpenBlog,
  onCloseBlog,
  onOpenCompose
}) {
  return (
    <div className="page-grid blog-layout">
      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">探店笔记</p>
            <h1>真实体验和口碑内容</h1>
          </div>
        </div>
        {loading ? <SkeletonList /> : (
          <div className="blog-feed">
            {blogs.length === 0 ? (
              <EmptyState title="暂无探店笔记" text="当前没有从本地数据库查询到探店内容，可以登录后发布一篇。" />
            ) : blogs.map((blog) => (
              <BlogCard key={blog.id} blog={blog} currentUser={user} onOpen={() => onOpenBlog(blog)} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog)} />
            ))}
            {hasMore && (
              <button className="secondary wide" onClick={onLoadMore} disabled={loading}>
                {loading ? '加载中...' : '加载更多笔记'}
              </button>
            )}
          </div>
        )}
      </section>
      <aside className="compose-panel">
        <p className="eyebrow">发布笔记</p>
        <h2>写一篇探店</h2>
        <button className="add-note-button" type="button" onClick={onOpenCompose} aria-label="发布探店笔记">
          <span>+</span>
          <strong>{loggedIn ? '写一篇探店' : '登录后发布'}</strong>
          <small>{loggedIn ? '添加商户、图片和正文' : '点击后提示需要登录'}</small>
        </button>
      </aside>
      {selectedBlog && (
        <BlogDetailDialog
          blog={selectedBlog}
          loading={detailLoading}
          onClose={onCloseBlog}
          onLike={() => onLike(selectedBlog.id)}
          onFollow={() => onFollow(selectedBlog)}
          currentUser={user}
        />
      )}
    </div>
  )
}

function ReviewAiSummary({ summary, loading }) {
  if (loading) {
    return (
      <section className="ai-review-summary" aria-busy="true" aria-label="正在生成评价总结">
        <div className="ai-summary-heading">
          <p className="eyebrow">AI 评论摘要</p>
          <h2>正在分析近期评价...</h2>
        </div>
        <div className="ai-summary-skeleton" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </section>
    )
  }

  if (!summary || summary.status !== 'READY') {
    return (
      <section className="ai-review-summary ai-summary-empty" role="status">
        <div className="ai-summary-heading">
          <p className="eyebrow">AI 评论摘要</p>
          <h2>店铺评价概览</h2>
        </div>
        <p>{summary?.message || '当前暂无可用的评价总结'}</p>
      </section>
    )
  }

  const groups = [
    { title: '大家认可', items: summary.highlights || [], tone: 'positive' },
    { title: '需要留意', items: summary.weaknesses || [], tone: 'caution' },
    { title: '适合场景', items: summary.scenes || [], tone: 'neutral' }
  ].filter((group) => group.items.length > 0)

  return (
    <section className="ai-review-summary" aria-labelledby="ai-review-summary-title">
      <div className="ai-summary-heading">
        <div>
          <p className="eyebrow">AI 评论摘要</p>
          <h2 id="ai-review-summary-title">大家怎么评价这家店</h2>
        </div>
        <span>{summary.reviewCount || 0} 条评价 · {timeText(summary.generatedAt)}</span>
      </div>
      <p className="ai-summary-copy">{summary.summary}</p>
      <div className="ai-summary-groups">
        {groups.map((group) => (
          <section key={group.title} className={`ai-summary-group ${group.tone}`}>
            <h3>{group.title}</h3>
            <div className="ai-summary-tags">
              {group.items.map((item) => <span key={item}>{item}</span>)}
            </div>
          </section>
        ))}
      </div>
    </section>
  )
}

function ComposeBlogPage({
  form,
  setForm,
  shopKeyword,
  setShopKeyword,
  shopResults,
  selectedShop,
  uploading,
  onSearchShop,
  onSelectShop,
  onUploadImages,
  onRemoveImage,
  onPublish,
  onBack
}) {
  const images = imageList(form.images)
  const availableShops = shopResults.filter((shop) => String(shop.id) !== String(selectedShop?.id))
  return (
    <div className="compose-page">
      <button className="text-button back-button" type="button" onClick={onBack}>返回探店</button>
      <section className="compose-workspace">
        <div className="compose-main">
          <div className="section-head">
            <div>
              <p className="eyebrow">发布探店笔记</p>
              <h1>记录这家店的真实体验</h1>
            </div>
            <button className="primary" type="button" onClick={onPublish}>发布</button>
          </div>

          <form className="shop-search-panel" onSubmit={onSearchShop}>
            <label>
              搜索店铺
              <div className="inline-search">
                <input
                  value={shopKeyword}
                  onChange={(event) => setShopKeyword(event.target.value)}
                  placeholder="输入店名、商圈或地址"
                />
                <button className="secondary" type="submit">搜索</button>
              </div>
            </label>
            {selectedShop && (
              <div className="selected-shop">
                <span className="selected-label">已确定店铺</span>
                <strong>{selectedShop.name}</strong>
                <small>{selectedShop.area || '商圈待补充'} · {selectedShop.address || '地址待补充'}</small>
              </div>
            )}
            {availableShops.length > 0 && (
              <div className="shop-picker" role="listbox" aria-label="店铺搜索结果">
                {availableShops.map((shop) => (
                  <button
                    key={shop.id}
                    type="button"
                    className={String(form.shopId) === String(shop.id) ? 'shop-pick-active' : ''}
                    onClick={() => onSelectShop(shop)}
                  >
                    <span>
                      <strong>{shop.name}</strong>
                      <em>选择这家</em>
                    </span>
                    <small>{shop.area || '热门商圈'} · {shop.address || '地址待补充'}</small>
                  </button>
                ))}
              </div>
            )}
          </form>

          <form className="form note-editor" onSubmit={onPublish}>
            <input type="hidden" value={form.shopId} readOnly />
            <label>
              标题
              <input
                value={form.title}
                onChange={(event) => setForm({ ...form, title: event.target.value })}
                placeholder="例如：这家店的午市套餐很能打"
              />
            </label>
            <label>
              正文
              <textarea
                value={form.content}
                onChange={(event) => setForm({ ...form, content: event.target.value })}
                placeholder="口味、环境、服务、排队情况..."
              />
            </label>
            <button className="primary wide" type="submit">发布探店笔记</button>
          </form>
        </div>

        <aside className="upload-panel">
          <p className="eyebrow">图片</p>
          <h2>上传现场照片</h2>
          <label className="upload-drop">
            <input
              type="file"
              accept="image/*"
              multiple
              disabled={uploading}
              onChange={(event) => {
                onUploadImages(event.target.files)
                event.target.value = ''
              }}
            />
            <span>+</span>
            <strong>{uploading ? '上传中...' : '选择图片'}</strong>
            <small>最多一次选择 9 张，上传后自动写入笔记</small>
          </label>
          {images.length > 0 ? (
            <div className="uploaded-grid">
              {images.map((image) => (
                <div key={image} className="uploaded-image">
                  <img src={image} alt="探店上传图片" />
                  <button type="button" onClick={() => onRemoveImage(image)} aria-label="移除图片">×</button>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title="还没有图片" text="上传店铺环境、菜品或菜单照片，内容会更完整。" />
          )}
        </aside>
      </section>
    </div>
  )
}

function DealPage({ activities, loading, busyId, loggedIn, onRefresh, onClaim, onLogin }) {
  const activeSeckill = activities.filter((item) => item.type === 1 && item.activityStatus === 'ACTIVE')
  const upcomingSeckill = activities.filter((item) => item.type === 1 && item.activityStatus === 'UPCOMING')
  const normalVouchers = activities.filter((item) => item.type === 0)
  return (
    <div className="deal-page">
      <section className="deal-hero user-deal-hero">
        <div>
          <p className="eyebrow">限时优惠</p>
          <h1>抢代金券和秒杀券</h1>
          <p>活动数据来自本地数据库，只展示当前或未来可以参与的优惠。</p>
          <div className="hero-actions">
            <button className="primary" onClick={loggedIn ? onRefresh : onLogin}>{loggedIn ? '刷新活动' : '登录参与'}</button>
            <button className="ghost" onClick={onRefresh}>查看最新</button>
          </div>
        </div>
        <div className="coupon-preview" aria-hidden="true">
          <span>券</span>
          <strong>{activeSeckill.length} 场秒杀进行中</strong>
          <p>{upcomingSeckill.length} 场即将开始 · {normalVouchers.length} 张代金券可领</p>
        </div>
      </section>

      {loading ? <SkeletonList /> : (
        <div className="deal-sections">
          <VoucherSection
            title="正在秒杀"
            text="库存有限，下单结果以服务端异步处理为准。"
            items={activeSeckill}
            busyId={busyId}
            onClaim={onClaim}
          />
          <VoucherSection
            title="即将开始"
            text="提前查看活动时间，开始后即可参与。"
            items={upcomingSeckill}
            busyId={busyId}
            onClaim={onClaim}
          />
          <VoucherSection
            title="代金券"
            text="登录后可直接领取，到店消费时使用。"
            items={normalVouchers}
            busyId={busyId}
            onClaim={onClaim}
          />
          {activities.length === 0 && (
            <EmptyState title="暂无可参与活动" text="当前数据库中没有上架且未结束的优惠活动。" />
          )}
        </div>
      )}
    </div>
  )
}

function VoucherSection({ title, text, items, busyId, onClaim }) {
  if (items.length === 0) return null
  return (
    <section className="content-section voucher-section">
      <div className="section-head">
        <div>
          <p className="eyebrow">{title}</p>
          <h2>{text}</h2>
        </div>
      </div>
      <div className="voucher-grid">
        {items.map((item) => (
          <VoucherCard key={item.id} activity={item} busy={busyId === item.id} onClaim={() => onClaim(item)} />
        ))}
      </div>
    </section>
  )
}

function VoucherCard({ activity, busy, onClaim }) {
  const isSeckill = activity.type === 1
  const discount = Number(activity.actualValue || 0) - Number(activity.payValue || 0)
  const stock = activity.stock ?? 0
  const initStock = activity.initStock || stock || 1
  const progress = isSeckill ? Math.max(0, Math.min(100, Math.round((stock / initStock) * 100))) : 100
  const disabled = busy || activity.activityStatus === 'UPCOMING' || activity.activityStatus === 'SOLD_OUT'
  const buttonText = busy
    ? '处理中...'
    : activity.activityStatus === 'UPCOMING'
      ? '未开始'
      : activity.activityStatus === 'SOLD_OUT'
        ? '已抢完'
        : isSeckill ? '立即秒杀' : '立即领取'
  return (
    <article className={isSeckill ? 'voucher-card seckill-card' : 'voucher-card'}>
      <div className="voucher-value">
        <strong>{yuan(activity.actualValue)}</strong>
        <span>{activity.payValue > 0 ? `${yuan(activity.payValue)} 抢` : '免费领'}</span>
      </div>
      <div className="voucher-body">
        <div className="voucher-title-row">
          <h3>{activity.title}</h3>
          <span>{isSeckill ? '秒杀券' : '代金券'}</span>
        </div>
        <p>{activity.shopName || `商户 ${activity.shopId}`}</p>
        <p>{activity.subTitle || activity.rules || '到店消费可用'}</p>
        {isSeckill && (
          <>
            <div className="stock-line">
              <span>剩余 {stock}</span>
              <span>{timeText(activity.beginTime)} - {timeText(activity.endTime)}</span>
            </div>
            <div className="stock-bar" aria-label={`剩余库存 ${progress}%`}>
              <span style={{ width: `${progress}%` }} />
            </div>
          </>
        )}
        {!isSeckill && <p className="saving-line">可抵扣 {yuan(discount > 0 ? discount : activity.actualValue)}</p>}
        <button className={isSeckill ? 'primary' : 'secondary'} disabled={disabled} onClick={onClaim}>
          {buttonText}
        </button>
      </div>
    </article>
  )
}

function ProfilePage({
  user,
  signDays,
  siteUv,
  shopUv,
  profileData,
  profileLoading,
  profilePanel,
  feed,
  selectedShop,
  onSign,
  onPanel,
  onRefreshProfile,
  onRefreshStats,
  onOpenBlog,
  onLike,
  onFollow,
  onLogin,
  onLogout
}) {
  if (!user) {
    return (
      <section className="auth-layout">
        <div className="auth-copy">
          <p className="eyebrow">个人中心</p>
          <h1>登录后查看你的本地生活数据</h1>
          <p>签到、关注流、点赞、秒杀订单都依赖登录态。</p>
          <button className="primary" onClick={onLogin}>去登录</button>
        </div>
      </section>
    )
  }

  const data = profileData || {}
  const panelItems = {
    notes: data.myBlogs || [],
    likes: data.likedBlogs || [],
    coupons: data.vouchers || [],
    reviews: data.reviews || [],
    sign: []
  }
  const panelTitle = {
    notes: '我发布的笔记',
    likes: '我点赞的笔记',
    coupons: '我的优惠券',
    reviews: '我发布的评价',
    sign: '签到记录'
  }[profilePanel]

  return (
    <div className="profile-layout">
      <section className="profile-banner">
        <div className="avatar large">{user.nickName?.slice(0, 1) || 'U'}</div>
        <div>
          <p className="eyebrow">当前登录</p>
          <h1>{user.nickName}</h1>
          <p>ID {user.id} · 关注 {data.followCount ?? 0} · 粉丝 {data.fanCount ?? 0}</p>
        </div>
        <div className="profile-actions">
          <button className="secondary" onClick={onRefreshProfile} disabled={profileLoading}>
            {profileLoading ? '刷新中...' : '刷新'}
          </button>
          <button className="secondary" onClick={onLogout}>退出</button>
        </div>
      </section>
      <section className="profile-widget-grid">
        <ProfileWidget active={profilePanel === 'likes'} title="点赞笔记" value={data.likedBlogCount ?? 0} onClick={() => onPanel('likes')} />
        <ProfileWidget active={profilePanel === 'coupons'} title="优惠券" value={data.voucherCount ?? 0} onClick={() => onPanel('coupons')} />
        <ProfileWidget active={profilePanel === 'notes'} title="发布笔记" value={data.blogCount ?? 0} onClick={() => onPanel('notes')} />
        <ProfileWidget active={profilePanel === 'reviews'} title="发布评价" value={data.reviewCount ?? 0} onClick={() => onPanel('reviews')} />
        <ProfileWidget active={profilePanel === 'sign'} title="连续签到" value={`${data.signDays ?? signDays ?? 0} 天`} action="签到" onClick={() => onPanel('sign')} onAction={onSign} />
      </section>

      <section className="content-section profile-panel">
        <div className="section-head">
          <div>
            <p className="eyebrow">个人数据</p>
            <h2>{panelTitle}</h2>
          </div>
        </div>
        {profileLoading ? <SkeletonList /> : (
          <ProfilePanel
            type={profilePanel}
            items={panelItems[profilePanel] || []}
            signDays={data.signDays ?? signDays ?? 0}
            siteUv={siteUv}
            shopUv={shopUv}
            selectedShop={selectedShop}
            currentUser={user}
            onSign={onSign}
            onRefreshStats={onRefreshStats}
            onOpenBlog={onOpenBlog}
            onLike={onLike}
            onFollow={onFollow}
          />
        )}
      </section>

      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">关注流</p>
            <h2>关注博主的最新笔记</h2>
          </div>
          <button className="secondary" onClick={onRefreshProfile}>刷新关注流</button>
        </div>
        <div className="blog-feed">
          {feed.length === 0 ? (
            <EmptyState title="暂无关注流" text="关注探店作者后，他们发布的新笔记会出现在这里。" />
          ) : feed.map((blog) => (
            <BlogCard key={`profile-feed-${blog.id}`} blog={blog} currentUser={user} onOpen={() => onOpenBlog(blog)} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog)} />
          ))}
        </div>
      </section>
    </div>
  )
}

function ProfileWidget({ active, title, value, action, onClick, onAction }) {
  return (
    <button className={active ? 'profile-widget active' : 'profile-widget'} type="button" onClick={onClick}>
      <span>{title}</span>
      <strong>{value}</strong>
      {action && <em onClick={(event) => {
        event.stopPropagation()
        onAction()
      }}>{action}</em>}
    </button>
  )
}

function ProfilePanel({ type, items, signDays, siteUv, shopUv, selectedShop, currentUser, onSign, onRefreshStats, onOpenBlog, onLike, onFollow }) {
  if (type === 'sign') {
    return (
      <div className="metric-grid">
        <Metric title="连续签到" value={`${signDays ?? 0} 天`} action="签到" onAction={onSign} />
        <Metric title="全站今日 UV" value={siteUv ?? '-'} action="刷新" onAction={onRefreshStats} />
        <Metric title={`${selectedShop?.name || '当前商户'} UV`} value={shopUv ?? '-'} action="刷新" onAction={onRefreshStats} />
      </div>
    )
  }
  if (items.length === 0) {
    const emptyText = {
      notes: '你还没有发布探店笔记。',
      likes: '你还没有点赞过探店笔记。',
      coupons: '你还没有领取优惠券。',
      reviews: '你还没有发布评价。'
    }[type] || '暂无数据'
    return <EmptyState title="暂无内容" text={emptyText} />
  }
  if (type === 'coupons') {
    return (
      <div className="profile-coupon-list">
        {items.map((voucher) => <UserVoucherCard key={voucher.orderId} voucher={voucher} />)}
      </div>
    )
  }
  if (type === 'reviews') {
    return (
      <div className="blog-feed">
        {items.map((review) => <ReviewCard key={review.id} review={review} />)}
      </div>
    )
  }
  return (
    <div className="blog-feed">
      {items.map((blog) => (
        <BlogCard key={`${type}-${blog.id}`} blog={blog} currentUser={currentUser} onOpen={() => onOpenBlog(blog)} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog)} />
      ))}
    </div>
  )
}

function UserVoucherCard({ voucher }) {
  return (
    <article className="user-voucher-card">
      <div>
        <strong>{yuan(voucher.actualValue)}</strong>
        <span>{voucher.payValue > 0 ? `${yuan(voucher.payValue)} 抢` : '免费领取'}</span>
      </div>
      <section>
        <h3>{voucher.title || `优惠券 ${voucher.voucherId}`}</h3>
        <p>{voucher.shopName || `商户 ${voucher.shopId || '-'}`}</p>
        <p>{voucher.subTitle || '到店消费可用'} · {voucher.type === 1 ? '秒杀券' : '代金券'}</p>
        <small>订单 {voucher.orderId} · {dateText(voucher.createTime)}</small>
      </section>
    </article>
  )
}

function ShopCard({ shop, onOpen }) {
  const image = normalizeImage(shop.images)
  return (
    <article className="shop-card">
      {image ? <img src={image} alt={shop.name} /> : <ImagePlaceholder label={shop.name} />}
      <div>
        <div className="card-title-row">
          <h3>{shop.name}</h3>
          <span>{distanceText(shop.distance)}</span>
        </div>
        <p><strong>{scoreText(shop.score)}</strong> · {shop.comments || 0} 条评价 · 已售 {shop.sold || 0}</p>
        <p>{shop.area || '热门商圈'} · {shop.address || '地址待补充'}</p>
        <p>人均 {money(shop.avgPrice)} · {shop.openHours || '营业时间待定'}</p>
        <button className="text-button" onClick={onOpen}>查看详情</button>
      </div>
    </article>
  )
}

function ShopDetail({ shop }) {
  const image = normalizeImage(shop.images)
  return (
    <div className="shop-detail">
      {image ? <img src={image} alt={shop.name} /> : <ImagePlaceholder label={shop.name} large />}
      <p className="eyebrow">商户详情</p>
      <h2>{shop.name}</h2>
      <div className="rating-line">
        <strong>{scoreText(shop.score)}</strong>
        <span>{shop.comments || 0} 条评价</span>
        <span>{distanceText(shop.distance)}</span>
      </div>
      <dl>
        <div><dt>商圈</dt><dd>{shop.area || '-'}</dd></div>
        <div><dt>地址</dt><dd>{shop.address || '-'}</dd></div>
        <div><dt>营业</dt><dd>{shop.openHours || '-'}</dd></div>
        <div><dt>人均</dt><dd>{money(shop.avgPrice)}</dd></div>
      </dl>
    </div>
  )
}

function ShopInfoItem({ label, value, multiline = false }) {
  return (
    <article className={multiline ? 'shop-info-item multiline' : 'shop-info-item'}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}

function BlogCard({ blog, currentUser, onOpen, onLike, onFollow }) {
  const image = normalizeImage(blog.images)
  const content = richTextToPlainText(blog.content)
  const preview = content.length > 120 ? `${content.slice(0, 120)}...` : content
  const isSelf = currentUser && String(currentUser.id) === String(blog.userId)
  const followText = isSelf ? '本人' : blog.isFollow ? '已关注' : '关注'
  return (
    <article className="blog-card clickable-card" onClick={onOpen} role="button" tabIndex={0} onKeyDown={(event) => {
      if (event.key === 'Enter') onOpen()
    }}>
      {image ? <img src={image} alt={blog.title} /> : <ImagePlaceholder label="探店" />}
      <div>
        <div className="author-line">
          <span className="mini-avatar">{blog.name?.slice(0, 1) || 'U'}</span>
          <strong>{blog.name || `用户 ${blog.userId}`}</strong>
          <button className="text-button" onClick={(event) => {
            event.stopPropagation()
            onFollow()
          }}>{followText}</button>
        </div>
        <h3>{blog.title}</h3>
        <p>{preview}</p>
        <div className="blog-actions">
          <button className={blog.isLike ? 'liked' : ''} onClick={(event) => {
            event.stopPropagation()
            onLike()
          }}>赞 {blog.liked || 0}</button>
          <span>评 {blog.comments || 0}</span>
          <span>{blog.createTime ? String(blog.createTime).slice(0, 10) : '刚刚'}</span>
        </div>
      </div>
    </article>
  )
}

function BlogDetailDialog({ blog, loading, currentUser, onClose, onLike, onFollow }) {
  const images = imageList(blog.images)
  const content = richTextToPlainText(blog.content)
  const isSelf = currentUser && String(currentUser.id) === String(blog.userId)
  const followText = isSelf ? '本人' : blog.isFollow ? '已关注' : '关注'
  return (
    <div className="dialog-backdrop" role="presentation" onClick={onClose}>
      <section className="blog-dialog" role="dialog" aria-modal="true" aria-label={blog.title} onClick={(event) => event.stopPropagation()}>
        <button className="dialog-close" type="button" onClick={onClose} aria-label="关闭">×</button>
        {loading ? (
          <SkeletonList />
        ) : (
          <>
            <div className="author-line">
              <span className="mini-avatar">{blog.name?.slice(0, 1) || 'U'}</span>
              <strong>{blog.name || `用户 ${blog.userId}`}</strong>
              <button className="text-button" onClick={onFollow}>{followText}</button>
            </div>
            <h2>{blog.title}</h2>
            {images.length > 0 && (
              <div className="blog-dialog-images">
                {images.slice(0, 9).map((image) => <img key={image} src={image} alt={blog.title} />)}
              </div>
            )}
            <p className="blog-dialog-content">{content}</p>
            <div className="blog-actions">
              <button className={blog.isLike ? 'liked' : ''} onClick={onLike}>赞 {blog.liked || 0}</button>
              <span>评 {blog.comments || 0}</span>
              <span>{dateText(blog.createTime)}</span>
            </div>
          </>
        )}
      </section>
    </div>
  )
}

function ReviewCard({ review }) {
  const [expanded, setExpanded] = useState(false)
  const content = richTextToPlainText(review.content)
  const compact = content.length > 120
  const visibleContent = expanded || !compact ? content : `${content.slice(0, 120)}...`
  const images = Array.isArray(review.images) ? review.images : []

  return (
    <article className="review-card">
      <div className="review-author">
        <span className="mini-avatar">{review.userName?.slice(0, 1) || 'U'}</span>
        <div>
          <strong>{review.userName || `用户 ${review.userId}`}</strong>
          <span>{dateText(review.createTime)}</span>
        </div>
      </div>
      <div className="review-score" aria-label={`${review.score || 0} 分`}>
        <span>{stars(review.score)}</span>
        <strong>{review.score || 0}.0</strong>
      </div>
      <p>{visibleContent}</p>
      {compact && (
        <button className="text-button" onClick={() => setExpanded((value) => !value)}>
          {expanded ? '收起' : '展开'}
        </button>
      )}
      {images.length > 0 && (
        <div className="review-images">
          {images.slice(0, 6).map((image) => <img key={image} src={image} alt="评价图片" />)}
        </div>
      )}
      <div className="review-meta">
        <span>{review.liked || 0} 人觉得有用</span>
      </div>
    </article>
  )
}

function VisualPanel({ image, title, subtitle }) {
  return (
    <div className="visual-panel">
      {image ? <img src={image} alt={title} /> : (
        <div className="visual-placeholder">
          <strong>{title}</strong>
          <span>{subtitle}</span>
        </div>
      )}
    </div>
  )
}

function ImagePlaceholder({ label, large = false }) {
  return (
    <div className={large ? 'image-placeholder large' : 'image-placeholder'}>
      <span>{label?.slice(0, 2) || '店铺'}</span>
    </div>
  )
}

function Metric({ title, value, action, onAction }) {
  return (
    <article className="metric-card">
      <p>{title}</p>
      <strong>{value}</strong>
      <button className="text-button" onClick={onAction}>{action}</button>
    </article>
  )
}

function EmptyState({ title, text }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{text}</p>
    </div>
  )
}

function SkeletonList() {
  return (
    <div className="skeleton-list" aria-busy="true" aria-label="正在加载">
      <span />
      <span />
      <span />
    </div>
  )
}

createRoot(document.getElementById('root')).render(<App />)
