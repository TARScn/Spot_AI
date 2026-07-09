import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildBusinessRankings,
  normalizeShopId,
  shopIdFromHref
} from './shopNavigation.js'
import { normalizeShopItems } from './shopItems.js'

test('keeps shop ids larger than the JavaScript safe integer range unchanged', () => {
  const shopId = '8394027033637893060'

  assert.equal(normalizeShopId(shopId), shopId)
  assert.equal(shopIdFromHref(`spotai://shop/${shopId}`), shopId)
})

test('accepts internal shop links emitted in supported Markdown forms', () => {
  assert.equal(shopIdFromHref('/shop/8562208734731944888'), '8562208734731944888')
  assert.equal(shopIdFromHref('#shop-8562208734731944888'), '8562208734731944888')
  assert.equal(shopIdFromHref('https://example.com'), '')
})

test('builds rankings from the same business records used by the detail drawer', () => {
  const businesses = [
    { id: '90071992547409931', name: '高分店', rating: 4.9, reviews: 200, sold: 300, avgPrice: 80, reason: '高分' },
    { id: '90071992547409932', name: '热门店', rating: 4.5, reviews: 500, sold: 900, avgPrice: 60, reason: '热门' },
    { id: '90071992547409933', name: '实惠店', rating: 4.6, reviews: 180, sold: 260, avgPrice: 35, reason: '实惠' }
  ]

  const rankings = buildBusinessRankings(businesses)

  assert.equal(rankings[0].items[0].shopId, '90071992547409932')
  assert.equal(rankings[1].items[0].shopId, '90071992547409931')
  assert.equal(rankings[2].items[0].shopId, '90071992547409933')
})

test('normalizes and sorts real shop items without losing large ids', () => {
  const items = normalizeShopItems([
    { id: '9000000000000400002', shopId: '7', name: '家常豆腐', description: '酱香浓郁', price: '2100', sort: 2 },
    { id: '9000000000000400001', shopId: '7', name: '招牌酸菜鱼', description: '酸爽鲜嫩', price: '7800', sort: 1 }
  ])

  assert.deepEqual(items.map((item) => item.id), [
    '9000000000000400001',
    '9000000000000400002'
  ])
  assert.equal(items[0].price, 7800)
})
