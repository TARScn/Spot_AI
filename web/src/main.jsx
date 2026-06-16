import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const tokenKey = 'spotai_token'
const visitorKey = 'spotai_visitor'
const phonePattern = /^1[3-9]\d{9}$/
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

function normalizeImage(images) {
  if (!images) return ''
  return String(images).split(',')[0].trim()
}

function imageList(images) {
  if (!images) return []
  return String(images).split(',').map((item) => item.trim()).filter(Boolean)
}

function App() {
  const [activeTab, setActiveTab] = useState('home')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [countdown, setCountdown] = useState(0)
  const [user, setUser] = useState(null)
  const [toast, setToast] = useState('')
  const [busy, setBusy] = useState(false)
  const [categories, setCategories] = useState([])
  const [typeId, setTypeId] = useState(1)
  const [shops, setShops] = useState([])
  const [shopLoading, setShopLoading] = useState(false)
  const [selectedShop, setSelectedShop] = useState(null)
  const [shopReviews, setShopReviews] = useState([])
  const [shopDetailLoading, setShopDetailLoading] = useState(false)
  const [blogs, setBlogs] = useState([])
  const [feed, setFeed] = useState([])
  const [blogLoading, setBlogLoading] = useState(false)
  const [signDays, setSignDays] = useState(null)
  const [siteUv, setSiteUv] = useState(null)
  const [shopUv, setShopUv] = useState(null)
  const [blogForm, setBlogForm] = useState({
    shopId: '1',
    title: '',
    images: '',
    content: ''
  })
  const [voucherForm, setVoucherForm] = useState({
    shopId: '1',
    title: '100元代金券',
    subTitle: '周一至周日均可使用',
    rules: '全场通用，不与其他优惠同享',
    payValue: '8000',
    actualValue: '10000',
    stock: '50'
  })

  const loggedIn = Boolean(user)

  useEffect(() => {
    ensureVisitor()
    hydrateUser()
    loadCategories()
    loadShops(1)
    loadBlogs()
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
    if (!phonePattern.test(phone)) {
      setToast('请输入正确的手机号')
      return
    }
    if (countdown > 0) return
    setBusy(true)
    const body = await api(`/user/code?${new URLSearchParams({ phone })}`, { method: 'POST' })
    setBusy(false)
    if (!body.success) {
      setToast(body.errorMsg || '验证码发送失败')
      return
    }
    setCountdown(60)
    setToast('验证码已发送，请查看后端日志')
  }

  async function login(event) {
    event.preventDefault()
    if (!phonePattern.test(phone)) {
      setToast('请输入正确的手机号')
      return
    }
    if (!/^\d{6}$/.test(code)) {
      setToast('请输入 6 位验证码')
      return
    }
    setBusy(true)
    const body = await api('/user/login', {
      method: 'POST',
      body: JSON.stringify({ phone, code })
    })
    setBusy(false)
    if (!body.success) {
      setToast(body.errorMsg || '登录失败')
      return
    }
    localStorage.setItem(tokenKey, body.data)
    setToast('登录成功')
    await hydrateUser()
  }

  function logout() {
    localStorage.removeItem(tokenKey)
    setUser(null)
    setSignDays(null)
    setFeed([])
    setToast('已退出登录')
  }

  async function loadShops(nextTypeId = typeId) {
    setShopLoading(true)
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

  async function loadShopDetail(shopOrId) {
    const summary = typeof shopOrId === 'object' ? shopOrId : null
    const id = summary?.id || shopOrId
    setShopDetailLoading(true)
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
    await loadShopReviews(id)
    setShopDetailLoading(false)
  }

  async function loadShopReviews(shopId) {
    const body = await api(`/blog/of/shop?${new URLSearchParams({ id: String(shopId), current: '1' })}`)
    if (body.success && Array.isArray(body.data)) {
      setShopReviews(body.data)
      return
    }
    setShopReviews([])
    if (!body.success) setToast(body.errorMsg || '店铺评价加载失败')
  }

  async function warmGeo() {
    const body = await api('/shop/geo/load', { method: 'PUT' })
    setToast(body.success ? '附近商户坐标已重新加载' : body.errorMsg || 'GEO 加载失败')
  }

  async function loadBlogs() {
    setBlogLoading(true)
    const body = await api('/blog/hot?current=1')
    setBlogLoading(false)
    if (body.success && Array.isArray(body.data)) {
      setBlogs(body.data)
      return
    }
    setBlogs([])
    if (!body.success) setToast(body.errorMsg || '探店笔记加载失败')
  }

  async function loadFeed() {
    if (!loggedIn) {
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

  async function publishBlog(event) {
    event.preventDefault()
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
    const body = await api('/blog', { method: 'POST', body: JSON.stringify(payload) })
    if (body.success) {
      setToast(`发布成功，笔记 ID：${body.data}`)
      setBlogForm({ shopId: '1', title: '', images: '', content: '' })
      loadBlogs()
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
      setBlogs((items) => items.map((item) => (
        item.id === id
          ? { ...item, isLike: !item.isLike, liked: Number(item.liked || 0) + (item.isLike ? -1 : 1) }
          : item
      )))
    } else {
      setToast(body.errorMsg || '操作失败')
    }
  }

  async function followAuthor(userId) {
    if (!loggedIn) {
      setToast('登录后才能关注')
      setActiveTab('login')
      return
    }
    const body = await api(`/follow/${userId}/true`, { method: 'PUT' })
    setToast(body.success ? '已关注作者' : body.errorMsg || '关注失败')
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
    if (!body.success && targetType !== 'site') setToast(body.errorMsg || 'UV 记录失败')
  }

  async function loadStats() {
    const today = new Date().toISOString().slice(0, 10)
    const site = await api(`/stats/uv/site?date=${today}`)
    const shop = selectedShop?.id ? await api(`/stats/uv/shop/${selectedShop.id}?date=${today}`) : null
    if (site.success) setSiteUv(site.data)
    if (shop?.success) setShopUv(shop.data)
  }

  async function addVoucher(type) {
    const payload = {
      shopId: Number(voucherForm.shopId),
      title: voucherForm.title.trim(),
      subTitle: voucherForm.subTitle.trim(),
      rules: voucherForm.rules.trim(),
      payValue: Number(voucherForm.payValue),
      actualValue: Number(voucherForm.actualValue)
    }
    if (type === 'seckill') {
      const begin = new Date(Date.now() + 60 * 60 * 1000)
      const end = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000)
      payload.stock = Number(voucherForm.stock)
      payload.beginTime = begin.toISOString().slice(0, 19)
      payload.endTime = end.toISOString().slice(0, 19)
    }
    const path = type === 'seckill' ? '/voucher/seckill' : '/voucher'
    const body = await api(path, { method: 'POST', body: JSON.stringify(payload) })
    setToast(body.success ? `优惠券创建成功，ID：${body.data}` : body.errorMsg || '优惠券创建失败')
  }

  async function seckillVoucher() {
    if (!loggedIn) {
      setToast('登录后才能抢购秒杀券')
      setActiveTab('login')
      return
    }
    const voucherId = window.prompt('请输入秒杀券 ID')
    if (!voucherId) return
    const body = await api(`/voucher-order/seckill/${voucherId}`, { method: 'POST' })
    setToast(body.success ? `下单已受理，订单 ID：${body.data}` : body.errorMsg || '抢购失败')
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
        <div className="search-box">
          <span>搜</span>
          <input aria-label="搜索商户或探店笔记" placeholder="搜索商户、优惠券、探店笔记" />
        </div>
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
              if (key === 'profile') loadStats()
              if (key === 'blogs') loadBlogs()
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
            phone={phone}
            code={code}
            countdown={countdown}
            busy={busy}
            user={user}
            setPhone={setPhone}
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
            onOpenShop={loadShopDetail}
            userLocation={userLocation}
          />
        )}

        {activeTab === 'shopDetail' && (
          <ShopDetailPage
            shop={selectedShop}
            reviews={shopReviews}
            loading={shopDetailLoading}
            onBack={() => setActiveTab('shops')}
            onLike={likeBlog}
            onFollow={followAuthor}
          />
        )}

        {activeTab === 'blogs' && (
          <BlogPage
            blogs={blogs}
            feed={feed}
            loading={blogLoading}
            form={blogForm}
            setForm={setBlogForm}
            onPublish={publishBlog}
            onLike={likeBlog}
            onFollow={followAuthor}
            onLoadFeed={loadFeed}
          />
        )}

        {activeTab === 'deals' && (
          <DealPage
            form={voucherForm}
            setForm={setVoucherForm}
            onAddVoucher={addVoucher}
            onSeckill={seckillVoucher}
          />
        )}

        {activeTab === 'profile' && (
          <ProfilePage
            user={user}
            signDays={signDays}
            siteUv={siteUv}
            shopUv={shopUv}
            selectedShop={selectedShop}
            onSign={signToday}
            onRefreshStats={loadStats}
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
          ['deals', '优惠'],
          ['profile', '我的']
        ].map(([key, label]) => (
          <button key={key} className={activeTab === key ? 'nav-active' : ''} onClick={() => setActiveTab(key)}>
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
    </div>
  )
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
            <EmptyState title="暂无商户数据" text="请确认后端已启动、tb_shop 已导入西安商户，并刷新 Redis GEO。" />
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

function LoginPage({ phone, code, countdown, busy, user, setPhone, setCode, onSendCode, onLogin, onLogout }) {
  return (
    <section className="auth-layout">
      <div className="auth-copy">
        <p className="eyebrow">手机号快捷登录</p>
        <h1>用验证码进入 Spot AI</h1>
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
              手机号
              <input value={phone} onChange={(event) => setPhone(event.target.value.trim())} placeholder="请输入手机号" inputMode="tel" maxLength={11} />
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

function ShopPage({ categories, typeId, setTypeId, shops, loading, selectedShop, onOpenShop, userLocation }) {
  return (
    <div className="page-grid shop-layout">
      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">附近商户</p>
            <h1>按分类查找附近好店</h1>
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
            {shops.length === 0 ? <EmptyState title="暂无商户" text="可以先确认数据库中是否已有 tb_shop 数据。" /> : shops.map((shop) => (
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

function ShopDetailPage({ shop, reviews, loading, onBack, onLike, onFollow }) {
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
            <EmptyState title="暂无评价" text="当前店铺还没有探店笔记，可以登录后发布第一条评价。" />
          ) : reviews.map((blog) => (
            <BlogCard key={blog.id} blog={blog} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog.userId)} />
          ))}
        </div>
      </section>
    </div>
  )
}

function BlogPage({ blogs, feed, loading, form, setForm, onPublish, onLike, onFollow, onLoadFeed }) {
  return (
    <div className="page-grid blog-layout">
      <section className="content-section">
        <div className="section-head">
          <div>
            <p className="eyebrow">探店笔记</p>
            <h1>真实体验和口碑内容</h1>
          </div>
          <button className="secondary" onClick={onLoadFeed}>关注流</button>
        </div>
        {loading ? <SkeletonList /> : (
          <div className="blog-feed">
            {blogs.length === 0 ? (
              <EmptyState title="暂无探店笔记" text="当前没有从本地数据库查询到探店内容，可以登录后发布一篇。" />
            ) : blogs.map((blog) => (
              <BlogCard key={blog.id} blog={blog} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog.userId)} />
            ))}
            {feed.length > 0 && (
              <div className="feed-block">
                <p className="eyebrow">关注流</p>
                {feed.map((blog) => <BlogCard key={`feed-${blog.id}`} blog={blog} onLike={() => onLike(blog.id)} onFollow={() => onFollow(blog.userId)} />)}
              </div>
            )}
          </div>
        )}
      </section>
      <aside className="compose-panel">
        <p className="eyebrow">发布笔记</p>
        <h2>写一篇探店</h2>
        <form className="form" onSubmit={onPublish}>
          <label>
            商户 ID
            <input value={form.shopId} onChange={(event) => setForm({ ...form, shopId: event.target.value })} inputMode="numeric" />
          </label>
          <label>
            标题
            <input value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="例如：这家店的午市套餐很能打" />
          </label>
          <label>
            图片地址
            <input value={form.images} onChange={(event) => setForm({ ...form, images: event.target.value })} placeholder="可粘贴 MinIO 图片 URL" />
          </label>
          <label>
            正文
            <textarea value={form.content} onChange={(event) => setForm({ ...form, content: event.target.value })} placeholder="口味、环境、服务、排队情况..." />
          </label>
          <button className="primary wide" type="submit">发布</button>
        </form>
      </aside>
    </div>
  )
}

function DealPage({ form, setForm, onAddVoucher, onSeckill }) {
  return (
    <div className="page-grid deal-layout">
      <section className="deal-hero">
        <div>
          <p className="eyebrow">限时优惠</p>
          <h1>券包和秒杀活动管理</h1>
          <p>普通券用于店铺促销，秒杀券走 Redis 库存校验和异步下单链路。</p>
          <button className="primary" onClick={onSeckill}>抢购秒杀券</button>
        </div>
        <div className="coupon-preview" aria-hidden="true">
          <span>券</span>
          <strong>本地活动</strong>
          <p>创建后写入数据库</p>
        </div>
      </section>
      <aside className="compose-panel">
        <p className="eyebrow">创建优惠券</p>
        <h2>商户活动</h2>
        <form className="form" onSubmit={(event) => event.preventDefault()}>
          {[
            ['shopId', '商户 ID'],
            ['title', '标题'],
            ['subTitle', '副标题'],
            ['rules', '使用规则'],
            ['payValue', '支付金额，单位分'],
            ['actualValue', '抵扣金额，单位分'],
            ['stock', '秒杀库存']
          ].map(([key, label]) => (
            <label key={key}>
              {label}
              <input value={form[key]} onChange={(event) => setForm({ ...form, [key]: event.target.value })} />
            </label>
          ))}
          <div className="split-actions">
            <button className="secondary" type="button" onClick={() => onAddVoucher('normal')}>普通券</button>
            <button className="primary" type="button" onClick={() => onAddVoucher('seckill')}>秒杀券</button>
          </div>
        </form>
      </aside>
    </div>
  )
}

function ProfilePage({ user, signDays, siteUv, shopUv, selectedShop, onSign, onRefreshStats, onLogin, onLogout }) {
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

  return (
    <div className="profile-layout">
      <section className="profile-banner">
        <div className="avatar large">{user.nickName?.slice(0, 1) || 'U'}</div>
        <div>
          <p className="eyebrow">当前登录</p>
          <h1>{user.nickName}</h1>
          <p>ID {user.id}</p>
        </div>
        <button className="secondary" onClick={onLogout}>退出</button>
      </section>
      <section className="metric-grid">
        <Metric title="连续签到" value={`${signDays ?? 0} 天`} action="签到" onAction={onSign} />
        <Metric title="全站今日 UV" value={siteUv ?? '-'} action="刷新" onAction={onRefreshStats} />
        <Metric title={`${selectedShop?.name || '当前商户'} UV`} value={shopUv ?? '-'} action="刷新" onAction={onRefreshStats} />
      </section>
    </div>
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

function BlogCard({ blog, onLike, onFollow }) {
  const image = normalizeImage(blog.images)
  return (
    <article className="blog-card">
      {image ? <img src={image} alt={blog.title} /> : <ImagePlaceholder label="探店" />}
      <div>
        <div className="author-line">
          <span className="mini-avatar">{blog.name?.slice(0, 1) || 'U'}</span>
          <strong>{blog.name || `用户 ${blog.userId}`}</strong>
          <button className="text-button" onClick={onFollow}>关注</button>
        </div>
        <h3>{blog.title}</h3>
        <p>{blog.content}</p>
        <div className="blog-actions">
          <button className={blog.isLike ? 'liked' : ''} onClick={onLike}>赞 {blog.liked || 0}</button>
          <span>评 {blog.comments || 0}</span>
          <span>{blog.createTime ? String(blog.createTime).slice(0, 10) : '刚刚'}</span>
        </div>
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
