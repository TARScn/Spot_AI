export const cityOptions = ['西安', '北京', '上海', '成都', '杭州']

export const categories = [
  { id: 'food', name: '美食', iconKey: 'food' },
  { id: 'hotpot', name: '火锅', iconKey: 'hotpot' },
  { id: 'bbq', name: '烧烤', iconKey: 'bbq' },
  { id: 'tea', name: '奶茶', iconKey: 'tea' },
  { id: 'coffee', name: '咖啡', iconKey: 'coffee' },
  { id: 'hotel', name: '酒店', iconKey: 'hotel' },
  { id: 'movie', name: '电影', iconKey: 'movie' },
  { id: 'fun', name: '休闲娱乐', iconKey: 'fun' },
  { id: 'beauty', name: '丽人', iconKey: 'beauty' },
  { id: 'family', name: '亲子', iconKey: 'family' },
  { id: 'travel', name: '周边游', iconKey: 'travel' }
]
export const filters = [
  { id: 'nearby', label: '附近' },
  { id: 'rating', label: '评分最高' },
  { id: 'distance', label: '距离最近' },
  { id: 'price', label: '人均价格' },
  { id: 'sold', label: '销量优先' },
  { id: 'deal', label: '优惠优先' },
  { id: 'quality', label: '高分好店' }
]

export const districts = ['钟楼', '小寨', '曲江', '高新', '大雁塔', '赛格', '永宁门']

export const businesses = [
  {
    id: 1,
    name: '炉边巷子老火锅',
    category: '火锅',
    categoryId: 'hotpot',
    district: '小寨',
    address: '长安中路赛格国际购物中心 7F',
    distance: 0.7,
    rating: 4.8,
    reviews: 4821,
    avgPrice: 88,
    sold: 13200,
    image: 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=900&q=80',
    deals: ['双人火锅套餐 138 元', '满 200 减 30', '工作日免排队券'],
    summary: '牛油锅底厚重，毛肚和鸭血稳定在线，晚高峰排队较多。',
    reason: '附近高分',
    tags: ['排队热门', '朋友聚餐', '夜宵'],
    hours: '10:30 - 02:00',
    phone: '029-8838 2688',
    dishes: ['鲜切吊龙', '脆毛肚', '红糖糍粑'],
    reviewsList: [
      '锅底香但不糊口，服务员加汤很及时。',
      '适合朋友聚餐，套餐分量比预期足。',
      '周末建议提前取号，等位区有小食。'
    ]
  },
  {
    id: 2,
    name: '南门里陕菜馆',
    category: '美食',
    categoryId: 'food',
    district: '永宁门',
    address: '南大街粉巷 18 号',
    distance: 1.4,
    rating: 4.7,
    reviews: 3169,
    avgPrice: 72,
    sold: 9800,
    image: 'https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=900&q=80',
    deals: ['招牌葫芦鸡套餐 99 元', '收藏送酸梅汤'],
    summary: '葫芦鸡外皮酥，油泼面香味足，外地朋友来西安很稳。',
    reason: '本地口碑',
    tags: ['陕菜', '游客友好', '家庭聚餐'],
    hours: '11:00 - 22:00',
    phone: '029-8721 7799',
    dishes: ['葫芦鸡', '油泼扯面', '温拌腰丝'],
    reviewsList: ['环境比老店清爽，菜量大。', '带外地朋友来很合适。', '招牌菜出品稳定。']
  },
  {
    id: 3,
    name: '春山咖啡 Roastery',
    category: '咖啡',
    categoryId: 'coffee',
    district: '高新',
    address: '科技路创业咖啡街区 B 座',
    distance: 2.2,
    rating: 4.9,
    reviews: 1260,
    avgPrice: 39,
    sold: 4200,
    image: 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=900&q=80',
    deals: ['手冲第二杯半价', '下午茶双人券 68 元'],
    summary: '浅烘豆选择多，座位间距舒适，适合办公和轻聊天。',
    reason: '环境舒适',
    tags: ['精品咖啡', '可办公', '安静'],
    hours: '08:30 - 21:30',
    phone: '029-8123 9901',
    dishes: ['耶加雪菲手冲', '开心果拿铁', '巴斯克蛋糕'],
    reviewsList: ['豆子新鲜，手冲风味干净。', '插座多，工作日下午很舒服。', '甜品不会太腻。']
  },
  {
    id: 4,
    name: '竹签记把把烧',
    category: '烧烤',
    categoryId: 'bbq',
    district: '钟楼',
    address: '东木头市 36 号',
    distance: 0.9,
    rating: 4.6,
    reviews: 5980,
    avgPrice: 64,
    sold: 18600,
    image: 'https://images.unsplash.com/photo-1529193591184-b1d58069ecdd?auto=format&fit=crop&w=900&q=80',
    deals: ['夜宵四人餐 168 元', '啤酒买二送一'],
    summary: '串小但入味，上菜快，越晚越热闹，适合夜宵续摊。',
    reason: '回头客多',
    tags: ['夜宵', '烟火气', '多人聚会'],
    hours: '17:00 - 03:00',
    phone: '029-8766 4312',
    dishes: ['牛油小串', '烤苕皮', '掌中宝'],
    reviewsList: ['小串很入味，适合边聊边吃。', '晚上气氛好，稍微有点吵。', '套餐价格挺划算。']
  },
  {
    id: 5,
    name: '桃野制茶',
    category: '奶茶',
    categoryId: 'tea',
    district: '曲江',
    address: '芙蓉新天地 1F',
    distance: 3.1,
    rating: 4.5,
    reviews: 2240,
    avgPrice: 23,
    sold: 15400,
    image: 'https://images.unsplash.com/photo-1558857563-b371033873b8?auto=format&fit=crop&w=900&q=80',
    deals: ['第二杯 9.9 元', '新品试饮券'],
    summary: '茶底清爽，果茶更受欢迎，拍照区对女生很友好。',
    reason: '性价比高',
    tags: ['果茶', '拍照', '排队短'],
    hours: '10:00 - 22:30',
    phone: '029-8100 6218',
    dishes: ['白桃乌龙', '茉莉青提', '厚乳茶'],
    reviewsList: ['果茶不甜腻，夏天很适合。', '门店小但出杯快。', '包装好看。']
  },
  {
    id: 6,
    name: '云端影院足道',
    category: '休闲娱乐',
    categoryId: 'fun',
    district: '大雁塔',
    address: '雁塔南路银泰城 5F',
    distance: 2.8,
    rating: 4.7,
    reviews: 1886,
    avgPrice: 119,
    sold: 6300,
    image: 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=900&q=80',
    deals: ['影院足道 90 分钟 99 元', '双人包间套餐'],
    summary: '房间干净，技师力度稳定，看电影休息两不误。',
    reason: '放松首选',
    tags: ['足疗', '包间', '可停车'],
    hours: '12:00 - 02:00',
    phone: '029-8912 6677',
    dishes: ['影院足道', '肩颈放松', '热石理疗'],
    reviewsList: ['包间私密性不错。', '力度可以提前沟通。', '逛完大雁塔来休息很合适。']
  }
]

