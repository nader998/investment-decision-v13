import re
import sys
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

BASE = "https://bourseeye.com"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; GoldFundDataProbe/1.0)"}
session = requests.Session()
session.headers.update(HEADERS)

def get(url):
    r = session.get(url, timeout=(15, 45))
    r.raise_for_status()
    return r

def norm(s):
    return re.sub(r"\s+", " ", s or "").strip()

def main():
    listing = get(BASE + "/funds/gold")
    soup = BeautifulSoup(listing.text, "html.parser")
    found = {}
    for a in soup.select('a[href]'):
        href = a.get("href", "")
        m = re.search(r"/funds/(\d+)/([^/?#]+)", href)
        if not m:
            continue
        fid = m.group(1)
        slug = m.group(2)
        label = norm(a.get_text(" ", strip=True))
        if fid not in found:
            found[fid] = (slug, label)
    print(f"LIST_HTTP={listing.status_code}; FUND_LINKS={len(found)}")
    if "1521" not in found:
        print("ERROR: known Ayar fund id 1521 not found in listing")
        sys.exit(2)

    for fid, (slug, label) in sorted(found.items()):
        url = f"{BASE}/funds/{fid}/{slug}"
        try:
            r = get(url)
            page = BeautifulSoup(r.text, "html.parser")
            tables = []
            for ti, table in enumerate(page.select("table")):
                rows = table.select("tr")
                headers = [norm(x.get_text(" ", strip=True)) for x in (rows[0].select("th,td") if rows else [])]
                samples = []
                for row in rows[1:3]:
                    cells = [norm(x.get_text(" ", strip=True)) for x in row.select("th,td")]
                    if cells:
                        samples.append(cells)
                if rows:
                    tables.append((len(rows), headers, samples))
            if fid == "1521":
                print(f"AYAR_URL={url}")
                print(f"AYAR_HTTP={r.status_code}; TABLES={len(tables)}")
                for n, headers, samples in tables:
                    print(f"AYAR_TABLE rows={n} headers={headers} samples={samples}")
                # Also expose table ids/classes and a compact excerpt around NAV labels.
                for ti, table in enumerate(page.select("table")):
                    print(f"AYAR_TABLE_META[{ti}] id={table.get('id')} class={table.get('class')}")
                for needle in ("NAV تعدیل", "NAV ابطال", "قیمت پایانی"):
                    text = page.get_text(" ", strip=True)
                    print(f"AYAR_HAS_{needle}={needle in text}")
            print(f"FUND={fid}; label={label}; HTTP={r.status_code}; tables={len(tables)}; table_rows={max([x[0] for x in tables], default=0)}")
        except Exception as e:
            print(f"FUND={fid}; label={label}; ERROR={type(e).__name__}:{e}")

if __name__ == "__main__":
    main()
