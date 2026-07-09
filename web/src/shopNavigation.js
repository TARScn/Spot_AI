export function normalizeShopId(value) {
  if (value === null || value === undefined) return ''
  const id = String(value).trim()
  return /^\d+$/.test(id) ? id : ''
}

export function shopIdFromHref(href) {
  if (!href) return ''
  let value = String(href).trim()
  try {
    value = decodeURIComponent(value)
  } catch {
    return ''
  }
  const patterns = [
    /^spotai:\/\/shop\/(\d+)\/?(?:[?#].*)?$/i,
    /^\/shop\/(\d+)\/?(?:[?#].*)?$/i,
    /^#shop-(\d+)$/
  ]
  for (const pattern of patterns) {
    const match = value.match(pattern)
    if (match) return normalizeShopId(match[1])
  }
  return ''
}

export function buildBusinessRankings(businesses) {
  const candidates = Array.isArray(businesses)
    ? businesses.filter((business) => normalizeShopId(business?.id) && business?.name)
    : []
  if (candidates.length === 0) return []

  return [
    {
      id: 'hot',
      title: '附近热门榜',
      items: rankingItems(candidates, compareHot, (business) => ({
        reason: `${numericValue(business.reviews)} 条评价`,
        tag: business.reason || '人气店'
      }))
    },
    {
      id: 'score',
      title: '高分必吃榜',
      items: rankingItems(candidates, compareScore, (business) => ({
        reason: '评分与口碑领先',
        tag: business.category || '高分'
      }))
    },
    {
      id: 'value',
      title: '性价比榜',
      items: rankingItems(candidates, compareValue, (business) => ({
        reason: `人均 ¥${numericValue(business.avgPrice)}`,
        tag: '性价比'
      }))
    }
  ]
}

function rankingItems(businesses, compare, describe) {
  return [...businesses]
    .sort(compare)
    .slice(0, 3)
    .map((business, index) => ({
      rank: index + 1,
      shopId: normalizeShopId(business.id),
      name: business.name,
      score: numericValue(business.rating).toFixed(1),
      ...describe(business)
    }))
}

function compareHot(left, right) {
  return numericValue(right.sold) - numericValue(left.sold)
    || numericValue(right.reviews) - numericValue(left.reviews)
    || compareScore(left, right)
}

function compareScore(left, right) {
  return numericValue(right.rating) - numericValue(left.rating)
    || numericValue(right.reviews) - numericValue(left.reviews)
}

function compareValue(left, right) {
  return valueScore(right) - valueScore(left) || compareScore(left, right)
}

function valueScore(business) {
  const price = Math.max(1, numericValue(business.avgPrice))
  return numericValue(business.rating) / price
}

function numericValue(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}
