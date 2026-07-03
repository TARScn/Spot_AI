import React, { useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { marked } from 'marked'
import {
  Baby,
  Coffee,
  Dumbbell,
  Film,
  Flame,
  Gamepad2,
  HeartPulse,
  Hotel,
  MapPinned,
  Mic,
  Scissors,
  Sparkles,
  Store,
  Utensils,
  Wine
} from 'lucide-react'
import {
  businesses as mockBusinesses,
  categories as mockCategories,
  districts,
  filters,
  rankings
} from './mockLocalLifeData'
import './styles.css'

const tokenKey = 'spotai_token'
const aiGuestHistoryKey = 'spotai_ai_guest_history'
const userLocation = { x: '108.916860', y: '34.229210' }
const aiGreeting = '你好，我是 Spot AI 助手。可以帮你找店、比较评价、查询人均和优惠。'
const aiChatTimeoutMs = 45000

const categoryIconMap = {
  food: Utensils,
  hotpot: Flame,
  bbq: Flame,
  tea: Coffee,
  coffee: Coffee,
  hotel: Hotel,
  movie: Film,
  ktv: Mic,
  fun: Gamepad2,
  beauty: Sparkles,
  hair: Scissors,
  spa: HeartPulse,
  massage: HeartPulse,
  family: Baby,
  travel: MapPinned,
  sport: Dumbbell,
  bar: Wine,
  party: Gamepad2,
  default: Store
}

function categoryIconKey(name = '') {
  const value = repairText(name).toLowerCase()
  if (value.includes('ktv') || value.includes('k歌')) return 'ktv'
  if (value.includes('火锅')) return 'hotpot'
  if (value.includes('烧烤') || value.includes('烤肉')) return 'bbq'
  if (value.includes('奶茶') || value.includes('茶')) return 'tea'
  if (value.includes('咖啡')) return 'coffee'
  if (value.includes('酒店') || value.includes('住宿')) return 'hotel'
  if (value.includes('电影') || value.includes('影院')) return 'movie'
  if (value.includes('丽人') || value.includes('美甲') || value.includes('美容')) return 'beauty'
  if (value.includes('美发')) return 'hair'
  if (value.includes('spa') || value.includes('按摩') || value.includes('足疗')) return 'spa'
  if (value.includes('亲子')) return 'family'
  if (value.includes('周边游') || value.includes('旅游')) return 'travel'
  if (value.includes('健身') || value.includes('运动')) return 'sport'
  if (value.includes('酒吧')) return 'bar'
  if (value.includes('轰趴')) return 'party'
  if (value.includes('休闲') || value.includes('娱乐')) return 'fun'
  if (value.includes('美食') || value.includes('餐')) return 'food'
  return 'default'
}

const navItems = [
  { id: 'home', label: '找店' },
  { id: 'blogs', label: '探店笔记' },
  { id: 'deals', label: '秒杀优惠' },
  { id: 'profile', label: '我的' }
]

const filterStrategies = {
  nearby: (items) => [...items].sort((a, b) => a.distance - b.distance),
  rating: (items) => [...items].sort((a, b) => b.rating - a.rating),
  distance: (items) => [...items].sort((a, b) => a.distance - b.distance),
  price: (items) => [...items].sort((a, b) => a.avgPrice - b.avgPrice),
  sold: (items) => [...items].sort((a, b) => b.sold - a.sold),
  deal: (items) => [...items].sort((a, b) => b.deals.length - a.deals.length),
  quality: (items) => [...items].filter((item) => item.rating >= 4.7)
}

const emptyBlogDraft = {
  shopId: '',
  shopName: '',
  title: '',
  images: '',
  content: ''
}

const emptyReviewDraft = {
  score: 5,
  content: '',
  images: []
}

function initialAiMessages() {
  return [
    {
      id: createMessageId(),
      role: 'assistant',
      content: aiGreeting,
      generatedAt: new Date().toISOString()
    }
  ]
}

function createMessageId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function api(path, options = {}) {
  const token = localStorage.getItem(tokenKey)
  const headers = { ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch(path, { ...options, headers })
  let body
  try {
    body = await response.json()
  } catch {
    body = { success: false, errorMsg: `HTTP ${response.status}` }
  }
  if (!response.ok && !body.errorMsg) body.errorMsg = `HTTP ${response.status}`
  return body
}

function App() {
  const [activeView, setActiveView] = useState('home')
  const [city, setCity] = useState('西安')
  const [query, setQuery] = useState('')
  const [activeCategory, setActiveCategory] = useState('food')
  const [categoryOptions, setCategoryOptions] = useState(mockCategories)
  const [serverBusinesses, setServerBusinesses] = useState([])
  const [usingServerData, setUsingServerData] = useState(false)
  const [activeFilter, setActiveFilter] = useState('nearby')
  const [activeDistrict, setActiveDistrict] = useState('全部')
  const [selectedBusiness, setSelectedBusiness] = useState(null)
  const [detailVouchers, setDetailVouchers] = useState([])
  const [reviewSummary, setReviewSummary] = useState(null)
  const [reviewSummaryLoading, setReviewSummaryLoading] = useState(false)
  const [shopReviews, setShopReviews] = useState([])
  const [reviewPage, setReviewPage] = useState(1)
  const [reviewHasMore, setReviewHasMore] = useState(false)
  const [reviewLoading, setReviewLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isFiltering, setIsFiltering] = useState(false)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')

  const [blogs, setBlogs] = useState([])
  const [blogLoading, setBlogLoading] = useState(false)
  const [blogMode, setBlogMode] = useState('discover')
  const [blogComposerOpen, setBlogComposerOpen] = useState(false)
  const [selectedBlog, setSelectedBlog] = useState(null)
  const [blogDetailLoading, setBlogDetailLoading] = useState(false)
  const [blogDraft, setBlogDraft] = useState(emptyBlogDraft)
  const [blogPublishing, setBlogPublishing] = useState(false)
  const [blogImageUploading, setBlogImageUploading] = useState(false)
  const [blogShopKeyword, setBlogShopKeyword] = useState('')
  const [blogShopResults, setBlogShopResults] = useState([])
  const [blogShopSearching, setBlogShopSearching] = useState(false)
  const [followFeedCursor, setFollowFeedCursor] = useState({ minTime: 0, offset: 0, hasMore: false })

  const [voucherActivities, setVoucherActivities] = useState([])
  const [voucherLoading, setVoucherLoading] = useState(false)
  const [voucherBusyId, setVoucherBusyId] = useState(null)

  const [user, setUser] = useState(null)
  const [profileData, setProfileData] = useState(null)
  const [profileLoading, setProfileLoading] = useState(false)
  const [aiMemories, setAiMemories] = useState([])
  const [aiMemoryLoading, setAiMemoryLoading] = useState(false)
  const [aiMemoryDeletingKey, setAiMemoryDeletingKey] = useState('')
  const [aiMemoryClearing, setAiMemoryClearing] = useState(false)
  const [aiHistoryPreview, setAiHistoryPreview] = useState([])
  const [aiHistoryPreviewLoading, setAiHistoryPreviewLoading] = useState(false)
  const [aiHistoryClearing, setAiHistoryClearing] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [countdown, setCountdown] = useState(0)
  const [authBusy, setAuthBusy] = useState(false)
  const [reviewDraft, setReviewDraft] = useState(emptyReviewDraft)
  const [reviewPublishing, setReviewPublishing] = useState(false)
  const [reviewImageUploading, setReviewImageUploading] = useState(false)

  const [aiChatOpen, setAiChatOpen] = useState(false)
  const [aiChatInput, setAiChatInput] = useState('')
  const [aiChatLoading, setAiChatLoading] = useState(false)
  const [aiChatMessages, setAiChatMessages] = useState(initialAiMessages)
  const [aiHistoryLoadedFor, setAiHistoryLoadedFor] = useState('')
  const [aiHistoryLoading, setAiHistoryLoading] = useState(false)
  const activeDetailIdRef = useRef('')

  useEffect(() => {
    hydrateBackendData()
    hydrateUser()
    loadHotBlogs()
    loadVoucherActivities()
  }, [])

  useEffect(() => {
    if (countdown <= 0) return undefined
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [countdown])

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(''), 1800)
    return () => window.clearTimeout(timer)
  }, [toast])

  useEffect(() => {
    if (!aiChatOpen) return
    const historyKey = user?.id ? `user:${user.id}` : 'guest'
    if (aiHistoryLoadedFor === historyKey) return
    loadAiChatHistory(historyKey)
  }, [aiChatOpen, user?.id])

  async function hydrateBackendData() {
    setIsLoading(true)
    try {
      const categoryBody = await api('/shop-type/list')
      if (categoryBody.success && Array.isArray(categoryBody.data) && categoryBody.data.length > 0) {
        const serverCategories = categoryBody.data.map((item) => ({
          id: `server:${item.id}`,
          sourceId: item.id,
          name: repairText(item.name),
          iconKey: categoryIconKey(item.name)
        }))
        setCategoryOptions(serverCategories)
        setActiveCategory(serverCategories[0].id)
        await loadServerShops(serverCategories[0].sourceId)
      } else {
        setUsingServerData(false)
      }
    } catch {
      setUsingServerData(false)
    } finally {
      setIsLoading(false)
    }
  }

  async function hydrateUser() {
    if (!localStorage.getItem(tokenKey)) return
    const body = await api('/user/me')
    if (body.success) {
      setUser(body.data)
      loadProfile()
      loadHotBlogs(true)
      return
    }
    localStorage.removeItem(tokenKey)
  }

  async function loadProfile() {
    if (!localStorage.getItem(tokenKey)) return
    setProfileLoading(true)
    loadAiMemories()
    loadAiHistoryPreview()
    const body = await api('/user/profile')
    setProfileLoading(false)
    if (body.success) {
      setProfileData(body.data)
    } else if (body.errorMsg) {
      setToast(body.errorMsg)
    }
  }

  async function loadAiMemories() {
    if (!localStorage.getItem(tokenKey)) {
      setAiMemories([])
      return
    }
    setAiMemoryLoading(true)
    const body = await api('/ai/memories')
    setAiMemoryLoading(false)
    if (body.success && Array.isArray(body.data)) {
      setAiMemories(body.data)
    } else if (body.errorMsg && body.errorMsg !== '请先登录') {
      setToast(body.errorMsg)
    }
  }

  async function loadAiHistoryPreview() {
    if (!localStorage.getItem(tokenKey)) {
      setAiHistoryPreview([])
      return
    }
    setAiHistoryPreviewLoading(true)
    try {
      const body = await api('/ai/conversations/recent?limit=6')
      if (body.success && Array.isArray(body.data)) {
        setAiHistoryPreview(body.data
          .filter((item) => item && item.content)
          .map((item, index) => ({
            role: item.role === 'assistant' ? 'assistant' : 'user',
            content: item.content || '',
            generatedAt: item.generatedAt || `preview-${index}`
          })))
      } else if (body.errorMsg && body.errorMsg !== '请先登录') {
        setToast(body.errorMsg)
      }
    } catch {
      setAiHistoryPreview([])
      setToast('AI 对话历史加载失败')
    } finally {
      setAiHistoryPreviewLoading(false)
    }
  }

  async function sendCode() {
    if (!email.trim()) {
      setToast('请输入邮箱')
      return
    }
    setAuthBusy(true)
    const body = await api(`/user/code?${new URLSearchParams({ email: email.trim() })}`, { method: 'POST' })
    setAuthBusy(false)
    if (body.success) {
      setCountdown(60)
      setToast('验证码已发送')
    } else {
      setToast(body.errorMsg || '验证码发送失败')
    }
  }

  async function login(event) {
    event?.preventDefault()
    if (!email.trim() || !code.trim()) {
      setToast('请输入邮箱和验证码')
      return
    }
    setAuthBusy(true)
    const body = await api('/user/login', {
      method: 'POST',
      body: JSON.stringify({ email: email.trim(), code: code.trim() })
    })
    setAuthBusy(false)
    if (!body.success) {
      setToast(body.errorMsg || '登录失败')
      return
    }
    localStorage.setItem(tokenKey, body.data)
    setLoginOpen(false)
    setToast('登录成功')
    hydrateUser()
  }

  function logout() {
    localStorage.removeItem(tokenKey)
    setUser(null)
    setProfileData(null)
    setAiMemories([])
    setAiHistoryPreview([])
    setAiHistoryLoadedFor('')
    setAiChatMessages(initialAiMessages())
    setToast('已退出登录')
  }

  async function loadServerShops(typeId) {
    const params = new URLSearchParams({
      typeId: String(typeId),
      current: '1',
      x: userLocation.x,
      y: userLocation.y
    })
    const body = await api(`/shop/of/type?${params}`)
    if (body.success && Array.isArray(body.data)) {
      setServerBusinesses(body.data.map(normalizeShop))
      setUsingServerData(true)
      setError('')
      return
    }
    setUsingServerData(false)
    setError(body.errorMsg || '')
  }

  async function searchServerShops(keyword) {
    const body = await api(`/shop/search?${new URLSearchParams({ keyword })}`)
    setIsFiltering(false)
    if (body.success && Array.isArray(body.data)) {
      setServerBusinesses(body.data.map(normalizeShop))
      setUsingServerData(true)
      setActiveCategory('all-server')
      setError('')
      setToast(body.data.length > 0 ? `找到 ${body.data.length} 家相关商户` : '没有找到相关商户')
      return
    }
    setError(body.errorMsg || '商户搜索失败')
  }

  async function openBusinessDetail(businessOrId) {
    const id = typeof businessOrId === 'object' ? businessOrId.id : Number(businessOrId)
    const summary = typeof businessOrId === 'object' ? businessOrId : null
    activeDetailIdRef.current = String(id)
    if (summary) setSelectedBusiness(summary)
    setDetailLoading(true)
    setDetailVouchers([])
    setReviewSummary(null)
    setReviewSummaryLoading(true)
    setShopReviews([])
    setReviewPage(1)
    setReviewHasMore(false)
    setReviewDraft(emptyReviewDraft)
    try {
      const [shopBody, voucherBody] = await Promise.all([
        api(`/shop/${id}`),
        api(`/voucher/activities/of/shop?${new URLSearchParams({ shopId: String(id) })}`),
        loadShopReviews(id, 1, true)
      ])
      if (activeDetailIdRef.current !== String(id)) return
      if (shopBody.success && shopBody.data) {
        const normalized = normalizeShop(shopBody.data)
        setSelectedBusiness({ ...normalized, distance: summary?.distance ?? normalized.distance })
      } else if (!summary) {
        const fallback = mockBusinesses.find((item) => Number(item.id) === id)
        if (fallback) setSelectedBusiness(fallback)
      }
      if (voucherBody.success && Array.isArray(voucherBody.data)) setDetailVouchers(voucherBody.data)
    } finally {
      if (activeDetailIdRef.current === String(id)) setDetailLoading(false)
    }
    loadReviewSummary(id)
  }

  async function loadReviewSummary(shopId) {
    const activeId = String(shopId)
    setReviewSummaryLoading(true)
    const body = await api(`/review/summary?${new URLSearchParams({ shopId: activeId })}`)
    if (activeDetailIdRef.current !== activeId) return
    if (body.success && body.data) {
      setReviewSummary(body.data)
    } else {
      setReviewSummary({
        status: 'UNAVAILABLE',
        message: body.errorMsg || 'AI 评论总结暂不可用'
      })
    }
    setReviewSummaryLoading(false)
  }

  async function loadShopReviews(shopId, page = 1, replace = false) {
    if (!shopId || (reviewLoading && !replace)) return
    const activeId = String(shopId)
    setReviewLoading(true)
    const body = await api(`/review/of/shop?${new URLSearchParams({ id: String(shopId), current: String(page) })}`)
    if (activeDetailIdRef.current !== activeId) return
    setReviewLoading(false)
    if (body.success && body.data) {
      const nextList = Array.isArray(body.data.list) ? body.data.list.map(normalizeReview) : []
      setShopReviews((items) => replace ? nextList : mergeById(items, nextList))
      setReviewPage(body.data.current || page)
      setReviewHasMore(Boolean(body.data.hasMore))
    } else if (replace) {
      setShopReviews([])
      setReviewHasMore(false)
    }
  }

  function loadMoreReviews() {
    if (!selectedBusiness || reviewLoading || !reviewHasMore) return
    loadShopReviews(selectedBusiness.id, reviewPage + 1, false)
  }

  async function loadHotBlogs(includeMine = Boolean(localStorage.getItem(tokenKey))) {
    setBlogLoading(true)
    const [body, mineBody] = await Promise.all([
      api('/blog/recent?current=1'),
      includeMine ? api('/blog/of/me?current=1') : Promise.resolve({ success: true, data: [] })
    ])
    setBlogLoading(false)
    const hotBlogs = body.success && Array.isArray(body.data) ? body.data.map(normalizeBlog) : []
    const myBlogs = mineBody.success && Array.isArray(mineBody.data) ? mineBody.data.map(normalizeBlog) : []
    const merged = mergeById(myBlogs, hotBlogs)
    if (merged.length > 0 || body.success || mineBody.success) {
      setBlogs(merged)
    } else {
      setBlogs([])
    }
  }

  async function loadFollowBlogs({ replace = true } = {}) {
    if (!localStorage.getItem(tokenKey)) {
      setBlogs([])
      setFollowFeedCursor({ minTime: 0, offset: 0, hasMore: false })
      setLoginOpen(true)
      setToast('登录后查看关注笔记')
      return
    }
    setBlogLoading(true)
    const params = new URLSearchParams({
      lastId: String(replace ? 0 : followFeedCursor.minTime || 0),
      offset: String(replace ? 0 : followFeedCursor.offset || 0)
    })
    const body = await api(`/blog/of/follow?${params}`)
    setBlogLoading(false)
    if (body.success && body.data) {
      const list = Array.isArray(body.data.list) ? body.data.list.map(normalizeBlog) : []
      setBlogs((items) => replace ? list : mergeById(items, list))
      setFollowFeedCursor({
        minTime: body.data.minTime || 0,
        offset: body.data.offset || 0,
        hasMore: list.length > 0 && Boolean(body.data.minTime)
      })
    } else {
      if (replace) setBlogs([])
      setFollowFeedCursor({ minTime: 0, offset: 0, hasMore: false })
      setToast(body.errorMsg || '关注笔记加载失败')
    }
  }

  function changeBlogMode(nextMode) {
    if (nextMode === blogMode) return
    setBlogMode(nextMode)
    if (nextMode === 'follow') {
      loadFollowBlogs({ replace: true })
      return
    }
    loadHotBlogs()
  }

  function refreshBlogs() {
    if (blogMode === 'follow') {
      loadFollowBlogs({ replace: true })
      return
    }
    loadHotBlogs()
  }

  async function openBlogDetail(blog) {
    setSelectedBlog(blog)
    setBlogDetailLoading(true)
    const body = await api(`/blog/${blog.id}`)
    setBlogDetailLoading(false)
    if (body.success && body.data) {
      setSelectedBlog(normalizeBlog(body.data))
    } else {
      setToast(body.errorMsg || '探店笔记加载失败')
    }
  }

  async function likeBlog(blogId) {
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能点赞')
      return
    }
    const body = await api(`/blog/like/${blogId}`, { method: 'PUT' })
    if (body.success) {
      setToast('已更新点赞')
      refreshBlogs()
    } else {
      setToast(body.errorMsg || '点赞失败')
    }
  }

  async function toggleFollowBlogAuthor(blog) {
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能关注博主')
      return
    }
    if (!blog.userId || String(blog.userId) === String(user.id)) {
      setToast('不能关注自己')
      return
    }
    const nextFollow = !blog.isFollow
    const body = await api(`/follow/${blog.userId}/${nextFollow}`, { method: 'PUT' })
    if (body.success) {
      setToast(nextFollow ? '已关注博主' : '已取消关注')
      updateBlogFollowState(blog.userId, nextFollow)
      if (blogMode === 'follow' && !nextFollow) {
        setBlogs((items) => items.filter((item) => String(item.userId) !== String(blog.userId)))
      }
      loadProfile()
    } else {
      setToast(body.errorMsg || '关注操作失败')
    }
  }

  function updateBlogFollowState(userId, isFollow) {
    setBlogs((items) => items.map((item) => String(item.userId) === String(userId) ? { ...item, isFollow } : item))
    setSelectedBlog((item) => item && String(item.userId) === String(userId) ? { ...item, isFollow } : item)
  }

  async function publishBlog(event) {
    event?.preventDefault()
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能发布探店笔记')
      return
    }
    const shopId = blogDraft.shopId
    if (!shopId || !blogDraft.title.trim() || !blogDraft.content.trim()) {
      setToast('请先搜索并确认店铺，再填写标题和正文')
      return
    }
    setBlogPublishing(true)
    const body = await api('/blog', {
      method: 'POST',
      body: JSON.stringify({
        shopId: String(shopId),
        title: blogDraft.title.trim(),
        images: blogDraft.images.trim(),
        content: blogDraft.content.trim()
      })
    })
    setBlogPublishing(false)
    if (body.success) {
      setToast('探店笔记已发布')
      setBlogDraft(emptyBlogDraft)
      setBlogShopKeyword('')
      setBlogShopResults([])
      setBlogComposerOpen(false)
      setBlogMode('discover')
      if (body.data) {
        const detailBody = await api(`/blog/${body.data}`)
        if (detailBody.success && detailBody.data) {
          const createdBlog = normalizeBlog(detailBody.data)
          setBlogs((items) => [createdBlog, ...items.filter((item) => String(item.id) !== String(createdBlog.id))])
        } else {
          loadHotBlogs()
        }
      } else {
        loadHotBlogs()
      }
      loadProfile()
    } else {
      setToast(body.errorMsg || '发布失败')
    }
  }

  async function searchBlogShops(event) {
    event?.preventDefault()
    const keyword = blogShopKeyword.trim()
    if (!keyword) {
      setToast('请输入店铺名或关键词')
      return
    }
    setBlogShopSearching(true)
    const body = await api(`/shop/search?${new URLSearchParams({ keyword })}`)
    setBlogShopSearching(false)
    if (body.success && Array.isArray(body.data)) {
      const results = body.data.map(normalizeShop)
      setBlogShopResults(results)
      setToast(results.length > 0 ? `找到 ${results.length} 家店铺` : '没有找到匹配店铺')
    } else {
      setBlogShopResults([])
      setToast(body.errorMsg || '店铺搜索失败')
    }
  }

  function confirmBlogShop(shop) {
    setBlogDraft((current) => ({
      ...current,
      shopId: shop.id,
      shopName: shop.name
    }))
    setBlogShopKeyword(shop.name)
    setBlogShopResults([])
    setToast(`已选择：${shop.name}`)
  }

  function changeBlogShopKeyword(value) {
    setBlogShopKeyword(value)
    setBlogDraft((current) => current.shopName && value !== current.shopName
      ? { ...current, shopId: '', shopName: '' }
      : current)
  }

  async function uploadImages(files, endpoint) {
    const selected = Array.from(files || []).filter((file) => file.type.startsWith('image/')).slice(0, 9)
    if (selected.length === 0) return []
    const urls = []
    for (const file of selected) {
      const formData = new FormData()
      formData.append('file', file)
      const body = await api(endpoint, {
        method: 'POST',
        body: formData
      })
      if (!body.success || !body.data?.url) {
        throw new Error(body.errorMsg || `${file.name} 上传失败`)
      }
      urls.push(body.data.url)
    }
    return urls
  }

  async function uploadBlogImages(event) {
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能上传图片')
      return
    }
    setBlogImageUploading(true)
    try {
      const urls = await uploadImages(event.target.files, '/upload/blog')
      if (urls.length > 0) {
        setBlogDraft((current) => ({
          ...current,
          images: mergeImageCsv(current.images, urls).join(',')
        }))
        setToast(`已上传 ${urls.length} 张图片`)
      }
    } catch (error) {
      setToast(error.message || '图片上传失败')
    } finally {
      setBlogImageUploading(false)
      event.target.value = ''
    }
  }

  async function uploadReviewImages(event) {
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能上传图片')
      return
    }
    setReviewImageUploading(true)
    try {
      const urls = await uploadImages(event.target.files, '/upload/file?directory=review')
      if (urls.length > 0) {
        setReviewDraft((current) => ({
          ...current,
          images: mergeImageList(current.images, urls)
        }))
        setToast(`已上传 ${urls.length} 张图片`)
      }
    } catch (error) {
      setToast(error.message || '图片上传失败')
    } finally {
      setReviewImageUploading(false)
      event.target.value = ''
    }
  }

  async function publishReview(event) {
    event?.preventDefault()
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能发布评价')
      return
    }
    if (!selectedBusiness?.id) {
      setToast('请先打开店铺详情')
      return
    }
    if (!reviewDraft.content.trim()) {
      setToast('请填写评价内容')
      return
    }
    setReviewPublishing(true)
    const body = await api('/review', {
      method: 'POST',
      body: JSON.stringify({
        shopId: String(selectedBusiness.id),
        score: Number(reviewDraft.score),
        content: reviewDraft.content.trim(),
        images: reviewDraft.images
      })
    })
    setReviewPublishing(false)
    if (body.success) {
      setToast('评价已发布')
      setReviewDraft(emptyReviewDraft)
      await loadShopReviews(selectedBusiness.id, 1, true)
      loadProfile()
      loadReviewSummary(selectedBusiness.id)
    } else {
      setToast(body.errorMsg || '评价发布失败')
    }
  }

  async function loadVoucherActivities() {
    setVoucherLoading(true)
    const body = await api('/voucher/activities')
    setVoucherLoading(false)
    if (body.success && Array.isArray(body.data)) {
      setVoucherActivities(body.data.map(normalizeVoucher))
    } else {
      setVoucherActivities([])
    }
  }

  async function claimVoucher(voucher) {
    if (!user) {
      setLoginOpen(true)
      setToast('登录后才能参与活动')
      return
    }
    setVoucherBusyId(voucher.id)
    const path = voucher.type === 1 ? `/voucher-order/seckill/${voucher.id}` : `/voucher-order/${voucher.id}`
    const body = await api(path, { method: 'POST' })
    setVoucherBusyId(null)
    if (body.success) {
      setToast(voucher.type === 1 ? `秒杀已受理，订单 ${body.data}` : `领取成功，订单 ${body.data}`)
      loadVoucherActivities()
    } else {
      setToast(body.errorMsg || '参与失败')
    }
  }

  async function signToday() {
    if (!user) {
      setLoginOpen(true)
      return
    }
    const body = await api('/user/sign', { method: 'POST' })
    if (body.success) {
      setToast('签到成功')
      loadProfile()
    } else {
      setToast(body.errorMsg || '签到失败')
    }
  }

  async function deleteBlog(blogId) {
    if (!window.confirm('确定删除这篇探店笔记吗？删除后不可恢复。')) return
    const body = await api(`/blog/${blogId}`, { method: 'DELETE' })
    if (body.success) {
      setToast('笔记已删除')
      setBlogs((items) => items.filter((item) => String(item.id) !== String(blogId)))
      loadProfile()
    } else {
      setToast(body.errorMsg || '删除笔记失败')
    }
  }

  async function deleteReview(reviewId) {
    if (!window.confirm('确定删除这条评价吗？删除后不可恢复。')) return
    const body = await api(`/review/${reviewId}`, { method: 'DELETE' })
    if (body.success) {
      setToast('评价已删除')
      loadProfile()
    } else {
      setToast(body.errorMsg || '删除评价失败')
    }
  }

  async function deleteAiMemory(memoryKey) {
    if (!memoryKey) return
    if (!window.confirm('确定删除这条 AI 偏好吗？删除后 AI 将不再主动参考它。')) return
    const previous = aiMemories
    setAiMemoryDeletingKey(memoryKey)
    setAiMemories((items) => items.filter((item) => item.memoryKey !== memoryKey))
    const body = await api(`/ai/memories/${encodeURIComponent(memoryKey)}`, { method: 'DELETE' })
    setAiMemoryDeletingKey('')
    if (body.success) {
      setToast('AI 偏好已删除')
    } else {
      setAiMemories(previous)
      setToast(body.errorMsg || '删除 AI 偏好失败')
    }
  }

  async function clearAiMemories() {
    if (!aiMemories.length || aiMemoryClearing) return
    if (!window.confirm('确定清除全部 AI 记忆吗？清除后，AI 将不再参考已记录的偏好和历史摘要进行推荐。')) return
    const previous = aiMemories
    setAiMemoryClearing(true)
    setAiMemories([])
    try {
      const body = await api('/ai/memories', { method: 'DELETE' })
      if (body.success) {
        setToast('AI 记忆已清除')
      } else {
        setAiMemories(previous)
        setToast(body.errorMsg || '清除 AI 记忆失败')
      }
    } catch {
      setAiMemories(previous)
      setToast('清除 AI 记忆失败')
    } finally {
      setAiMemoryClearing(false)
    }
  }

  function changeFilter(nextFilter) {
    setActiveFilter(nextFilter)
    setIsFiltering(true)
    window.setTimeout(() => setIsFiltering(false), 280)
  }

  function runSearch(event) {
    event?.preventDefault()
    const value = query.trim()
    setIsFiltering(true)
    if (value) {
      searchServerShops(value)
      setActiveView('home')
      return
    }
    setError('')
    window.setTimeout(() => setIsFiltering(false), 320)
  }

  async function loadAiChatHistory(historyKey) {
    setAiHistoryLoading(true)
    try {
      if (user?.id) {
        const body = await api('/ai/conversations/recent?limit=20')
        if (body.success && Array.isArray(body.data) && body.data.length > 0) {
          setAiChatMessages([
            ...initialAiMessages(),
            ...body.data.map((item, index) => normalizeAiHistoryMessage(item, `history-${index}`))
              .filter((item) => item.content)
          ])
        } else {
          setAiChatMessages(initialAiMessages())
          if (body.errorMsg && body.errorMsg !== '请先登录') setToast(body.errorMsg)
        }
        setAiHistoryLoadedFor(historyKey)
        return
      }

      const stored = JSON.parse(localStorage.getItem(aiGuestHistoryKey) || '[]')
      if (Array.isArray(stored) && stored.length > 0) {
        setAiChatMessages([
          ...initialAiMessages(),
          ...stored.slice(-20)
            .filter((item) => item && item.content)
            .map((item, index) => normalizeAiHistoryMessage(item, `guest-history-${index}`))
        ])
      } else {
        setAiChatMessages(initialAiMessages())
      }
    } catch {
      if (!user?.id) localStorage.removeItem(aiGuestHistoryKey)
      setAiChatMessages(initialAiMessages())
      setToast('AI 历史同步失败')
    } finally {
      setAiHistoryLoadedFor(historyKey)
      setAiHistoryLoading(false)
    }
  }

  async function clearAiChatHistory() {
    if (aiHistoryClearing) return
    if (!window.confirm('确定清空当前 AI 对话历史吗？')) return
    setAiHistoryClearing(true)
    try {
      if (user?.id) {
        const body = await api('/ai/conversations', { method: 'DELETE' })
        if (!body.success) {
          setToast(body.errorMsg || '清空 AI 对话失败')
          return
        }
        setAiHistoryLoadedFor(`user:${user.id}`)
        setAiHistoryPreview([])
      } else {
        localStorage.removeItem(aiGuestHistoryKey)
        setAiHistoryLoadedFor('guest')
      }
      setAiChatMessages(initialAiMessages())
      setToast('AI 对话历史已清空')
    } catch {
      setToast('清空 AI 对话失败')
    } finally {
      setAiHistoryClearing(false)
    }
  }

  function persistGuestAiMessages(messages) {
    if (user?.id) return
    const compact = messages
      .filter((item) => item && (item.role === 'user' || item.role === 'assistant') && item.content)
      .filter((item) => item.content !== aiGreeting)
      .slice(-20)
      .map(({ role, content, generatedAt, usedTools }) => ({
        role,
        content,
        generatedAt: generatedAt || new Date().toISOString(),
        usedTools: Array.isArray(usedTools) ? usedTools : []
      }))
    localStorage.setItem(aiGuestHistoryKey, JSON.stringify(compact))
  }

  function normalizeAiHistoryMessage(item, fallbackGeneratedAt) {
    const role = item?.role === 'assistant' ? 'assistant' : 'user'
    const content = item?.content || ''
    const explicitTools = Array.isArray(item?.usedTools) ? item.usedTools : []
    const confirmRequest = role === 'assistant' ? extractToolConfirmation(content, explicitTools) : null
    return {
      id: createMessageId(),
      role,
      content,
      generatedAt: item?.generatedAt || fallbackGeneratedAt || new Date().toISOString(),
      usedTools: role === 'assistant'
        ? mergeToolNames(explicitTools, inferUsedToolsFromContent(content), confirmRequest?.toolName ? [confirmRequest.toolName] : [])
        : [],
      confirmRequest
    }
  }

  function inferUsedToolsFromContent(content) {
    if (!content || !/spotai:\/\/shop\/\d+/.test(content)) return []
    return ['recommendShops']
  }

  function mergeToolNames(...groups) {
    return [...new Set(groups.flat().filter(Boolean))]
  }

  function extractToolConfirmation(content, usedTools = []) {
    const text = String(content || '')
    if (!text.includes('CONFIRM_REQUIRED')) return null
    const parsed = parseFirstJsonObject(text)
    const confirmToken = parsed?.confirmToken || findJsonStringField(text, 'confirmToken')
    if (!confirmToken) return null
    const toolName = parsed?.toolName || findJsonStringField(text, 'toolName') || usedTools.find((item) => item === 'claimCoupon') || 'unknown'
    return {
      toolName,
      confirmToken,
      status: 'pending',
      resultText: ''
    }
  }

  function normalizeToolConfirmation(raw) {
    if (!raw?.confirmToken) return null
    return {
      toolName: raw.toolName || 'unknown',
      confirmToken: raw.confirmToken,
      title: raw.title || '',
      description: raw.description || '',
      status: 'pending',
      resultText: ''
    }
  }

  function parseFirstJsonObject(text) {
    const raw = String(text || '').replace(/^```(?:json)?/i, '').replace(/```$/i, '').trim()
    const start = raw.indexOf('{')
    const end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    try {
      return JSON.parse(raw.slice(start, end + 1))
    } catch {
      return null
    }
  }

  function findJsonStringField(text, field) {
    const match = String(text || '').match(new RegExp(`"${field}"\\s*:\\s*"([^"]+)"`))
    return match?.[1] || ''
  }

  function isRawConfirmPayload(content) {
    const text = String(content || '').trim()
    if (!text.includes('CONFIRM_REQUIRED')) return false
    const parsed = parseFirstJsonObject(text)
    if (!parsed || parsed.status !== 'CONFIRM_REQUIRED') return false
    return text.replace(/^```(?:json)?/i, '').replace(/```$/i, '').trim().startsWith('{')
  }

  async function sendAiChatMessage(event, overrideMessage) {
    event?.preventDefault()
    const message = (overrideMessage || aiChatInput).trim()
    if (!message || aiChatLoading) return
    const userMessage = { id: createMessageId(), role: 'user', content: message, generatedAt: new Date().toISOString() }
    setAiChatMessages((items) => {
      const next = [...items, userMessage]
      persistGuestAiMessages(next)
      return next
    })
    setAiChatInput('')
    setAiChatOpen(true)
    setAiChatLoading(true)
    const history = aiChatMessages.slice(-8).map(({ role, content }) => ({ role, content }))
    const controller = new AbortController()
    const timer = window.setTimeout(() => controller.abort(), aiChatTimeoutMs)
    let body
    try {
      body = await api('/ai/chat', {
        method: 'POST',
        signal: controller.signal,
        body: JSON.stringify({
          route: 'CHAT',
          shopId: selectedBusiness?.id || null,
          message,
          history
        })
      })
    } catch (error) {
      body = {
        success: false,
        errorMsg: error?.name === 'AbortError'
          ? 'AI 响应超时，请稍后再试，或把问题说得更具体一点。'
          : 'AI 请求失败，请检查后端服务是否正常。'
      }
    } finally {
      window.clearTimeout(timer)
      setAiChatLoading(false)
    }
    const answer = body.success && body.data?.answer
      ? body.data.answer
      : body.errorMsg || 'AI 助手暂不可用，请稍后再试。'
    const explicitTools = Array.isArray(body.data?.usedTools) ? body.data.usedTools : []
    const confirmRequest = normalizeToolConfirmation(body.data?.toolConfirmation)
      || extractToolConfirmation(answer, explicitTools)
    const assistantMessage = {
        id: createMessageId(),
        role: 'assistant',
        content: answer,
        generatedAt: body.data?.generatedAt || new Date().toISOString(),
        agentRoute: body.data?.agentRoute,
        memoryUpdated: Boolean(body.data?.memoryUpdated),
        usedTools: mergeToolNames(explicitTools, confirmRequest?.toolName ? [confirmRequest.toolName] : []),
        confirmRequest,
        error: !body.success
      }
    setAiChatMessages((items) => {
      const next = [...items, assistantMessage]
      persistGuestAiMessages(next)
      return next
    })
    if (body.success && body.data?.memoryUpdated && user?.id) {
      loadAiMemories()
    }
    if (body.success && user?.id) {
      loadAiHistoryPreview()
    }
  }

  async function confirmAiTool(messageId, confirmed) {
    const target = aiChatMessages.find((message) => message.id === messageId)
    const confirmRequest = target?.confirmRequest
    if (!confirmRequest?.confirmToken || confirmRequest.status === 'processing') return
    if (!user?.id && !localStorage.getItem(tokenKey)) {
      setLoginOpen(true)
      setToast('登录后才能确认 AI 代操作')
      return
    }
    setAiChatMessages((items) => items.map((message) => message.id === messageId
      ? {
        ...message,
        confirmRequest: {
          ...message.confirmRequest,
          status: 'processing',
          resultText: confirmed ? '正在提交确认...' : '正在取消...'
        }
      }
      : message))
    const body = await api('/ai/tool/confirm', {
      method: 'POST',
      body: JSON.stringify({
        confirmToken: confirmRequest.confirmToken,
        confirmed
      })
    })
    const result = formatToolConfirmResult(body)
    setAiChatMessages((items) => {
      const next = items.map((message) => message.id === messageId
        ? {
          ...message,
          confirmRequest: {
            ...message.confirmRequest,
            status: result.status,
            resultText: result.text
          }
        }
        : message)
      persistGuestAiMessages(next)
      return next
    })
    setToast(result.text)
    if (result.status === 'confirmed') {
      loadProfile()
    }
  }

  function formatToolConfirmResult(body) {
    if (!body?.success) {
      return { status: 'error', text: body?.errorMsg || '确认失败，请稍后再试' }
    }
    const payload = typeof body.data === 'string' ? parseFirstJsonObject(body.data) : body.data
    if (payload?.status === 'SUCCESS') {
      return { status: 'confirmed', text: payload.orderId ? `领取成功，订单 ${payload.orderId}` : '操作成功' }
    }
    if (payload?.status === 'REJECTED') {
      return { status: 'rejected', text: '已取消本次操作' }
    }
    if (payload?.message) {
      return { status: 'error', text: repairText(payload.message) }
    }
    return { status: 'error', text: '确认失败，请稍后再试' }
  }

  const dataSource = usingServerData ? serverBusinesses : mockBusinesses
  const visibleBusinesses = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    const filtered = dataSource.filter((item) => {
      const categoryMatched = usingServerData
        || activeCategory === 'food'
        || activeCategory === 'all-server'
        || item.categoryId === activeCategory
      const districtMatched = activeDistrict === '全部' || item.district === activeDistrict
      const keywordMatched = !keyword || [
        item.name,
        item.category,
        item.district,
        item.address,
        item.summary,
        ...item.tags,
        ...item.deals
      ].join(' ').toLowerCase().includes(keyword)
      return categoryMatched && districtMatched && keywordMatched
    })
    return (filterStrategies[activeFilter] || filterStrategies.nearby)(filtered)
  }, [activeCategory, activeDistrict, activeFilter, dataSource, query, usingServerData])

  return (
    <div className="app-shell">
      <Header
        city={city}
        setCity={setCity}
        query={query}
        setQuery={setQuery}
        onSearch={runSearch}
        activeView={activeView}
        setActiveView={setActiveView}
        user={user}
        onLogin={() => setLoginOpen(true)}
        onLogout={logout}
      />
      <main className="page-enter">
        {activeView === 'home' && (
          <HomePage
            query={query}
            setQuery={setQuery}
            categoryOptions={categoryOptions}
            activeCategory={activeCategory}
            setActiveCategory={setActiveCategory}
            loadServerShops={loadServerShops}
            changeFilter={changeFilter}
            visibleBusinesses={visibleBusinesses}
            isLoading={isLoading || isFiltering}
            error={error}
            activeFilter={activeFilter}
            activeDistrict={activeDistrict}
            setActiveDistrict={setActiveDistrict}
            onOpenBusiness={openBusinessDetail}
            rankings={rankings}
            dataSource={dataSource}
          />
        )}
        {activeView === 'blogs' && (
          <BlogPage
            blogs={blogs}
            loading={blogLoading}
            activeMode={blogMode}
            onModeChange={changeBlogMode}
            onRefresh={refreshBlogs}
            composerOpen={blogComposerOpen}
            setComposerOpen={setBlogComposerOpen}
            onOpen={openBlogDetail}
            onLike={likeBlog}
            onFollow={toggleFollowBlogAuthor}
            onPublish={publishBlog}
            draft={blogDraft}
            setDraft={setBlogDraft}
            publishing={blogPublishing}
            shops={dataSource}
            user={user}
            onLogin={() => setLoginOpen(true)}
            onUploadImages={uploadBlogImages}
            imageUploading={blogImageUploading}
            shopKeyword={blogShopKeyword}
            setShopKeyword={changeBlogShopKeyword}
            shopResults={blogShopResults}
            shopSearching={blogShopSearching}
            onSearchShops={searchBlogShops}
            onConfirmShop={confirmBlogShop}
            followHasMore={followFeedCursor.hasMore}
            onLoadMoreFollow={() => loadFollowBlogs({ replace: false })}
          />
        )}
        {activeView === 'deals' && (
          <DealPage
            activities={voucherActivities}
            loading={voucherLoading}
            busyId={voucherBusyId}
            onRefresh={loadVoucherActivities}
            onClaim={claimVoucher}
            onOpenShop={openBusinessDetail}
          />
        )}
        {activeView === 'profile' && (
          <ProfilePage
            user={user}
            data={profileData}
            loading={profileLoading}
            aiMemories={aiMemories}
            aiMemoryLoading={aiMemoryLoading}
            aiMemoryDeletingKey={aiMemoryDeletingKey}
            aiMemoryClearing={aiMemoryClearing}
            aiHistoryPreview={aiHistoryPreview}
            aiHistoryPreviewLoading={aiHistoryPreviewLoading}
            aiHistoryClearing={aiHistoryClearing}
            onLogin={() => setLoginOpen(true)}
            onRefresh={loadProfile}
            onSign={signToday}
            onLogout={logout}
            onOpenBlog={openBlogDetail}
            onDeleteBlog={deleteBlog}
            onDeleteReview={deleteReview}
            onDeleteAiMemory={deleteAiMemory}
            onClearAiMemories={clearAiMemories}
            onClearAiHistory={clearAiChatHistory}
          />
        )}
      </main>
      <BusinessDetail
        business={selectedBusiness}
        vouchers={detailVouchers}
        reviewSummary={reviewSummary}
        loading={detailLoading}
        onClose={() => {
          activeDetailIdRef.current = ''
          setSelectedBusiness(null)
          setReviewSummaryLoading(false)
        }}
        voucherBusyId={voucherBusyId}
        onClaimVoucher={claimVoucher}
        onAskAiReview={() => sendAiChatMessage(null, '请分析这家店的评价，说明优点、槽点和适合人群')}
        reviews={shopReviews}
        reviewLoading={reviewLoading}
        reviewHasMore={reviewHasMore}
        onLoadMoreReviews={loadMoreReviews}
        reviewSummaryLoading={reviewSummaryLoading}
        user={user}
        onLogin={() => setLoginOpen(true)}
        reviewDraft={reviewDraft}
        setReviewDraft={setReviewDraft}
        onPublishReview={publishReview}
        onUploadReviewImages={uploadReviewImages}
        reviewPublishing={reviewPublishing}
        reviewImageUploading={reviewImageUploading}
      />
      <BlogDialog blog={selectedBlog} loading={blogDetailLoading} onClose={() => setSelectedBlog(null)} onLike={likeBlog} />
      <LoginDialog
        open={loginOpen}
        email={email}
        code={code}
        countdown={countdown}
        busy={authBusy}
        setEmail={setEmail}
        setCode={setCode}
        onSendCode={sendCode}
        onLogin={login}
        onClose={() => setLoginOpen(false)}
      />
      <AiChatWidget
        open={aiChatOpen}
        onToggle={() => setAiChatOpen((value) => !value)}
        messages={aiChatMessages}
        input={aiChatInput}
        setInput={setAiChatInput}
        loading={aiChatLoading}
        historyLoading={aiHistoryLoading}
        selectedBusiness={selectedBusiness}
        onOpenBusiness={openBusinessDetail}
        onSubmit={sendAiChatMessage}
        onConfirmTool={confirmAiTool}
        onClearHistory={clearAiChatHistory}
      />
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  )
}

