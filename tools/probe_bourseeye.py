import re
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

BASE = "https://bourseeye.com"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7",
}
session = requests.Session()
session.headers.update(HEADERS)

def norm(value):
    return re.sub(r"\\s+", " ", value or "").strip()

def main():
    response = session.get(BASE + "/funds/gold", timeout=(20, 45))
    print(f"LIST_HTTP={response.status_code}; FINAL_URL={response.url}; BYTES={len(response.content)}")
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    found = {}
    for a in soup.select("a[href]"):
        url = urljoin(BASE, a.get("href", ""))
        match = re.search(r"/funds/(\\d+)/([^/?#]+)", url)
        if match:
            found.setdefault(match.group(1), (match.group(2), norm(a.get_text(" ", strip=True))))
    print(f"FUND_LINKS={len(found)}")
    if not found:
        print("TITLE=" + norm(soup.title.get_text(" ", strip=True) if soup.title else "NO_TITLE"))
        print("BODY=" + norm(soup.get_text(" ", strip=True))[:1500])
        raise SystemExit("No fund links in server response")

    validated = 0
    for fid, (slug, label) in sorted(found.items()):
        url = f"{BASE}/funds/{fid}/{slug}/history"
        try:
            r = session.get(url, timeout=(20, 45))
            r.raise_for_status()
            page = BeautifulSoup(r.text, "html.parser")
            tables = page.select("table")
            history_table = None
            for table in tables:
                rows = table.select("tr")
                headers = [norm(c.get_text(" ", strip=True)) for c in (rows[0].select("th,td") if rows else [])]
                if len(headers) >= 5 and "تاریخ" in headers[0] and any("NAV" in h for h in headers):
                    history_table = (headers, rows)
                    break
            if history_table:
                headers, rows = history_table
                data_rows = []
                for row in rows[1:]:
                    cells = [norm(c.get_text(" ", strip=True)) for c in row.select("th,td")]
                    if cells:
                        data_rows.append(cells)
                validated += 1
                print(f"FUND={fid}; name={label}; HTTP={r.status_code}; HISTORY_ROWS={len(data_rows)}; HEADERS={headers}; SAMPLE={data_rows[:3]}")
            else:
                print(f"FUND={fid}; name={label}; HTTP={r.status_code}; HISTORY_TABLE=NOT_FOUND; TABLES={len(tables)}; TEXT={norm(page.get_text(' ', strip=True))[:250]}")
        except Exception as exc:
            print(f"FUND={fid}; name={label}; ERROR={type(exc).__name__}:{exc}")
    print(f"HISTORY_VALIDATED={validated}/{len(found)}")

if __name__ == "__main__":
    main()
