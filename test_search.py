import urllib.request, urllib.parse, json
kw = '海底捞'
url = 'http://localhost:8080/shop/search?keyword=' + urllib.parse.quote(kw)
r = urllib.request.urlopen(url, timeout=5)
data = json.loads(r.read().decode())
print(f'success={data["success"]}, count={len(data["data"])}')
for d in data['data']:
    print(f'  {d["id"]} | {d["name"]}')