export const rankings = [
  {
    id: 'hot',
    title: '附近热门榜',
    items: [
      { rank: 1, name: '竹签记把把烧', score: 4.6, reason: '夜宵搜索热度高', tag: '烟火气' },
      { rank: 2, name: '炉边巷子老火锅', score: 4.8, reason: '晚餐排队热门', tag: '聚餐' },
      { rank: 3, name: '桃野制茶', score: 4.5, reason: '曲江人气茶饮', tag: '果茶' }
    ]
  },
  {
    id: 'score',
    title: '高分必吃榜',
    items: [
      { rank: 1, name: '春山咖啡 Roastery', score: 4.9, reason: '环境和出品双高', tag: '精品咖啡' },
      { rank: 2, name: '炉边巷子老火锅', score: 4.8, reason: '食材稳定', tag: '火锅' },
      { rank: 3, name: '南门里陕菜馆', score: 4.7, reason: '本地菜代表', tag: '陕菜' }
    ]
  },
  {
    id: 'value',
    title: '性价比榜',
    items: [
      { rank: 1, name: '桃野制茶', score: 4.5, reason: '低客单高复购', tag: '优惠' },
      { rank: 2, name: '竹签记把把烧', score: 4.6, reason: '套餐分量足', tag: '夜宵' },
      { rank: 3, name: '南门里陕菜馆', score: 4.7, reason: '多人聚餐划算', tag: '家庭' }
    ]
  }
]

