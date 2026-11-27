import json
import re
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

BASE = "https://bourseeye.com"
TSETMC = "https://cdn.tsetmc.com"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/json,*/*;q=0.8",
    "Accept-Language": "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7",
}
session = requests.Session()
session.headers.update(HEADERS)

def norm(value):
    value = str(value or "").translate(str.maketrans({
        "ي":"ی", "ك":"ک", "ۀ":"ه", "ة":"ه", "أ":"ا", "إ":"ا",
        "ؤ":"و", "‌":"", "ـ":""
    }))
    value = re.sub(r"[\u064b-\u065f\u0670]", "", value)
    return re.sub(r"[^\w\u0600-\u06ff]+", "", value).lower()

def strings(obj, keys):
    out = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in keys and isinstance(v, (str, int, float)):
                out.append(str(v))
            elif isinstance(v, (dict, list)):
                out.extend(strings(v, keys))
    elif isinstance(obj, list):
        for v in obj:
            out.extend(strings(v, keys))
    return out

def score_match(left, right):
    a, b = norm(left), norm(right)
    if not a or not b:
        return 0
    if a == b:
        return 100
    if len(a) >= 4 and len(b) >= 4 and (a in b or b in a):
        return 88
    # Token overlap after retaining Persian/Latin letters and digits.
    ta = {x for x in re.findall(r"[\w\u0600-\u06ff]{3,}", str(left).lower())}
    tb = {x for x in re.findall(r"[\w\u0600-\u06ff]{3,}", str(right).lower())}
    if ta and tb:
        return int(100 * len(ta & tb) / len(ta))
    return 0

def main():
    # Pull the same commodity-fund universe used by the Android application.
    api = session.get(TSETMC + "/api/ClosingPrice/GetTradeTop/CommodityFund/0/9999", timeout=(20,45))
    print(f"TSETMC_LIST_HTTP={api.status_code}; BYTES={len(api.content)}")
    api.raise_for_status()
    root = api.json()
    rows = []
    def find_rows(value):
        if isinstance(value, dict):
            for k, v in value.items():
                if k in ("tradeTop", "funds") and isinstance(v, list):
                    rows.extend(v)
                elif isinstance(v, (dict, list)):
                    find_rows(v)
        elif isinstance(value, list):
            for v in value:
                find_rows(v)
    find_rows(root)
    funds = {}
    for item in rows:
        if not isinstance(item, dict):
            continue
        inst = item.get("instrument") if isinstance(item.get("instrument"), dict) else item
        code = str(inst.get("insCode") or inst.get("inscode") or item.get("insCode") or item.get("inscode") or "")
        if not code:
            continue
        symbol = str(inst.get("lVal18AFC") or inst.get("symbol") or item.get("lVal18AFC") or item.get("symbol") or "")
        name = str(inst.get("lVal30") or inst.get("name") or item.get("lVal30") or item.get("name") or symbol)
        funds[code] = {"code":code, "symbol":symbol, "name":name}
    print(f"TSETMC_COMMODITY_FUNDS={len(funds)}")
    if not funds:
        print("TSETMC_RESPONSE_SAMPLE=" + json.dumps(root, ensure_ascii=False)[:2500])
        raise RuntimeError("No usable instruments in TSETMC response")

    listing = session.get(BASE + "/funds/gold", timeout=(20,45))
    print(f"BOURSEEYE_LIST_HTTP={listing.status_code}; BYTES={len(listing.content)}; URL={listing.url}")
    listing.raise_for_status()
    soup = BeautifulSoup(listing.text, "html.parser")
    found = {}
    for a in soup.select("a[href]"):
        url = urljoin(BASE, a.get("href", ""))
        match = re.search(r"/funds/(\d+)/([^/?#]+)", url)
        if match:
            found.setdefault(match.group(1), {"slug":match.group(2), "label":a.get_text(" ", strip=True), "url":url})
    print(f"BOURSEEYE_FUND_LINKS={len(found)}")

    # Validate the live history table structurally and by actual numeric paired rows.
    page_data = {}
    for fid, item in sorted(found.items()):
        url = item["url"].rstrip("/") + "/history"
        try:
            r = session.get(url, timeout=(20,45))
            r.raise_for_status()
            page = BeautifulSoup(r.text, "html.parser")
            result = {"http":r.status_code, "rows":0, "valid_pairs":0, "headers":[], "url":url}
            for table in page.select("table"):
                trs = table.select("tr")
                if not trs:
                    continue
                headers = [re.sub(r"\s+"," ",c.get_text(" ",strip=True)).strip() for c in trs[0].select("th,td")]
                date_i = next((i for i,h in enumerate(headers) if "تاریخ" in h), -1)
                nav_i = next((i for i,h in enumerate(headers) if "NAV تعدیل" in h), -1)
                if nav_i < 0:
                    nav_i = next((i for i,h in enumerate(headers) if "NAV ابطال" in h), -1)
                close_i = next((i for i,h in enumerate(headers) if "قیمت پایانی" in h), -1)
                if min(date_i,nav_i,close_i) < 0:
                    continue
                valid = 0
                nav_count = 0
                data_count = 0
                valid_rows = []
                nav_rows = []
                month_map = {"فروردین":1,"اردیبهشت":2,"خرداد":3,"تیر":4,"مرداد":5,"شهریور":6,"مهر":7,"آبان":8,"آذر":9,"دی":10,"بهمن":11,"اسفند":12}
                for tr in trs[1:]:
                    cells = [c.get_text(" ",strip=True) for c in tr.select("td,th")]
                    if len(cells) <= max(date_i,nav_i,close_i):
                        continue
                    data_count += 1
                    def number(x):
                        x = str(x).translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹٬٫", "0123456789,."))
                        x = re.sub(r"[^0-9.]", "", x)
                        try: return float(x) if x else None
                        except: return None
                    date_text = cells[date_i].translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789"))
                    date_match = re.search(r"(\d{1,2})\s+([\wآ-ی]+)\s+(\d{4})", date_text)
                    nav_value, close_value = number(cells[nav_i]), number(cells[close_i])
                    if date_match:
                        day, month_name, year = int(date_match.group(1)), date_match.group(2), int(date_match.group(3))
                        month = month_map.get(month_name)
                        if month and 1 <= day <= 31:
                            date_key = year*10000+month*100+day
                            if nav_value and nav_value > 0:
                                nav_count += 1
                                nav_rows.append((date_key, nav_value))
                            if nav_value and nav_value > 0 and close_value and close_value > 0:
                                valid += 1
                                valid_rows.append((date_key, nav_value, close_value))
                unique = {row[0]: row for row in valid_rows}
                ordered = sorted(unique.values(), key=lambda x:x[0], reverse=True)
                unique_nav = {row[0]: row for row in nav_rows}
                ordered_nav = sorted(unique_nav.values(), key=lambda x:x[0], reverse=True)
                def nav_return(days):
                    # Match Android: use actual trading dates with both close and NAV.
                    if len(ordered) <= days or ordered[days][1] <= 0:
                        return None
                    return round((ordered[0][1]/ordered[days][1]-1)*100, 4)
                result.update(rows=data_count, valid_pairs=valid, unique_pairs=len(unique), nav_observations=len(unique_nav), return5=nav_return(5), return15=nav_return(15), return30=nav_return(30), headers=headers)
                break
            page_data[fid] = result
            print(f"HISTORY fid={fid}; label={item['label']}; HTTP={result['http']}; ROWS={result['rows']}; VALID_PRICE_NAV_PAIRS={result['valid_pairs']}; UNIQUE_PAIRS={result.get('unique_pairs',0)}; NAV_OBSERVATIONS={result.get('nav_observations',0)}; NAV_RETURN_5_15_30={result.get('return5')}/{result.get('return15')}/{result.get('return30')}; HEADERS={result['headers']}")
            if not result["headers"]:
                title = page.title.get_text(" ", strip=True) if page.title else ""
                text_sample = page.get_text(" ", strip=True)[:900]
                script_sample = " ".join(x.get_text(" ", strip=True) for x in page.select("script") if any(k in x.get_text() for k in ("history","nav","fund","chart","__NEXT_DATA__")))[:1800]
                print(f"HTML_DIAG fid={fid}; URL={r.url}; BYTES={len(r.content)}; TITLE={title}; TABLES={len(page.select('table'))}; BODY={text_sample}; SCRIPTS={script_sample}")
        except Exception as exc:
            page_data[fid] = {"http":"ERROR","rows":0,"valid_pairs":0,"headers":[],"url":url}
            print(f"HISTORY fid={fid}; label={item['label']}; ERROR={type(exc).__name__}:{exc}")

    # Conservative one-to-one name mapping: no automatic acceptance of ambiguous matches.
    unmatched = []
    mapped = set()
    for fund in funds.values():
        ranked = sorted(((max(score_match(fund["name"], p["label"]), score_match(fund["symbol"], p["label"])), fid, p)
                         for fid,p in found.items()), reverse=True, key=lambda x:x[0])
        best = ranked[0] if ranked else (0,None,None)
        second = ranked[1][0] if len(ranked)>1 else 0
        if best[0] >= 65 and best[0] > second:
            fid, p = best[1], best[2]
            mapped.add(fid)
            stats = page_data.get(fid,{})
            print(f"MAP symbol={fund['symbol']}; name={fund['name']}; score={best[0]}; bourseeye={p['label']}; rows={stats.get('rows',0)}; valid_pairs={stats.get('valid_pairs',0)}")
        else:
            unmatched.append(fund)
            print(f"UNMATCHED symbol={fund['symbol']}; name={fund['name']}; best_score={best[0]}; runner_up={second}")
    print(f"SUMMARY TSETMC={len(funds)} BOURSEEYE={len(found)} MAPPED={len(mapped)} UNMATCHED={len(unmatched)}")
    print(f"SUMMARY_HISTORY_PAGES_WITH_60_VALID_PAIRS={sum(1 for x in page_data.values() if x.get('valid_pairs',0)>=60)}/{len(page_data)}")
    print(f"SUMMARY_HISTORY_PAGES_WITH_ANY_VALID_PAIRS={sum(1 for x in page_data.values() if x.get('valid_pairs',0)>0)}/{len(page_data)}")

if __name__ == "__main__":
    main()