function Header({ city, setCity, query, setQuery, onSearch, activeView, setActiveView, user, onLogin, onLogout }) {
  return (
    <header className="site-header">
      <button type="button" className="brand" onClick={() => setActiveView('home')} aria-label="Spot Life 首页">
        <span className="brand-logo">S</span>
        <span>
          <strong>Spot Life</strong>
          <small>发现附近好店</small>
        </span>
      </button>
      <span className="city-picker-static"><MapPinned size={18} /> 西安</span>
      <form className="search-bar" onSubmit={onSearch}>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索美食、商圈、店名"
          aria-label="搜索美食、商圈、店名"
        />
        <button type="submit" aria-label="搜索">搜索</button>
      </form>
      <nav className="header-actions" aria-label="主导航">
        {navItems.map((item) => (
          <button
            type="button"
            key={item.id}
            className={activeView === item.id ? 'nav-active' : ''}
            onClick={() => setActiveView(item.id)}
          >
            {item.label}
          </button>
        ))}
        {user
          ? <button type="button" className="user-entry" onClick={onLogout}>退出</button>
          : <button type="button" className="user-entry" onClick={onLogin}>登录</button>}
      </nav>
    </header>
  )
}

function HomePage({
  setQuery,
  categoryOptions,
  activeCategory,
  setActiveCategory,
  loadServerShops,
  changeFilter,
  visibleBusinesses,
  isLoading,
  error,
  activeFilter,
  activeDistrict,
  setActiveDistrict,
  onOpenBusiness,
  rankings,
  dataSource
}) {
  return (
    <>
      <HeroSection onQuickSearch={(value) => {
        setQuery(value)
        changeFilter('deal')
      }} />
      <CategoryNav
        categories={categoryOptions}
        activeCategory={activeCategory}
        onSelect={(id) => {
          setActiveCategory(id)
          const selected = categoryOptions.find((item) => item.id === id)
          if (selected?.sourceId) loadServerShops(selected.sourceId)
          changeFilter('nearby')
        }}
      />
      <section className="content-layout" aria-label="商家浏览区">
        <div className="main-column">
          <FilterBar
            filters={filters}
            districts={['全部', ...districts]}
            activeFilter={activeFilter}
            activeDistrict={activeDistrict}
            onFilter={changeFilter}
            onDistrict={(district) => setActiveDistrict(district)}
            resultCount={visibleBusinesses.length}
          />
          <BusinessList
            businesses={visibleBusinesses}
            loading={isLoading}
            error={error}
            onRetry={() => window.location.reload()}
            onOpen={onOpenBusiness}
            onClearSearch={() => setQuery('')}
          />
        </div>
        <aside className="side-column" aria-label="热门榜单">
          <RankingSection rankings={rankings} onOpen={(name) => {
            const target = [...dataSource, ...mockBusinesses].find((item) => item.name === name)
            if (target) onOpenBusiness(target)
          }} />
        </aside>
      </section>
    </>
  )
}

