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
    print(f"LIST_HTTP={response.status_code}; FINAL_URL={response.url}; CONTENT_TYPE={response.headers.get('content-type')}; BYTES={len(response.content)}")
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    print("PAGE_TITLE=" + norm(soup.title.get_text(" ", strip=True) if soup.title else "NO_TITLE"))
    print("BODY_START=" + norm(soup.get_text(" ", strip=True))[:1200])

    hrefs = [urljoin(BASE, a.get("href", "")) for a in soup.select("a[href]")]
    fund_hrefs = [u for u in hrefs if re.search(r"/funds/\\d+/", u)]
    print(f"ANCHORS={len(hrefs)}; FUND_HREFS={len(fund_hrefs)}")
    for url in fund_hrefs[:50]:
        print("FUND_LINK=" + url)

    scripts = [urljoin(BASE, s.get("src", "")) for s in soup.select("script[src]")]
    print(f"SCRIPT_COUNT={len(scripts)}")
    for url in scripts:
        print("SCRIPT_SRC=" + url)

    # Next.js/RSC pages may carry route data or API references outside anchors.
    text = response.text
    for pattern in (r"https?://api\\.bourseeye\\.com[^\\\"' ]+", r"/api/[^\\\"' ]+", r"/funds/\\d+/[^\\\"' ]+"):
        matches = list(dict.fromkeys(re.findall(pattern, text)))
        print(f"EMBEDDED_MATCHES pattern={pattern} count={len(matches)}")
        for match in matches[:40]:
            print("EMBEDDED=" + match[:500])

    if not fund_hrefs:
        print("DIAGNOSIS=No fund links in server response; inspect body/title and embedded payload above. This is not evidence that the fund history is unavailable.")

if __name__ == "__main__":
    main()
