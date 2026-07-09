export function normalizeShopItems(items) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      id: String(item?.id || '').trim(),
      shopId: String(item?.shopId || '').trim(),
      name: String(item?.name || '').trim(),
      description: String(item?.description || '').trim(),
      price: finiteNumber(item?.price),
      sort: finiteNumber(item?.sort)
    }))
    .filter((item) => item.id && item.name)
    .sort((left, right) => left.sort - right.sort || left.id.localeCompare(right.id))
}

export function formatShopItemPrice(price) {
  const cents = finiteNumber(price)
  if (cents <= 0) return '到店咨询'
  const yuan = cents / 100
  return `¥${Number.isInteger(yuan) ? yuan : yuan.toFixed(2)}`
}

function finiteNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}