function HeroSection({ onQuickSearch }) {
  return (
    <section className="hero-section">
      <div className="hero-copy">
        <p className="eyebrow">西安本地精选</p>
        <h1>发现附近值得去的好店</h1>
        <p>从评分、距离、优惠和真实评价里快速判断今天去哪吃、去哪玩。</p>
        <div className="hero-quick-actions">
          {['火锅优惠', '钟楼夜宵', '高新咖啡'].map((item) => (
            <button key={item} type="button" onClick={() => onQuickSearch(item)}>{item}</button>
          ))}
        </div>
      </div>
      <div className="hero-stack" aria-hidden="true">
        <article className="deal-ticket">
          <span>今日专享</span>
          <strong>附近好店券</strong>
          <small>最高立减 30 元</small>
        </article>
        <article className="floating-shop-card">
          <img src={mockBusinesses[0].image} alt="" />
          <div>
            <strong>{mockBusinesses[0].name}</strong>
            <span>4.8 分 · 小寨 · 人均 ¥88</span>
          </div>
        </article>
      </div>
    </section>
  )
}

function CategoryNav({ categories, activeCategory, onSelect }) {
  return (
    <section className="category-section" aria-labelledby="category-title">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">分类导航</p>
          <h2 id="category-title">按场景快速进入</h2>
        </div>
      </div>
      <div className="category-scroll" role="list">
        {categories.map((item) => {
          const Icon = categoryIconMap[item.iconKey || categoryIconKey(item.name)] || categoryIconMap.default
          return (
            <button
              key={item.id}
              type="button"
              className={activeCategory === item.id ? 'category-item active' : 'category-item'}
              onClick={() => onSelect(item.id)}
              aria-pressed={activeCategory === item.id}
            >
              <span className="category-icon" aria-hidden="true">
                <Icon size={22} strokeWidth={2.25} />
              </span>
              <strong>{item.name}</strong>
            </button>
          )
        })}
      </div>
    </section>
  )
}

