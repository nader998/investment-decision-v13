package com.example.investmentdecision

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.icu.util.PersianCalendar
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Read-only first stage of automatic gold-fund market data.
 *
 * TSETMC CDN endpoints are community-documented and not an official,
 * versioned public API. Missing fields are intentionally left unavailable.
 */
class GoldFundAutoDataActivity : Activity() {

    private data class Quote(
        val symbol: String,
        val name: String,
        val insCode: String,
        val date: String,
        val marketPriceToman: Double?,
        val navToman: Double?,
        val tradeValueToman: Double?,
        val netRealFlowToman: Double?,
        val spreadPercent: Double?,
        val return5: Double?,
        val return15: Double?,
        val return30: Double?,
        val premiumPercentile60: Double?,
        val pairedHistoryCount: Int,
        val historyStatus: String
    )

    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val blue = Color.rgb(21, 101, 192)
    private val pageColor = Color.rgb(243, 246, 250)
    private val cachePrefs by lazy { getSharedPreferences("tsetmc_fund_cache", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(pageColor)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        page.addView(text("دریافت خودکار اطلاعات صندوق‌های کالایی", 21, true, Gravity.CENTER))
        page.addView(text(
            "منبع مرحله اول: داده‌های بازار TSETMC. قیمت و ارزش معاملات از ریال به تومان تبدیل می‌شوند.",
            13, false
        ))
        page.addView(text(
            "درصدک حباب ۶۰روزه، بازده NAV و ورود پول واقعی هنوز از منبع قابل‌تأیید دریافت نمی‌شوند؛ بنابراین در این صفحه هیچ سیگنال خرید یا فروش صادر نمی‌شود.",
            13, true
        ))

        val refresh = Button(this).apply {
            text = "دریافت / به‌روزرسانی اطلاعات"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { loadData() }
        }
        page.addView(refresh, matchWrap())

        status = text("برای دریافت اطلاعات، دکمه بالا را بزن.", 13, true)
        page.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(results)

        page.addView(Button(this).apply {
            text = "بازگشت"
            isAllCaps = false
            setOnClickListener { finish() }
        }, matchWrap())

        setContentView(scroll)
    }

    private fun loadData() {
        status.text = "در حال اتصال به TSETMC و دریافت فهرست صندوق‌ها..."
        results.removeAllViews()

        Thread {
            try {
                val listJson = getJson(
                    "https://cdn.tsetmc.com/api/ClosingPrice/GetTradeTop/CommodityFund/0/9999"
                )
                val rows = findArray(listJson, setOf("tradeTop", "funds"))
                if (rows.length() == 0) {
                    throw IllegalStateException("فهرست صندوق‌های کالایی خالی است.")
                }

                val candidates = mutableListOf<Triple<String, String, String>>()
                for (i in 0 until rows.length()) {
                    val item = rows.optJSONObject(i) ?: continue
                    val instrument = item.optJSONObject("instrument") ?: item
                    val code = firstString(instrument, "insCode", "inscode")
                        ?: firstString(item, "insCode", "inscode")
                        ?: continue
                    val symbol = firstString(instrument, "lVal18AFC", "symbol")
                        ?: firstString(item, "lVal18AFC", "symbol")
                        ?: code
                    val name = firstString(instrument, "lVal30", "name")
                        ?: firstString(item, "lVal30", "name")
                        ?: symbol
                    if (candidates.none { it.third == code }) {
                        candidates.add(Triple(symbol, name, code))
                    }
                }

                if (candidates.isEmpty()) {
                    throw IllegalStateException("فهرست دریافت شد، اما نماد و کد قابل‌استفاده در پاسخ پیدا نشد.")
                }

                // Limit parallel requests to avoid overloading the public market-data endpoint.
                val pool = Executors.newFixedThreadPool(4)
                val quotes = try {
                    val tasks = candidates.take(100).map { item ->
                        Callable { fetchQuote(item.first, item.second, item.third) }
                    }
                    pool.invokeAll(tasks, 55, TimeUnit.SECONDS)
                        .mapNotNull { future ->
                            try { if (future.isDone && !future.isCancelled) future.get() else null }
                            catch (_: Exception) { null }
                        }
                } finally {
                    pool.shutdownNow()
                }

                runOnUiThread {
                    results.removeAllViews()
                    if (quotes.isEmpty()) {
                        showCachedData("فهرست صندوق‌ها دریافت شد، اما اطلاعات قیمت جدیدی قابل‌خواندن نبود.")
                    } else {
                        val orderedQuotes = quotes.sortedBy { it.symbol }
                        saveCachedQuotes(orderedQuotes)
                        val marketDate = orderedQuotes.mapNotNull { dateKey(it.date) }.maxOrNull()
                        val today = tehranTodayKey()
                        status.text = if (marketDate != null && marketDate < today) {
                            "بازار امروز معامله جدیدی ثبت نکرده یا داده جدید منتشر نشده است. آخرین تاریخ داده: ${marketDate}. تعداد نمادها: ${candidates.size} | دریافت‌شده: ${quotes.size}"
                        } else {
                            "اطلاعات بازار دریافت شد. تعداد نمادهای شناسایی‌شده: ${candidates.size} | دریافت‌شده: ${quotes.size}"
                        }
                        orderedQuotes.forEach { addQuoteCard(it) }
                        if (quotes.size < candidates.size) {
                            results.addView(text(
                                "توجه: برای بعضی نمادها پاسخ کامل دریافت نشد؛ تعداد نمادهای ناموفق: ${candidates.size - quotes.size}.",
                                12, false
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showCachedData("دریافت اطلاعات جدید ناموفق بود: ${e.message ?: "خطای ارتباط"}.")
                    Toast.makeText(this, "در صورت وجود، آخرین اطلاعات ذخیره‌شده نمایش داده شد.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun fetchQuote(symbol: String, name: String, insCode: String): Quote {
        val snapshot = getJson(
            "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/$insCode"
        )
        val priceObject = findObject(snapshot, "closingPriceInfo") ?: snapshot
        val marketPrice = firstNumber(priceObject, "pDrCotVal", "pClosing")
            ?.takeIf { it > 0.0 }?.div(10.0)
        val dailyHistory = try {
            val historyRoot = getJson("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceDailyList/$insCode/5")
            findArray(historyRoot, setOf("closingPriceDaily"))
        } catch (_: Exception) { JSONArray() }
        val latestDaily = (0 until dailyHistory.length())
            .mapNotNull { dailyHistory.optJSONObject(it) }
            .maxByOrNull { firstNumber(it, "dEven") ?: 0.0 }
        // qTotCap is total traded value in the TSETMC daily-price response (Rial).
        val tradeValue = latestDaily?.let { firstNumber(it, "qTotCap") }
            ?.takeIf { it >= 0.0 }?.div(10.0)
        val date = buildDate(
            firstNumber(priceObject, "dEven")?.toLong(),
            firstNumber(priceObject, "hEven")?.toLong()
        )

        val nav = try {
            val etfJson = getJson(
                "https://cdn.tsetmc.com/api/Fund/GetETFByInsCode/$insCode"
            )
            val etf = findObject(etfJson, "etf") ?: etfJson
            firstNumber(etf, "pRedTran")?.takeIf { it > 0.0 }?.div(10.0)
        } catch (_: Exception) {
            null
        }

        val netRealFlow = try {
            val flowRoot = getJson("https://cdn.tsetmc.com/api/ClientType/GetClientTypeHistory/$insCode")
            val flowRows = findArray(flowRoot, setOf("clientType"))
            val latestFlow = (0 until flowRows.length())
                .mapNotNull { flowRows.optJSONObject(it) }
                .maxByOrNull { firstNumber(it, "recDate") ?: 0.0 }
            if (latestFlow == null) null else {
                val buy = firstNumber(latestFlow, "buy_I_Value")
                val sell = firstNumber(latestFlow, "sell_I_Value")
                if (buy != null && sell != null) (buy - sell) / 10.0 else null
            }
        } catch (_: Exception) { null }

        val spread = try {
            val limitsJson = getJson("https://cdn.tsetmc.com/api/BestLimits/$insCode")
            val limits = findArray(limitsJson, setOf("bestLimits"))
            var bid = 0.0
            var ask = 0.0
            for (i in 0 until limits.length()) {
                val level = limits.optJSONObject(i) ?: continue
                val demand = firstNumber(level, "pd") ?: 0.0
                val offer = firstNumber(level, "po") ?: 0.0
                if (demand > 0.0 && (bid == 0.0 || demand > bid)) bid = demand
                if (offer > 0.0 && (ask == 0.0 || offer < ask)) ask = offer
            }
            if (bid > 0.0 && ask > 0.0 && ask >= bid) {
                ((ask - bid) / ((ask + bid) / 2.0)) * 100.0
            } else null
        } catch (_: Exception) {
            null
        }

        val historyMetrics = try {
            fetchAndStoreHistory(insCode, name, symbol)
        } catch (e: Exception) {
            HistoryMetrics(null, null, null, null, 0, "خطای تاریخچه: ${e.message ?: "نامشخص"}")
        }
        return Quote(
            symbol, name, insCode, date, marketPrice, nav, tradeValue, netRealFlow, spread,
            historyMetrics.return5, historyMetrics.return15, historyMetrics.return30,
            historyMetrics.percentile60, historyMetrics.pairedCount, historyMetrics.status
        )
    }

    private data class HistoryMetrics(
        val return5: Double?,
        val return15: Double?,
        val return30: Double?,
        val percentile60: Double?,
        val pairedCount: Int,
        val status: String
    )

    private fun fetchAndStoreHistory(insCode: String, fundName: String, symbol: String): HistoryMetrics {
        val historyStore = GoldFundHistoryStore(this)
        val priceRoot = getJson("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceDailyList/$insCode/60")
        val priceRows = findArray(priceRoot, setOf("closingPriceDaily"))
        if (priceRows.length() == 0) return HistoryMetrics(null, null, null, null, 0, "تاریخچه قیمت خالی است")

        val prices = mutableMapOf<String, Double>()
        for (i in 0 until priceRows.length()) {
            val row = priceRows.optJSONObject(i) ?: continue
            val day = firstNumber(row, "dEven")?.toLong()?.toString() ?: continue
            val close = firstNumber(row, "pClosing")?.takeIf { it > 0.0 }?.div(10.0) ?: continue
            prices[day] = close
        }

        // Refresh the fund registry on every run; a permanently cached registry can become stale.
        val downloadedRegistry = findArray(
            getJson("https://cdn.tsetmc.com/api/Fund/GetFunds/5"),
            setOf("funds", "fund", "items")
        )
        val registry = if (downloadedRegistry.length() > 0) {
            cachePrefs.edit().putString("commodity_funds_registry", downloadedRegistry.toString()).apply()
            downloadedRegistry
        } else {
            val cached = cachePrefs.getString("commodity_funds_registry", null)
            if (cached != null) JSONArray(cached) else JSONArray()
        }
        val wanted = setOf(normalizeFundName(fundName), normalizeFundName(symbol))
            .filter { it.isNotBlank() }.toSet()
        var registrationNumber: String? = null
        for (i in 0 until registry.length()) {
            val row = registry.optJSONObject(i) ?: continue
            val names = listOf("mfName", "FundName", "name", "lVal18AFC", "symbol")
                .mapNotNull { firstString(row, it) }.map { normalizeFundName(it) }
            if (names.any { it in wanted }) {
                registrationNumber = firstString(row, "regNo", "RegistrationNumber", "registrationNumber")
                if (registrationNumber != null) break
            }
        }

        val navByDate = mutableMapOf<String, Double>()
        if (registrationNumber != null) {
            val detailRoot = getJson("https://cdn.tsetmc.com/api/Fund/GetFundInDetail/$registrationNumber")
            val stats = findArray(detailRoot, setOf("Stats", "stats", "fundStats"))
            for (i in 0 until stats.length()) {
                val row = stats.optJSONObject(i) ?: continue
                val rawDate = firstString(row, "UpdateGregorianDate", "updateGregorianDate", "date", "dEven")
                    ?: firstNumber(row, "dEven")?.toLong()?.toString() ?: continue
                val day = normalizeHistoryDate(rawDate) ?: continue
                val navValue = firstNumber(row, "RedemptionNAV", "redemptionNAV", "pRedTran", "nav")
                    ?.takeIf { it > 0.0 }?.div(10.0) ?: continue
                navByDate[day] = navValue
            }
        }

        // Never replace a previously stored NAV with null when a source date is absent.
        prices.forEach { (day, close) ->
            val historicalNav = navByDate[day]
            if (historicalNav != null) {
                historyStore.upsert(insCode, day, close, historicalNav)
            }
        }

        val paired = historyStore.pairedObservationCount(insCode)
        val r5 = historyStore.navReturnPercent(insCode, 5)
        val r15 = historyStore.navReturnPercent(insCode, 15)
        val r30 = historyStore.navReturnPercent(insCode, 30)
        val percentile = historyStore.percentile60(insCode)
        val status = when {
            paired >= 60 -> "تاریخچه قیمت و NAV کامل است"
            registrationNumber == null -> "کد ثبت صندوق در فهرست TSETMC پیدا نشد؛ نام/نماد تطبیق داده نشد"
            navByDate.isEmpty() -> "NAV تاریخی از پاسخ صندوق استخراج نشد"
            else -> "تاریخچه جفت‌شده $paired روز است؛ برای درصدک ۶۰روزه کافی نیست"
        }
        return HistoryMetrics(r5, r15, r30, percentile, paired, status)
    }

    private fun normalizeFundName(value: String): String =
        value.replace("ي", "ی").replace("ك", "ک")
            .replace(Regex("\\s+"), "").trim().lowercase(Locale.ROOT)

    private fun normalizeHistoryDate(value: String): String? {
        val raw = value.trim()
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 8) return null
        val firstEight = digits.take(8)
        // TSETMC daily prices use Solar Hijri YYYYMMDD. Fund detail dates are often
        // Gregorian ISO strings; convert those before joining the two histories.
        if ((raw.startsWith("19") || raw.startsWith("20")) &&
            (raw.contains("-") || raw.contains("/"))) {
            return try {
                val datePart = raw.take(10).replace('/', '-')
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(datePart) ?: return null
                val persian = PersianCalendar().apply { timeInMillis = parsed.time }
                "%04d%02d%02d".format(
                    Locale.US,
                    persian.get(android.icu.util.Calendar.YEAR),
                    persian.get(android.icu.util.Calendar.MONTH) + 1,
                    persian.get(android.icu.util.Calendar.DAY_OF_MONTH)
                )
            } catch (_: Exception) { null }
        }
        return firstEight
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            useCaches = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun findArray(root: JSONObject, keys: Set<String>): JSONArray {
        for (key in keys) {
            val value = root.opt(key)
            if (value is JSONArray) return value
        }
        val names = root.keys()
        while (names.hasNext()) {
            val value = root.opt(names.next())
            if (value is JSONObject) {
                val nested = findArrayOrNull(value, keys)
                if (nested != null) return nested
            }
        }
        return JSONArray()
    }

    private fun findArrayOrNull(root: JSONObject, keys: Set<String>): JSONArray? {
        for (key in keys) {
            val value = root.opt(key)
            if (value is JSONArray) return value
        }
        val names = root.keys()
        while (names.hasNext()) {
            when (val value = root.opt(names.next())) {
                is JSONObject -> findArrayOrNull(value, keys)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val child = value.optJSONObject(i) ?: continue
                        findArrayOrNull(child, keys)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun findObject(root: JSONObject, key: String): JSONObject? {
        val value = root.opt(key)
        if (value is JSONObject) return value
        if (value is JSONArray && value.length() > 0) return value.optJSONObject(0)
        return null
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.opt(key)
            if (value != null && value != JSONObject.NULL) {
                val string = value.toString().trim()
                if (string.isNotEmpty() && string != "0") return string
            }
        }
        return null
    }

    private fun firstNumber(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val value = obj.opt(key)
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            if (number != null && number.isFinite()) return number
        }
        return null
    }

    private fun buildDate(day: Long?, time: Long?): String {
        if (day == null || day <= 0L) return "تاریخ در پاسخ موجود نیست"
        val timeText = time?.toString()?.padStart(6, '0')
        return if (timeText != null && timeText.length == 6) {
            "$day - ${timeText.substring(0, 2)}:${timeText.substring(2, 4)}"
        } else day.toString()
    }


    private fun saveCachedQuotes(quotes: List<Quote>) {
        val array = JSONArray()
        quotes.forEach { q ->
            array.put(JSONObject().apply {
                put("symbol", q.symbol)
                put("name", q.name)
                put("insCode", q.insCode)
                put("date", q.date)
                put("marketPriceToman", q.marketPriceToman ?: JSONObject.NULL)
                put("navToman", q.navToman ?: JSONObject.NULL)
                put("tradeValueToman", q.tradeValueToman ?: JSONObject.NULL)
                put("netRealFlowToman", q.netRealFlowToman ?: JSONObject.NULL)
                put("spreadPercent", q.spreadPercent ?: JSONObject.NULL)
                put("return5", q.return5 ?: JSONObject.NULL)
                put("return15", q.return15 ?: JSONObject.NULL)
                put("return30", q.return30 ?: JSONObject.NULL)
                put("premiumPercentile60", q.premiumPercentile60 ?: JSONObject.NULL)
                put("pairedHistoryCount", q.pairedHistoryCount)
                put("historyStatus", q.historyStatus)
            })
        }
        cachePrefs.edit().putString("last_quotes", array.toString())
            .putLong("saved_at", System.currentTimeMillis()).apply()
    }

    private fun loadCachedQuotes(): List<Quote> {
        val raw = cachePrefs.getString("last_quotes", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                fun n(key: String): Double? =
                    if (o.isNull(key)) null else o.optDouble(key).takeIf { it.isFinite() }
                Quote(o.optString("symbol"), o.optString("name"), o.optString("insCode"),
                    o.optString("date"), n("marketPriceToman"), n("navToman"),
                    n("tradeValueToman"), n("netRealFlowToman"), n("spreadPercent"),
                    n("return5"), n("return15"), n("return30"), n("premiumPercentile60"),
                    o.optInt("pairedHistoryCount", 0), o.optString("historyStatus", "وضعیت تاریخچه نامشخص"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun showCachedData(reason: String) {
        results.removeAllViews()
        val cached = loadCachedQuotes()
        if (cached.isEmpty()) {
            status.text = "$reason هیچ اطلاعات ذخیره‌شده‌ای برای نمایش وجود ندارد."
            return
        }
        val savedAt = cachePrefs.getLong("saved_at", 0L)
        val savedText = if (savedAt > 0L) SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }.format(Date(savedAt))
            else "نامشخص"
        val latest = cached.mapNotNull { dateKey(it.date) }.maxOrNull() ?: "نامشخص"
        status.text = "$reason آخرین تاریخ داده: $latest | ذخیره روی گوشی: $savedText | تعداد صندوق‌ها: ${cached.size}. داده‌ها زنده نیستند."
        cached.sortedBy { it.symbol }.forEach { addQuoteCard(it) }
    }

    private fun dateKey(value: String): String? = Regex("\\d{8}").find(value)?.value

    private fun tehranTodayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        }.format(Date())

    private fun addQuoteCard(q: Quote) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            setBackgroundColor(Color.WHITE)
        }
        card.addView(text("${q.symbol} | ${q.name}", 15, true))
        card.addView(text("زمان داده قیمت: ${q.date}", 11, false))
        card.addView(text("قیمت بازار: ${money(q.marketPriceToman)} تومان", 13, true))
        card.addView(text("NAV ابطال: ${money(q.navToman)} تومان", 13, false))
        val premium = if (q.marketPriceToman != null && q.navToman != null && q.navToman > 0.0) {
            "${fmt((q.marketPriceToman / q.navToman - 1.0) * 100.0)}٪"
        } else "فاقد داده"
        card.addView(text("فاصله قیمت بازار از NAV: $premium", 12, false))
        card.addView(text("ارزش معاملات روز: ${money(q.tradeValueToman)} تومان", 12, false))
        card.addView(text("خالص خرید حقیقی: ${money(q.netRealFlowToman)} تومان (خرید حقیقی منهای فروش حقیقی)", 12, false))
        card.addView(text("اختلاف بهترین خرید و فروش: ${fmtOrMissing(q.spreadPercent)}٪", 12, false))
        card.addView(text(
            "بازده NAV در ۵/۱۵/۳۰ روز معاملاتی: ${fmtOrMissing(q.return5)}٪ / ${fmtOrMissing(q.return15)}٪ / ${fmtOrMissing(q.return30)}٪",
            12, true
        ))
        card.addView(text(
            "درصدک حباب ۶۰روزه: ${fmtOrMissing(q.premiumPercentile60)}٪ | تاریخچه جفت‌شده: ${q.pairedHistoryCount} روز",
            12, true
        ))
        card.addView(text("وضعیت تاریخچه: ${q.historyStatus}", 11, false))
        card.addView(text("توجه: خالص خرید حقیقی، اختلاف ارزش خرید و فروش حقیقی در معاملات ثانویه است و معادل ورود/خروج وجه نقد از صندوق از محل صدور و ابطال واحدها نیست.", 11, false))
        results.addView(card, matchWrap())
    }

    private fun money(value: Double?): String =
        if (value == null) "فاقد داده" else NumberFormat.getNumberInstance(Locale("fa", "IR"))
            .apply { maximumFractionDigits = 0 }.format(value)

    private fun fmt(value: Double): String = NumberFormat.getNumberInstance(Locale("fa", "IR"))
        .apply { maximumFractionDigits = 2 }.format(value)

    private fun fmtOrMissing(value: Double?): String = value?.let { fmt(it) } ?: "فاقد داده"

    private fun text(value: String, size: Int, bold: Boolean, gravityValue: Int = Gravity.START) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(if (bold) ink else muted)
            gravity = gravityValue
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
