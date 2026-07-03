import urllib.request, urllib.parse, json

# 1. Test search API
kw = '海底捞'
url = 'http://localhost:8080/shop/search?keyword=' + urllib.parse.quote(kw)
r = urllib.request.urlopen(url, timeout=5)
data = json.loads(r.read().decode())
print(f'search("海底捞"): success={data["success"]}, count={len(data["data"])}')

# 2. Simulate the exact coupon claim flow - first login
login_url = 'http://localhost:8080/user/login'
login_body = json.dumps({"email": "xiaoyu@spotai.local", "code": "000000"}).encode()
req = urllib.request.Request(login_url, data=login_body, headers={'Content-Type': 'application/json'})
try:
    r = urllib.request.urlopen(req, timeout=5)
    login_data = json.loads(r.read().decode())
    token = login_data.get('data', '')
    print(f'login: success={login_data["success"]}, token={token[:20]}...')
    
    # 3. Send AI chat with coupon claim intent
    ai_url = 'http://localhost:8080/ai/chat'
    ai_body = json.dumps({
        "message": "帮我领取一下海底捞的代金券",
        "history": [],
        "route": "CHAT"
    }).encode()
    req2 = urllib.request.Request(ai_url, data=ai_body, headers={
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {token}'
    })
    r2 = urllib.request.urlopen(req2, timeout=30)
    ai_data = json.loads(r2.read().decode())
    print(f'\nAI response: success={ai_data["success"]}')
    if ai_data.get('success'):
        d = ai_data['data']
        print(f'  answer: {d["answer"][:100]}')
        print(f'  confirmation: {d.get("toolConfirmation")}')
        print(f'  usedTools: {d.get("usedTools")}')
    else:
        print(f'  error: {ai_data.get("errorMsg")}')
except urllib.error.HTTPError as e:
    print(f'HTTP Error {e.code}: {e.read().decode()[:200]}')