function FilterBar({ filters, districts, activeFilter, activeDistrict, onFilter, onDistrict, resultCount }) {
  return (
    <section className="filter-panel" aria-label="筛选排序">
      <div className="filter-head">
        <div>
          <h2>推荐商家</h2>
          <p>共找到 {resultCount} 家可比较商家</p>
        </div>
        <label>
          商圈
          <select value={activeDistrict} onChange={(event) => onDistrict(event.target.value)}>
            {districts.map((district) => <option key={district}>{district}</option>)}
          </select>
        </label>
      </div>
      <div className="filter-chips" role="list">
        {filters.map((item) => (
          <button
            key={item.id}
            type="button"
            className={activeFilter === item.id ? 'filter-chip active' : 'filter-chip'}
            onClick={() => onFilter(item.id)}
            aria-pressed={activeFilter === item.id}
          >
            {item.label}
          </button>
        ))}
      </div>
    </section>
  )
}

function BusinessList({ businesses, loading, error, onRetry, onOpen, onClearSearch }) {
  if (loading) return <LoadingSkeleton />
  if (error) return <ErrorState message={error} onRetry={onRetry} />
  if (businesses.length === 0) {
    return (
      <EmptyState
        title="没有找到匹配商家"
        text="换个关键词、商圈或分类试试，也可以清空筛选重新浏览附近好店。"
        action="清空筛选"
        onAction={onClearSearch}
      />
    )
  }
  return (
    <section className="business-list" aria-label="推荐商家列表">
      {businesses.map((business, index) => (
        <BusinessCard key={business.id} business={business} index={index} onOpen={() => onOpen(business)} />
      ))}
    </section>
  )
}

