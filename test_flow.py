import urllib.request, urllib.parse, json

# Test 1: Direct search API
r = urllib.request.urlopen('http://localhost:8080/shop/search?keyword=' + urllib.parse.quote('海底捞'), timeout=5)
d = json.loads(r.read().decode())
print(f'[REST API] search("海底捞") => {len(d["data"])} results')

# Test 2: Check that coupons exist for shop 5 (海底捞水晶城店)
r = urllib.request.urlopen('http://localhost:8080/shop/5', timeout=5)
d = json.loads(r.read().decode())
shop5 = d['data']
print(f'[REST API] shop/5 => name={shop5["name"]}, avg_price={shop5.get("avgPrice")}')

# Test 3: Check voucher activities
r = urllib.request.urlopen('http://localhost:8080/voucher/activities/of/shop?shopId=5', timeout=5)
d = json.loads(r.read().decode())
vouchers = d.get('data', [])
print(f'[REST API] voucher/activities/of/shop?shopId=5 => {len(vouchers)} vouchers')
for v in vouchers[:3]:
    print(f'  id={v["id"]} title={v.get("title")} type={"seckill" if v.get("type")==1 else "regular"}')
