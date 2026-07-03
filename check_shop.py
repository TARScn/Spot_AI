import pymysql
conn = pymysql.connect(host='localhost', port=3306, user='root', password='000000', database='spotai_0', charset='utf8mb4')
c = conn.cursor()
c.execute("SELECT id, name FROM tb_shop WHERE name LIKE '%海%底%捞%'")
rows = c.fetchall()
print(f'Found {len(rows)} shops:')
for r in rows:
    print(f'  {r[0]} | {r[1]}')
c.close()
conn.close()