function BusinessCard({ business, index, onOpen }) {
  return (
    <article className="business-card" style={{ animationDelay: `${index * 45}ms` }}>
      <button className="image-button" type="button" onClick={onOpen} aria-label={`查看${business.name}详情`}>
        <img src={business.image} alt={business.name} />
        <span>{business.reason}</span>
      </button>
      <div className="business-body">
        <div className="business-title-row">
          <div>
            <p className="business-meta">{business.category} · {business.district}</p>
            <h3>{business.name}</h3>
          </div>
          <button type="button" className="detail-button" onClick={onOpen}>看详情</button>
        </div>
        <div className="decision-row">
          <span className="rating">{stars(business.rating)} <strong>{business.rating}</strong></span>
          <span>{business.reviews.toLocaleString()} 条评价</span>
          <span>人均 ¥{business.avgPrice}</span>
          <span>{business.distance}km</span>
        </div>
        <p className="address-line">{business.address}</p>
        <p className="summary-line">{business.summary}</p>
        <div className="deal-row">
          {business.deals.slice(0, 2).map((deal) => <span key={deal}>{deal}</span>)}
        </div>
        <div className="tag-row">
          {business.tags.slice(0, 3).map((tag) => <small key={tag}>{repairText(tag)}</small>)}
        </div>
      </div>
    </article>
  )
}

function RankingSection({ rankings, onOpen }) {
  return (
    <section className="ranking-section">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">热门榜单</p>
          <h2>大家正在看</h2>
        </div>
      </div>
      <div className="ranking-grid">
        {rankings.map((ranking) => (
          <article className="ranking-card" key={ranking.id}>
            <h3>{ranking.title}</h3>
            <ol>
              {ranking.items.map((item) => (
                <li key={`${ranking.id}-${item.name}`}>
                  <button type="button" onClick={() => onOpen(item.name)}>
                    <span className={item.rank === 1 ? 'rank top' : 'rank'}>{item.rank}</span>
                    <strong>{item.name}</strong>
                    <small>{item.score} 分 · {item.reason}</small>
                    <em>{item.tag}</em>
                  </button>
                </li>
              ))}
            </ol>
          </article>
        ))}
      </div>
    </section>
  )
}

function BusinessDetail({
  business,
  vouchers,
  reviewSummary,
  loading,
  onClose,
  voucherBusyId,
  onClaimVoucher,
  onAskAiReview,
  reviews,
  reviewLoading,
  reviewHasMore,
  onLoadMoreReviews,
  reviewSummaryLoading,
  user,
  onLogin,
  reviewDraft,
  setReviewDraft,
  onPublishReview,
  onUploadReviewImages,
  reviewPublishing,
  reviewImageUploading
}) {
  useEffect(() => {
    if (!business) return undefined
    function closeOnEscape(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [business, onClose])

  if (!business) return null

  function handleScroll(event) {
    const target = event.currentTarget
    if (target.scrollHeight - target.scrollTop - target.clientHeight < 180) {
      onLoadMoreReviews()
    }
  }

  return (
    <div className="drawer-backdrop" role="presentation" onClick={onClose}>
      <aside className="business-drawer" role="dialog" aria-modal="true" aria-labelledby="business-detail-title" onClick={(event) => event.stopPropagation()} onScroll={handleScroll}>
        <button className="drawer-close" type="button" onClick={onClose} aria-label="关闭详情">×</button>
        <img className="drawer-hero" src={business.image} alt={business.name} />
        <div className="drawer-content">
          <p className="business-meta">{business.category} · {business.district}</p>
          <h2 id="business-detail-title">{business.name}</h2>
          <div className="drawer-score">
            <strong>{business.rating}</strong>
            <span>{stars(business.rating)} · {business.reviews.toLocaleString()} 条评价 · 人均 ¥{business.avgPrice}</span>
          </div>
          <dl className="info-list">
            <div><dt>地址</dt><dd>{business.address}</dd></div>
            <div><dt>营业</dt><dd>{business.hours}</dd></div>
            <div><dt>电话</dt><dd>{business.phone}</dd></div>
          </dl>
          <ReviewAiSummary summary={reviewSummary} loading={reviewSummaryLoading} onAskAi={onAskAiReview} />
          <DetailSection title="优惠套餐">
            <div className="drawer-deals">
              {vouchers?.length ? vouchers.map((voucher) => (
                <button type="button" key={voucher.id} onClick={() => onClaimVoucher(voucher)} disabled={voucherBusyId === voucher.id}>
                  {formatVoucher(voucher)}
                  <span>{voucherBusyId === voucher.id ? '处理中' : voucher.type === 1 ? '立即秒杀' : '立即领取'}</span>
                </button>
              )) : business.deals.map((deal) => (
                <button type="button" key={deal}>{deal}<span>{loading ? '加载中' : '暂无可领券'}</span></button>
              ))}
            </div>
          </DetailSection>
          <DetailSection title="推荐菜 / 服务">
            <div className="dish-row">
              {business.dishes.map((dish) => <span key={dish}>{dish}</span>)}
            </div>
          </DetailSection>
          <ReviewComposer
            user={user}
            draft={reviewDraft}
            setDraft={setReviewDraft}
            onSubmit={onPublishReview}
            onUploadImages={onUploadReviewImages}
            publishing={reviewPublishing}
            imageUploading={reviewImageUploading}
            onLogin={onLogin}
          />
          <ReviewList reviews={reviews} loading={reviewLoading} hasMore={reviewHasMore} onLoadMore={onLoadMoreReviews} />
        </div>
      </aside>
    </div>
  )
}

function ReviewComposer({ user, draft, setDraft, onSubmit, onUploadImages, publishing, imageUploading, onLogin }) {
  const updateDraft = (key, value) => setDraft((current) => ({ ...current, [key]: value }))
  const removeImage = (url) => setDraft((current) => ({ ...current, images: current.images.filter((image) => image !== url) }))
  return (
    <DetailSection title="写评价">
      <form className="review-composer" onSubmit={onSubmit}>
        {!user ? (
          <button type="button" className="primary-action" onClick={onLogin}>登录后评价</button>
        ) : (
          <>
            <div className="score-picker" aria-label="评价星级">
              {[1, 2, 3, 4, 5].map((score) => (
                <button
                  type="button"
                  key={score}
                  className={score <= draft.score ? 'active' : ''}
                  onClick={() => updateDraft('score', score)}
                  aria-label={`${score} 星`}
                >
                  ★
                </button>
              ))}
              <span>{draft.score} 星</span>
            </div>
            <textarea
              value={draft.content}
              maxLength={2048}
              onChange={(event) => updateDraft('content', event.target.value)}
              placeholder="分享口味、环境、服务和适合人群"
            />
            <label className="upload-trigger">
              <input type="file" accept="image/*" multiple onChange={onUploadImages} disabled={imageUploading || draft.images.length >= 9} />
              <span>{imageUploading ? '图片上传中...' : '上传评价图片'}</span>
              <small>{draft.images.length}/9</small>
            </label>
            {draft.images.length > 0 && (
              <div className="upload-preview-grid">
                {draft.images.map((image) => (
                  <button type="button" key={image} onClick={() => removeImage(image)} aria-label="移除图片">
                    <img src={image} alt="" />
                  </button>
                ))}
              </div>
            )}
            <button type="submit" className="primary-action" disabled={publishing || imageUploading}>{publishing ? '发布中' : '发布评价'}</button>
          </>
        )}
      </form>
    </DetailSection>
  )
}

function ReviewList({ reviews, loading, hasMore, onLoadMore }) {
  return (
    <DetailSection title="用户评价">
      <div className="review-list" aria-busy={loading}>
        {reviews.length === 0 && !loading ? (
          <div className="review-empty">
            <strong>暂无用户评价</strong>
            <p>这家店还没有可展示的评价。</p>
          </div>
        ) : (
          reviews.map((review) => <ReviewItem key={review.id} review={review} />)
        )}
        {loading && <ReviewSkeleton />}
        {hasMore && !loading && (
          <button type="button" className="load-more-button" onClick={onLoadMore}>查看更多评价</button>
        )}
        {!hasMore && reviews.length > 0 && <p className="review-end">已经到底了</p>}
      </div>
    </DetailSection>
  )
}

function ReviewItem({ review }) {
  return (
    <article className="review-card">
      <div className="review-head">
        <img src={review.userIcon} alt="" />
        <div>
          <strong>{review.userName}</strong>
          <span>{stars(review.score)} · {review.createTime}</span>
        </div>
      </div>
      <p>{review.content || '这位用户暂未填写文字评价。'}</p>
      {review.images.length > 0 && (
        <div className="review-images">
          {review.images.slice(0, 4).map((image, index) => <img key={`${review.id}-${index}`} src={image} alt="" />)}
        </div>
      )}
      <small>赞 {review.liked || 0}</small>
    </article>
  )
}

function ReviewSkeleton() {
  return (
    <div className="review-skeleton" aria-label="评价加载中">
      <span />
      <span />
      <span />
    </div>
  )
}

function ReviewAiSummary({ summary, loading, onAskAi }) {
  const ready = summary?.status === 'READY'
  const positives = summary?.positiveTags || summary?.highlights || []
  const negatives = summary?.negativeTags || summary?.weaknesses || []
  const scenes = summary?.scenes || []
  return (
    <section className="ai-review-card">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">AI 评论分析</p>
          <h3>评价口碑总结</h3>
        </div>
        <button type="button" className="text-action" onClick={onAskAi}>问 AI</button>
      </div>
      {loading ? <p>正在分析评价...</p> : ready ? (
        <>
          <p>{repairText(summary.summary) || '暂无总结文本'}</p>
          <div className="summary-tag-grid">
            {positives.slice(0, 4).map((tag) => <span key={tag}>{repairText(tag)}</span>)}
            {negatives.slice(0, 3).map((tag) => <span key={tag} className="caution">{repairText(tag)}</span>)}
            {scenes.slice(0, 3).map((tag) => <span key={tag} className="scene">{repairText(tag)}</span>)}
          </div>
        </>
      ) : (
        <p>{summary?.message || '该店铺暂未生成 AI 评论总结，可点击“问 AI”结合当前店铺上下文分析。'}</p>
      )}
    </section>
  )
}

function BlogPage({
  blogs,
  loading,
  activeMode,
  onModeChange,
  onRefresh,
  composerOpen,
  setComposerOpen,
  onOpen,
  onLike,
  onFollow,
  onPublish,
  draft,
  setDraft,
  publishing,
  user,
  onLogin,
  onUploadImages,
  imageUploading,
  shopKeyword,
  setShopKeyword,
  shopResults,
  shopSearching,
  onSearchShops,
  onConfirmShop,
  followHasMore,
  onLoadMoreFollow
}) {
  const updateDraft = (key, value) => setDraft((current) => ({ ...current, [key]: value }))
  const draftImages = imageCsvToList(draft.images)
  const removeImage = (url) => setDraft((current) => ({
    ...current,
    images: imageCsvToList(current.images).filter((image) => image !== url).join(',')
  }))
  return (
    <section className="page-panel">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">探店笔记</p>
          <h1>{activeMode === 'follow' ? '关注博主的新笔记' : '真实用户正在分享的店'}</h1>
        </div>
        <div className="blog-toolbar">
          <div className="mode-switch" role="tablist" aria-label="探店笔记视图">
            <button type="button" role="tab" aria-selected={activeMode === 'discover'} className={activeMode === 'discover' ? 'active' : ''} onClick={() => onModeChange('discover')}>发现</button>
            <button type="button" role="tab" aria-selected={activeMode === 'follow'} className={activeMode === 'follow' ? 'active' : ''} onClick={() => onModeChange('follow')}>关注</button>
          </div>
          <button type="button" className="icon-action" onClick={() => setComposerOpen((value) => !value)} aria-expanded={composerOpen} aria-label={composerOpen ? '收起发布笔记' : '发布探店笔记'}>
            {composerOpen ? '×' : '+'}
          </button>
          <button type="button" className="secondary-action" onClick={onRefresh}>刷新</button>
        </div>
      </div>
      <form className={`blog-composer ${composerOpen ? 'open' : ''}`} onSubmit={onPublish} aria-hidden={!composerOpen}>
        <div>
          <p className="eyebrow">发布笔记</p>
          <h2>写一条真实探店体验</h2>
        </div>
        {!user ? (
          <button type="button" className="primary-action" onClick={onLogin}>登录后发布</button>
        ) : (
          <>
            <div className="composer-grid">
              <ShopSearchField
                keyword={shopKeyword}
                setKeyword={setShopKeyword}
                results={shopResults}
                searching={shopSearching}
                selectedName={draft.shopName}
                onSearch={onSearchShops}
                onConfirm={onConfirmShop}
              />
              <label>
                标题
                <input value={draft.title} maxLength={255} onChange={(event) => updateDraft('title', event.target.value)} placeholder="例如：周末值得专程去的一家店" />
              </label>
            </div>
            <label className="upload-trigger">
              <input type="file" accept="image/*" multiple onChange={onUploadImages} disabled={imageUploading || draftImages.length >= 9} />
              <span>{imageUploading ? '图片上传中...' : '上传探店图片'}</span>
              <small>{draftImages.length}/9</small>
            </label>
            {draftImages.length > 0 && (
              <div className="upload-preview-grid">
                {draftImages.map((image) => (
                  <button type="button" key={image} onClick={() => removeImage(image)} aria-label="移除图片">
                    <img src={image} alt="" />
                  </button>
                ))}
              </div>
            )}
            <label>
              正文
              <textarea value={draft.content} maxLength={2048} onChange={(event) => updateDraft('content', event.target.value)} placeholder="说说环境、口味、服务和适合人群" />
            </label>
            <button type="submit" className="primary-action" disabled={publishing}>{publishing ? '发布中' : '发布笔记'}</button>
          </>
        )}
      </form>
      {loading ? <LoadingSkeleton /> : blogs.length === 0 ? (
        <EmptyState
          title={activeMode === 'follow' ? '暂无关注笔记' : '暂无探店笔记'}
          text={activeMode === 'follow' ? '关注一些博主后，这里会展示他们的新笔记。' : '暂时没有可展示的探店笔记。'}
        />
      ) : (
        <div className="blog-grid blog-grid-enter">
          {blogs.map((blog) => <BlogCard key={blog.id} blog={blog} onOpen={() => onOpen(blog)} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog)} />)}
        </div>
      )}
      {activeMode === 'follow' && !loading && followHasMore && (
        <button type="button" className="load-more-button" onClick={onLoadMoreFollow}>查看更多关注笔记</button>
      )}
    </section>
  )
}

function ShopSearchField({ keyword, setKeyword, results, searching, selectedName, onSearch, onConfirm }) {
  return (
    <div className="shop-search-field">
      <label>
        店铺
        <div className="shop-search-row">
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索店铺名、商圈或关键词"
            aria-label="搜索店铺"
          />
          <button type="button" onClick={onSearch} disabled={searching}>{searching ? '搜索中' : '搜索'}</button>
        </div>
      </label>
      {selectedName && <p className="selected-shop-line">已确认：{selectedName}</p>}
      {results.length > 0 && (
        <div className="shop-search-results" role="list">
          {results.map((shop) => (
            <button type="button" key={shop.id} onClick={() => onConfirm(shop)} role="listitem">
              <strong>{shop.name}</strong>
              <span>{shop.category} · {shop.district || '未知商圈'} · 人均 ¥{shop.avgPrice}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function BlogCard({ blog, onOpen, onLike, onDelete, onFollow }) {
  return (
    <article className="blog-card">
      <button type="button" className="blog-cover" onClick={onOpen}>
        {blog.image ? <img src={blog.image} alt={blog.title} /> : <span>{blog.title.slice(0, 2)}</span>}
      </button>
      <div>
        <h3>{blog.title}</h3>
        <p>{blog.excerpt}</p>
        <div className="blog-meta">
          <span>{blog.author}</span>
          <div className="card-actions">
            {onFollow && blog.userId && <button type="button" onClick={onFollow}>{blog.isFollow ? '已关注' : '关注'}</button>}
            <button type="button" onClick={onLike}>赞 {blog.liked || 0}</button>
            {onDelete && <button type="button" className="danger-action" onClick={onDelete}>删除</button>}
          </div>
        </div>
      </div>
    </article>
  )
}

function BlogDialog({ blog, loading, onClose, onLike }) {
  if (!blog) return null
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="blog-dialog" role="dialog" aria-modal="true" aria-labelledby="blog-title" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="drawer-close" onClick={onClose} aria-label="关闭笔记">×</button>
        {loading ? <LoadingSkeleton /> : (
          <>
            {blog.images.length > 0 && (
              <div className="blog-dialog-images">
                {blog.images.map((image, index) => <img className="blog-dialog-image" key={`${blog.id}-${index}`} src={image} alt={index === 0 ? blog.title : ''} />)}
              </div>
            )}
            <p className="eyebrow">{blog.author}</p>
            <h2 id="blog-title">{blog.title}</h2>
            <p className="blog-dialog-content">{blog.fullContent || '这篇笔记暂时没有正文内容。'}</p>
            <button type="button" className="secondary-action" onClick={() => onLike(blog.id)}>点赞 {blog.liked || 0}</button>
          </>
        )}
      </section>
    </div>
  )
}

function DealPage({ activities, loading, busyId, onRefresh, onClaim, onOpenShop }) {
  const active = activities.filter((item) => item.activityStatus === 'ACTIVE')
  const upcoming = activities.filter((item) => item.activityStatus === 'UPCOMING')
  const normal = activities.filter((item) => item.type === 0)
  return (
    <section className="page-panel">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">秒杀活动</p>
          <h1>限时券和代金券</h1>
        </div>
        <button type="button" className="secondary-action" onClick={onRefresh}>刷新</button>
      </div>
      {loading ? <LoadingSkeleton /> : (
        <div className="deal-sections">
          <VoucherSection title="正在秒杀" items={active} busyId={busyId} onClaim={onClaim} onOpenShop={onOpenShop} />
          <VoucherSection title="即将开始" items={upcoming} busyId={busyId} onClaim={onClaim} onOpenShop={onOpenShop} />
          <VoucherSection title="代金券" items={normal} busyId={busyId} onClaim={onClaim} onOpenShop={onOpenShop} />
          {activities.length === 0 && <EmptyState title="暂无活动" text="后端暂无可展示的秒杀或代金券活动。" />}
        </div>
      )}
    </section>
  )
}

function VoucherSection({ title, items, busyId, onClaim, onOpenShop }) {
  if (items.length === 0) return null
  return (
    <section className="voucher-section">
      <h2>{title}</h2>
      <div className="voucher-grid">
        {items.map((item) => (
          <article className="voucher-card" key={item.id}>
            <div className="voucher-value">
              <strong>{yuan(item.actualValue)}</strong>
              <span>{item.payValue > 0 ? `${yuan(item.payValue)} 抢` : '免费领'}</span>
            </div>
            <div className="voucher-body">
              <h3>{item.title}</h3>
              <button type="button" className="text-action" onClick={() => onOpenShop(item.shopId)}>{item.shopName || `商户 ${item.shopId}`}</button>
              <p>{item.subTitle || item.rules || '到店消费可用'}</p>
              {item.type === 1 && <p>库存 {item.stock ?? '-'} · {timeText(item.beginTime)} 至 {timeText(item.endTime)}</p>}
              <button type="button" className="primary-action" disabled={busyId === item.id || item.activityStatus !== 'ACTIVE'} onClick={() => onClaim(item)}>
                {busyId === item.id ? '处理中' : item.activityStatus === 'ACTIVE' ? (item.type === 1 ? '立即秒杀' : '立即领取') : '未开始'}
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function ProfilePage({
  user,
  data,
  loading,
  aiMemories,
  aiMemoryLoading,
  aiMemoryDeletingKey,
  aiMemoryClearing,
  aiHistoryPreview,
  aiHistoryPreviewLoading,
  aiHistoryClearing,
  onLogin,
  onRefresh,
  onSign,
  onLogout,
  onOpenBlog,
  onDeleteBlog,
  onDeleteReview,
  onDeleteAiMemory,
  onClearAiMemories,
  onClearAiHistory
}) {
  if (!user) {
    return (
      <section className="page-panel profile-empty">
        <p className="eyebrow">用户详情</p>
        <h1>登录后查看你的探店数据</h1>
        <p>后端支持用户资料、签到、我的笔记、点赞、优惠券和评价数据。</p>
        <button type="button" className="primary-action" onClick={onLogin}>立即登录</button>
      </section>
    )
  }
  const blogs = data?.myBlogs || []
  const liked = data?.likedBlogs || []
  const vouchers = data?.vouchers || []
  const reviews = data?.reviews || []
  const aiMemoryGroups = groupAiMemories(aiMemories || [])
  const hasAiMemories = aiMemoryGroups.preferences.length > 0 || aiMemoryGroups.summaries.length > 0
  return (
    <section className="page-panel">
      <div className="profile-banner">
        <div className="avatar">{(user.nickName || 'U').slice(0, 1)}</div>
        <div>
          <p className="eyebrow">当前登录</p>
          <h1>{user.nickName || `用户 ${user.id}`}</h1>
          <p>连续签到 {data?.signDays ?? 0} 天 · 优惠券 {data?.voucherCount ?? vouchers.length} 张</p>
        </div>
        <div className="profile-actions">
          <button type="button" className="secondary-action" onClick={onRefresh} disabled={loading}>刷新</button>
          <button type="button" className="secondary-action" onClick={onSign}>签到</button>
          <button type="button" className="secondary-action" onClick={onLogout}>退出</button>
        </div>
      </div>
      <ProfileSection title="我的优惠券" empty="暂无优惠券">
        {vouchers.map((item) => <UserVoucher key={item.orderId || item.voucherId} voucher={normalizeVoucher(item)} />)}
      </ProfileSection>
      <ProfileSection title="我的探店笔记" empty="暂无笔记">
        {blogs.map((blog) => <BlogCard key={blog.id} blog={normalizeBlog(blog)} onOpen={() => onOpenBlog(normalizeBlog(blog))} onLike={() => {}} onDelete={() => onDeleteBlog(blog.id)} />)}
      </ProfileSection>
      <ProfileSection title="我点赞的笔记" empty="暂无点赞">
        {liked.map((blog) => <BlogCard key={blog.id} blog={normalizeBlog(blog)} onOpen={() => onOpenBlog(normalizeBlog(blog))} onLike={() => {}} />)}
      </ProfileSection>
      <ProfileSection title="我的评价" empty="暂无评价">
        {reviews.map((review) => <article className="review-list-item" key={review.id}><strong>{repairText(review.shopName) || '评价'}</strong><p>{repairText(review.content)}</p><button type="button" className="danger-action" onClick={() => onDeleteReview(review.id)}>删除</button></article>)}
      </ProfileSection>
    </section>
  )
}

function AiHistoryPreviewCard({ message }) {
  const label = message.role === 'assistant' ? 'AI' : '你'
  const content = repairText(richTextToPlainText(message.content || '')).slice(0, 120)
  return (
    <article className={`ai-history-card ${message.role === 'assistant' ? 'assistant' : 'user'}`}>
      <span>{label}</span>
      <p>{content || '空消息'}</p>
    </article>
  )
}

function AiMemoryCard({ memory, variant = 'preference', deleting, onDelete }) {
  const summary = formatAiMemorySummary(memory.summary)
  const source = formatAiMemorySource(memory)
  const updatedAt = formatAiMemoryTime(memory.updateTime)
  const deleteText = isConversationSummaryMemory(memory) ? '忽略此摘要' : '忽略此偏好'
  return (
    <article className={`ai-memory-card ${variant}`}>
      <div>
        <span>{formatAiMemoryType(memory)}</span>
        <strong>{formatAiMemoryKey(memory.memoryKey)}</strong>
      </div>
      <p>{summary}</p>
      <small className="ai-memory-meta">{source}{updatedAt ? ` · ${updatedAt}` : ''}</small>
      <footer>
        <small>置信度 {typeof memory.confidence === 'number' ? `${Math.round(memory.confidence * 100)}%` : '-'}</small>
        <button type="button" className="danger-action" onClick={onDelete} disabled={deleting}>
          {deleting ? '处理中' : deleteText}
        </button>
      </footer>
    </article>
  )
}

function groupAiMemories(memories) {
  return memories.reduce((groups, memory) => {
    if (isConversationSummaryMemory(memory)) {
      groups.summaries.push(memory)
    } else {
      groups.preferences.push(memory)
    }
    return groups
  }, { preferences: [], summaries: [] })
}

function isConversationSummaryMemory(memory) {
  const key = String(memory?.memoryKey || '')
  const type = String(memory?.memoryType || '')
  return key.startsWith('conversation.summary') || type === 'conversation.summary'
}

function formatAiMemoryType(memory) {
  if (isConversationSummaryMemory(memory)) return '对话摘要'
  if (String(memory?.memoryType || '').includes('dining')) return '推荐偏好'
  return memory?.memoryType || '偏好'
}

function formatAiMemorySource(memory) {
  const agent = String(memory?.sourceAgent || '')
  if (agent === 'ConversationSummaryAgent') return '来自历史对话摘要'
  if (agent === 'PreferenceExtractorAgent') return '来自你的聊天偏好'
  if (agent) return `来自 ${agent}`
  if (isConversationSummaryMemory(memory)) return '来自历史对话'
  return '来自 AI 记忆'
}

function formatAiMemoryTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function ProfileSection({ title, empty, action, children }) {
  const items = React.Children.toArray(children).filter(Boolean)
  return (
    <section className="profile-section">
      <div className="profile-section-header">
        <h2>{title}</h2>
        {action}
      </div>
      {items.length > 0 ? <div className="profile-grid">{items}</div> : <p className="muted-line">{empty}</p>}
    </section>
  )
}

function UserVoucher({ voucher }) {
  return (
    <article className="user-voucher">
      <strong>{yuan(voucher.actualValue)}</strong>
      <span>{voucher.title || `优惠券 ${voucher.id || voucher.voucherId}`}</span>
      <small>{voucher.shopName || `商户 ${voucher.shopId || '-'}`}</small>
    </article>
  )
}

function formatAiMemoryKey(key) {
  if (!key) return '偏好'
  if (String(key).startsWith('conversation.summary')) return '历史对话摘要'
  return String(key)
    .replace(/^dining\.preference\./, '')
    .replace(/^spot\.preference\./, '')
    .replace(/[_\.]+/g, ' ')
    .trim()
}

function formatAiMemorySummary(summary) {
  if (!summary) return 'AI 已记录这条偏好。'
  const text = String(summary).trim()
  try {
    const value = JSON.parse(text)
    if (Array.isArray(value)) {
      return value.map(formatAiMemoryValue).filter(Boolean).join('、') || text
    }
    if (value && typeof value === 'object') {
      const entries = Object.entries(value)
        .filter(([key]) => !isHiddenAiMemoryField(key))
        .map(([key, item]) => `${formatAiMemoryFieldLabel(key)}：${formatAiMemoryValue(item)}`)
        .filter((item) => !item.endsWith('：'))
      return entries.join('；') || text
    }
  } catch {
    return text
  }
  return text
}

function isHiddenAiMemoryField(key) {
  return ['source', 'memorySource', 'agentRoute', 'confidence'].includes(key)
}

function formatAiMemoryFieldLabel(key) {
  const labels = {
    summary: '摘要',
    budget: '预算',
    taste: '口味/品类',
    area: '常去区域',
    scene: '使用场景',
    avoid: '避雷点',
    discount: '优惠偏好',
    keyword: '关键词',
    likes: '喜欢',
    dislikes: '不喜欢',
    min: '最低',
    max: '最高'
  }
  return labels[key] || formatAiMemoryKey(key)
}

function formatAiMemoryValue(value) {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map(formatAiMemoryValue).filter(Boolean).join('、')
  if (typeof value === 'object') {
    return Object.entries(value)
      .filter(([key]) => !isHiddenAiMemoryField(key))
      .map(([key, item]) => `${formatAiMemoryFieldLabel(key)} ${formatAiMemoryValue(item)}`)
      .filter(Boolean)
      .join('，')
  }
  return String(value)
}

function DetailSection({ title, children }) {
  return (
    <section className="detail-section">
      <h3>{title}</h3>
      {children}
    </section>
  )
}

function LoginDialog({ open, email, code, countdown, busy, setEmail, setCode, onSendCode, onLogin, onClose }) {
  if (!open) return null
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="login-dialog" role="dialog" aria-modal="true" aria-labelledby="login-title" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="drawer-close" onClick={onClose} aria-label="关闭登录">×</button>
        <p className="eyebrow">账号登录</p>
        <h2 id="login-title">登录后参与秒杀、查看用户详情和同步 AI 记忆</h2>
        <form className="login-form" onSubmit={onLogin}>
          <label>邮箱<input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="请输入邮箱" /></label>
          <label>
            验证码
            <div className="code-field">
              <input value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 位验证码" />
              <button type="button" onClick={onSendCode} disabled={busy || countdown > 0}>{countdown > 0 ? `${countdown}s` : '获取'}</button>
            </div>
          </label>
          <button type="submit" className="primary-action" disabled={busy}>{busy ? '处理中...' : '登录'}</button>
        </form>
      </section>
    </div>
  )
}

function AiChatWidget({ open, onToggle, messages, input, setInput, loading, historyLoading, selectedBusiness, onOpenBusiness, onSubmit, onConfirmTool, onClearHistory }) {
  const messageListRef = useRef(null)
  useEffect(() => {
    if (messageListRef.current) messageListRef.current.scrollTop = messageListRef.current.scrollHeight
  }, [messages, loading, open])
  return (
    <aside className="ai-chat-widget" aria-label="AI 助手">
      {open && (
        <section className="ai-chat-panel">
          <header className="ai-chat-header">
            <div><p className="eyebrow">Spot AI</p><h2>本地生活助手</h2></div>
            <div className="ai-header-actions">
              <button type="button" className="ai-text-button" onClick={onClearHistory} disabled={loading}>清空</button>
              <button type="button" className="ai-icon-button" onClick={onToggle} aria-label="关闭 AI 助手">×</button>
            </div>
          </header>
          {selectedBusiness?.name && <div className="ai-chat-context">当前店铺：{selectedBusiness.name}</div>}
          {historyLoading && <div className="ai-chat-sync" role="status">正在同步历史对话...</div>}
          <div className="ai-chat-messages" ref={messageListRef}>
            {messages.map((message, index) => (
              <article key={message.id || `${message.role}-${index}-${message.generatedAt}`} className={`ai-message ${message.role === 'user' ? 'user' : 'assistant'} ${message.error ? 'error' : ''}`}>
                <span>{message.role === 'user' ? '你' : 'AI'}</span>
                {message.role === 'user'
                  ? <p>{message.content}</p>
                  : (
                    <div className="ai-assistant-bubble">
                      <AiMarkdown
                        content={message.confirmRequest && isRawConfirmPayloadForDisplay(message.content)
                          ? toolConfirmIntro(message.confirmRequest.toolName)
                          : message.content}
                        onOpenBusiness={onOpenBusiness}
                      />
                      {message.confirmRequest && (
                        <AiToolConfirmCard
                          request={message.confirmRequest}
                          onConfirm={() => onConfirmTool?.(message.id, true)}
                          onReject={() => onConfirmTool?.(message.id, false)}
                        />
                      )}
                    </div>
                  )}
                {message.role !== 'user' && message.usedTools?.includes('recommendShops') && <small className="tool-pill">已查询推荐候选</small>}
                {message.memoryUpdated && <small className="memory-pill">已更新偏好</small>}
              </article>
            ))}
            {loading && <article className="ai-message assistant"><span>AI</span><div className="ai-assistant-bubble"><div className="ai-markdown"><p>正在查询商家和评价数据...</p></div></div></article>}
          </div>
          <form className="ai-chat-form" onSubmit={onSubmit}>
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="问问附近哪家店适合你..."
              rows={2}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  onSubmit(event)
                }
              }}
            />
            <button type="submit" disabled={loading || !input.trim()}>{loading ? '发送中' : '发送'}</button>
          </form>
        </section>
      )}
      <button type="button" className="ai-fab" onClick={onToggle} aria-expanded={open}>AI</button>
    </aside>
  )
}

function AiToolConfirmCard({ request, onConfirm, onReject }) {
  const status = request?.status || 'pending'
  const processing = status === 'processing'
  const settled = ['confirmed', 'rejected', 'error'].includes(status)
  const title = request?.title || toolConfirmTitle(request?.toolName)
  const description = request?.description || toolConfirmDescription(request?.toolName)
  const primaryLabel = request?.toolName === 'claimCoupon' ? '确定领取' : '确认'
  const secondaryLabel = request?.toolName === 'claimCoupon' ? '暂不领取' : '取消'
  return (
    <section className={`ai-confirm-card ${status}`} role="group" aria-label={title}>
      <div>
        <span>{toolConfirmRiskLabel(request?.toolName)}</span>
        <strong>{title}</strong>
      </div>
      <p>{settled && request?.resultText ? request.resultText : description}</p>
      {!settled && (
        <footer>
          <button type="button" className="ai-confirm-secondary" onClick={onReject} disabled={processing}>
            {processing ? '处理中' : secondaryLabel}
          </button>
          <button type="button" className="ai-confirm-primary" onClick={onConfirm} disabled={processing}>
            {processing ? '领取中' : primaryLabel}
          </button>
        </footer>
      )}
    </section>
  )
}

function isRawConfirmPayloadForDisplay(content) {
  const text = String(content || '').trim()
  if (!text.includes('CONFIRM_REQUIRED')) return false
  const raw = text.replace(/^```(?:json)?/i, '').replace(/```$/i, '').trim()
  return raw.startsWith('{') && raw.endsWith('}')
}

function toolConfirmIntro(toolName) {
  if (toolName === 'claimCoupon') return 'AI 已为你找到可领取的优惠券，继续前需要你确认。'
  return 'AI 准备执行一项需要确认的操作。'
}

function toolConfirmTitle(toolName) {
  if (toolName === 'claimCoupon') return '确认领取优惠券'
  return '确认 AI 操作'
}

function toolConfirmDescription(toolName) {
  if (toolName === 'claimCoupon') return '确认后将调用后端为当前登录用户领取这张券，成功后会出现在“我的优惠券”中。'
  return '确认后 AI 将继续执行该操作。'
}

function toolConfirmRiskLabel(toolName) {
  if (toolName === 'claimCoupon') return '需要确认'
  return '敏感操作'
}

function AiMarkdown({ content, onOpenBusiness }) {
  const html = useMemo(() => {
    if (!content) return ''
    return marked.parse(content, { breaks: true, gfm: true })
      .replace(/<script[\s\S]*?<\/script>/gi, '')
      .replace(/on\w+\s*=\s*["'][^"']*["']/gi, '')
  }, [content])
  function handleClick(event) {
    const link = event.target.closest?.('a')
    if (!link) return
    const href = link.getAttribute('href') || ''
    const match = href.match(/^spotai:\/\/shop\/(\d+)$/)
    if (!match) return
    event.preventDefault()
    onOpenBusiness?.(Number(match[1]))
  }
  return <div className="ai-markdown" onClick={handleClick} dangerouslySetInnerHTML={{ __html: html }} />
}

function LoadingSkeleton() {
  return (
    <section className="skeleton-list" aria-busy="true" aria-label="正在加载">
      {Array.from({ length: 4 }).map((_, index) => (
        <article className="business-card skeleton-card" key={index}>
          <span className="skeleton-image" />
          <div><span className="skeleton-line short" /><span className="skeleton-line long" /><span className="skeleton-line" /><span className="skeleton-line medium" /></div>
        </article>
      ))}
    </section>
  )
}

function EmptyState({ title, text, action, onAction }) {
  return <section className="state-card" role="status"><strong>{title}</strong><p>{text}</p>{action && <button type="button" onClick={onAction}>{action}</button>}</section>
}

function ErrorState({ message, onRetry }) {
  return <section className="state-card error-state" role="alert"><strong>数据加载失败</strong><p>{message || '网络开小差了，请稍后再试。'}</p><button type="button" onClick={onRetry}>重新加载</button></section>
}

function stars(score) {
  const value = Math.max(0, Math.min(5, Math.round(Number(score || 0))))
  return '★★★★★'.slice(0, value) + '☆☆☆☆☆'.slice(0, 5 - value)
}

function normalizeShop(shop) {
  const fallback = mockBusinesses[Math.abs(Number(shop.id || 0)) % mockBusinesses.length]
  const rawScore = shop.score === null || shop.score === undefined ? fallback.rating : Number(shop.score)
  const score = rawScore > 5 ? rawScore / 10 : rawScore
  return {
    id: shop.id,
    name: repairText(shop.name) || fallback.name,
    category: repairText(shop.typeName || shop.category) || fallback.category,
    categoryId: shop.typeId ? `server:${shop.typeId}` : fallback.categoryId,
    district: repairText(shop.area) || fallback.district,
    address: repairText(shop.address) || fallback.address,
    distance: shop.distance === null || shop.distance === undefined ? fallback.distance : Number(Number(shop.distance).toFixed(1)),
    rating: Number(Number(score).toFixed(1)),
    reviews: shop.comments || fallback.reviews,
    avgPrice: shop.avgPrice || fallback.avgPrice,
    sold: shop.sold || fallback.sold,
    image: normalizeImage(shop.images, repairText(shop.name) || fallback.name) || fallback.image,
    deals: fallback.deals,
    summary: repairText(shop.introduction) || fallback.summary,
    reason: score >= 4.7 ? '高分好店' : fallback.reason,
    tags: [repairText(shop.area), repairText(shop.typeName), ...fallback.tags].filter(Boolean).slice(0, 3),
    hours: repairText(shop.openHours) || fallback.hours,
    phone: repairText(shop.phone) || fallback.phone,
    dishes: fallback.dishes
  }
}

function normalizeBlog(blog) {
  const content = richTextToPlainText(repairText(blog.content || blog.summary || ''))
  const images = normalizeImages(blog.images || blog.image, repairText(blog.title || '探店'))
  return {
    id: blog.id,
    title: repairText(blog.title) || '探店笔记',
    content,
    fullContent: content,
    excerpt: content.length > 140 ? `${content.slice(0, 140)}...` : content,
    image: images[0],
    images,
    author: repairText(blog.name || blog.author || blog.userName) || `用户 ${blog.userId || ''}`,
    userId: blog.userId,
    isFollow: Boolean(blog.isFollow),
    liked: blog.liked || 0
  }
}

function normalizeVoucher(voucher) {
  return {
    ...voucher,
    title: repairText(voucher.title),
    subTitle: repairText(voucher.subTitle),
    rules: repairText(voucher.rules),
    shopName: repairText(voucher.shopName)
  }
}

function normalizeReview(review) {
  const score = review.score === null || review.score === undefined ? 5 : Number(review.score)
  const normalizedScore = score > 5 ? score / 10 : score
  return {
    id: review.id,
    userName: repairText(review.userName) || '匿名用户',
    userIcon: normalizeImage(review.userIcon, repairText(review.userName || '用户')),
    score: normalizedScore,
    content: repairText(richTextToPlainText(review.content || '')),
    liked: review.liked || 0,
    images: Array.isArray(review.images)
      ? review.images.map((image, index) => normalizeImage(image, `评价${index + 1}`))
      : String(review.images || '').split(',').filter(Boolean).map((image, index) => normalizeImage(image, `评价${index + 1}`)),
    createTime: timeText(review.createTime).replace('长期有效', '')
  }
}

function mergeById(current, next) {
  const seen = new Set(current.map((item) => String(item.id)))
  return [...current, ...next.filter((item) => {
    const key = String(item.id)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })]
}

function repairText(value) {
  if (value === null || value === undefined) return ''
  const text = String(value)
  if (!/[ÃÂäåçéèæ]/.test(text)) return text
  try {
    const bytes = Uint8Array.from(Array.from(text).map((char) => char.charCodeAt(0) & 0xff))
    return new TextDecoder('utf-8', { fatal: false }).decode(bytes)
  } catch {
    return text
  }
}

function richTextToPlainText(value) {
  return String(value || '')
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

function normalizeImage(images, label = '店铺') {
  const value = String(images || '').split(',')[0]?.trim()
  if (!value || value.startsWith('/imgs/')) return imageFallback(label)
  return value.replace(/\\/g, '/')
}

function normalizeImages(images, label = '图片') {
  const list = String(images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => normalizeImage(item, label))
  return list.length > 0 ? list : [imageFallback(label)]
}

function imageCsvToList(images) {
  return String(images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function mergeImageCsv(current, nextUrls) {
  return mergeImageList(imageCsvToList(current), nextUrls)
}

function mergeImageList(current, nextUrls) {
  return [...new Set([...(current || []), ...(nextUrls || [])])].slice(0, 9)
}

function imageFallback(label = '店铺') {
  const text = String(label).slice(0, 4).replace(/[<>&'"]/g, '')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="420"><rect width="640" height="420" fill="#fff0e2"/><circle cx="492" cy="102" r="58" fill="#ffb35c"/><path d="M0 326C128 248 216 294 318 220C426 142 512 184 640 96V420H0Z" fill="#ffd3ac"/><text x="320" y="226" text-anchor="middle" font-family="Arial,Microsoft YaHei,sans-serif" font-size="42" font-weight="800" fill="#9b4420">${text}</text></svg>`
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

function yuan(value) {
  if (value === null || value === undefined || value === '') return '-'
  return `¥${Math.round(Number(value) / 100)}`
}

function timeText(value) {
  if (!value) return '长期有效'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatVoucher(voucher) {
  const actual = voucher.actualValue ? yuan(voucher.actualValue) : '优惠券'
  const pay = voucher.payValue > 0 ? ` ${Math.round(Number(voucher.payValue) / 100)}元抢` : ' 免费领'
  return `${voucher.title || actual}${pay}`
}

createRoot(document.getElementById('root')).render(<App />)

