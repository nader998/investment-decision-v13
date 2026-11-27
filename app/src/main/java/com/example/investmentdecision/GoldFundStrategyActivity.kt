package com.example.investmentdecision

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class GoldFundStrategyActivity : Activity() {

    private data class Fund(
        val name: String,
        val asOf: String,
        val price: Double,
        val nav: Double,
        val r5: Double,
        val r15: Double,
        val r30: Double,
        val premiumPercentile: Double,
        val volume: Double,
        val spread: Double,
        val flowPct: Double,
        val dataComplete: Boolean
    ) {
        val premium: Double get() = (price / nav - 1.0) * 100.0
        val mediumReturn: Double get() = (r15 + r30) / 2.0
    }

    private data class ScoredFund(
        val fund: Fund,
        val score: Double,
        val performanceScore: Double,
        val momentumScore: Double,
        val liquidityScore: Double,
        val flowScore: Double,
        val action: String
    )

    private val funds = mutableListOf<Fund>()
    private lateinit var listArea: LinearLayout
    private lateinit var resultArea: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var asOfInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var navInput: EditText
    private lateinit var r5Input: EditText
    private lateinit var r15Input: EditText
    private lateinit var r30Input: EditText
    private lateinit var premiumPercentileInput: EditText
    private lateinit var volumeInput: EditText
    private lateinit var spreadInput: EditText
    private lateinit var flowInput: EditText
    private lateinit var autoImportArea: LinearLayout

    private val prefs by lazy {
        getSharedPreferences("gold_fund_strategy", MODE_PRIVATE)
    }

    private val ink = Color.rgb(30, 41, 59)
    private val blue = Color.rgb(21, 101, 192)
    private val muted = Color.rgb(100, 116, 139)
    private val pageColor = Color.rgb(243, 246, 250)
    private val green = Color.rgb(22, 120, 75)
    private val red = Color.rgb(185, 55, 55)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(28))
            setBackgroundColor(pageColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        page.addView(label("نوسان‌گیری صندوق‌های طلا", 23, true, Gravity.CENTER))
        page.addView(label(
            "تحلیل نسبی صندوق‌ها بر اساس حباب، بازدهی، مومنتوم، نقدشوندگی و جریان پول",
            13, false, Gravity.CENTER
        ).apply { setPadding(0, 0, 0, dp(12)) })

        val capitalCard = card()
        capitalCard.addView(label("تنظیمات سرمایه و ریسک", 18, true))
        capitalCard.addView(label("سرمایه مبنا: ۵۰۰ میلیون تومان", 15, true))
        capitalCard.addView(label(
            "موقعیت اول: ۲۰۰ میلیون | موقعیت دوم: ۱۰۰ میلیون | ذخیره نقد: ۲۰۰ میلیون تومان",
            13, false
        ))
        capitalCard.addView(label(
            "سقف زیان آزمایشی هر معامله: ۲٫۵ میلیون تومان | سقف زیان روزانه: ۵ میلیون تومان",
            13, false
        ))
        capitalCard.addView(label(
            "این ارقام تنظیم اولیه‌اند؛ حد ضرر واقعی باید با قیمت قابل معامله، صف، شکاف قیمت و هزینه‌ها کنترل شود.",
            12, false
        ))
        page.addView(capitalCard, matchWrap())

        val warning = card()
        warning.addView(label("وضعیت داده‌ها", 17, true))
        warning.addView(label(
            "قیمت بازار، NAV، ارزش معاملات و اسپرد از TSETMC قابل دریافت خودکار است. برخی شاخص‌ها مانند بازده NAV و جریان واقعی صدور/ابطال ممکن است از این مسیر در دسترس نباشند و باید فقط با منبع معتبر تکمیل شوند. قیمت و NAV باید مربوط به یک زمان و یک واحد باشند. بازده‌ها بر حسب درصد و برای روزهای معاملاتی هستند.",
            13, false
        ))
        warning.addView(label(
            "درصدک حباب باید از تاریخچه همان صندوق در ۶۰ روز معاملاتی اخیر محاسبه شده باشد؛ صفر یعنی در پایین‌ترین محدوده تاریخی و ۱۰۰ یعنی در بالاترین محدوده. زمان داده همه صندوق‌ها باید تا حد امکان یکسان باشد و ترکیب دارایی صندوق‌های مقایسه‌شده نیز باید مشابه باشد.",
            12, false
        ))
        page.addView(warning, matchWrap())

        page.addView(Button(this).apply {
            text = "دریافت خودکار اطلاعات صندوق‌های طلا"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener {
                startActivity(Intent(this@GoldFundStrategyActivity, GoldFundAutoDataActivity::class.java))
            }
        }, matchWrap())

        val importCard = card()
        importCard.addView(label("ورود اطلاعات دریافت‌شده به محاسبه‌گر", 17, true))
        importCard.addView(label(
            "پس از دریافت اطلاعات، صندوق را انتخاب کن. قیمت، NAV، ارزش معاملات، اسپرد، بازده NAV در ۵/۱۵/۳۰ روز و در صورت تکمیل تاریخچه درصدک ۶۰روزه وارد می‌شوند. جریان واقعی صدور/ابطال از این API قابل تأیید نیست و جداگانه باید وارد شود.",
            12, false
        ))
        autoImportArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        importCard.addView(autoImportArea)
        page.addView(importCard, matchWrap())

        page.addView(Button(this).apply {
            text = "تحلیل خودکار صندوق‌های دارای داده کامل"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            setOnClickListener { importCompleteAutoQuotes() }
        }, matchWrap())

        val form = card()
        form.addView(label("ثبت یا به‌روزرسانی اطلاعات صندوق", 18, true))
        nameInput = addInput(form, "نام صندوق", "مثلاً نام دقیق نماد/صندوق", false)
        asOfInput = addInput(form, "تاریخ و ساعت داده‌ها", "مثلاً ۱۴۰۵/۰۸/۲۸ - ۱۲:۱۵", false)
        priceInput = addInput(form, "قیمت بازار هر واحد (تومان)", "قیمت قابل معامله", true)
        navInput = addInput(form, "NAV هر واحد (تومان)", "NAV مربوط به همان زمان یا آخرین NAV معتبر", true)
        r5Input = addInput(form, "بازده NAV در ۵ روز معاملاتی (%)", "مثلاً ۲٫۵ یا ۱٫۲-", true)
        r15Input = addInput(form, "بازده NAV در ۱۵ روز معاملاتی (%)", "درصد", true)
        r30Input = addInput(form, "بازده NAV در ۳۰ روز معاملاتی (%)", "درصد", true)
        premiumPercentileInput = addInput(form, "درصدک حباب در تاریخچه ۶۰ روزه (۰ تا ۱۰۰)", "از تاریخچه همان صندوق", true)
        volumeInput = addInput(form, "ارزش معاملات روزانه (تومان)", "عدد ارزش معاملات روز", true)
        spreadInput = addInput(form, "اختلاف بهترین خرید و فروش (%)", "Spread؛ درصد", true)
        flowInput = addInput(form, "خالص ورود پول روزانه به درصد ارزش صندوق (%)", "درصد از NAV کل صندوق؛ مثبت=ورود، منفی=خروج", true)

        form.addView(Button(this).apply {
            text = "ثبت صندوق"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { addOrUpdateFund() }
        }, matchWrap())

        page.addView(form, matchWrap())

        val listCard = card()
        listCard.addView(label("صندوق‌های ثبت‌شده", 18, true))
        listArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        listCard.addView(listArea)
        page.addView(listCard, matchWrap())

        page.addView(Button(this).apply {
            text = "محاسبه رتبه‌بندی و سیگنال‌ها"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            setOnClickListener { analyze() }
        }, matchWrap())

        resultArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(resultArea)

        page.addView(Button(this).apply {
            text = "بازگشت به صفحه اصلی"
            isAllCaps = false
            setOnClickListener { finish() }
        }, matchWrap())

        loadFunds()
        renderFunds()
        setContentView(scroll)
        renderAutoImportOptions()
    }

    override fun onResume() {
        super.onResume()
        if (::autoImportArea.isInitialized) renderAutoImportOptions()
    }

    private fun renderAutoImportOptions() {
        if (!::autoImportArea.isInitialized) return
        autoImportArea.removeAllViews()
        val cache = getSharedPreferences("tsetmc_fund_cache", MODE_PRIVATE)
        val raw = cache.getString("last_quotes", null)
        if (raw.isNullOrBlank()) {
            autoImportArea.addView(label(
                "هنوز داده‌ای دریافت نشده است. ابتدا دکمه دریافت خودکار اطلاعات را بزن.",
                12, false
            ))
            return
        }
        try {
            val quotes = JSONArray(raw)
            if (quotes.length() == 0) {
                autoImportArea.addView(label("فهرست داده‌های ذخیره‌شده خالی است.", 12, false))
                return
            }
            val history = GoldFundHistoryStore(this)
            for (i in 0 until quotes.length()) {
                val item = quotes.optJSONObject(i) ?: continue
                val symbol = item.optString("symbol").ifBlank { item.optString("name") }
                val name = item.optString("name").ifBlank { symbol }
                val insCode = item.optString("insCode")
                val price = item.optDouble("marketPriceToman", Double.NaN)
                val nav = item.optDouble("navToman", Double.NaN)
                val volume = item.optDouble("tradeValueToman", Double.NaN)
                val spread = item.optDouble("spreadPercent", Double.NaN)
                val date = item.optString("date", "تاریخ نامشخص")
                val percentile = if (insCode.isNotBlank()) history.percentile60(insCode) else null
                val button = Button(this).apply {
                    text = "وارد کردن ${symbol} — قیمت ${if (price.isFinite()) fmt(price) else "ناموجود"}"
                    isAllCaps = false
                    textSize = 13f
                    setOnClickListener {
                        nameInput.setText(name)
                        asOfInput.setText(date)
                        priceInput.setText(if (price.isFinite() && price > 0.0) price.toString() else "")
                        navInput.setText(if (nav.isFinite() && nav > 0.0) nav.toString() else "")
                        volumeInput.setText(if (volume.isFinite() && volume >= 0.0) volume.toString() else "")
                        spreadInput.setText(if (spread.isFinite() && spread >= 0.0) spread.toString() else "")
                        val r5 = item.optDouble("return5", Double.NaN)
                        val r15 = item.optDouble("return15", Double.NaN)
                        val r30 = item.optDouble("return30", Double.NaN)
                        val cachedPercentile = item.optDouble("premiumPercentile60", Double.NaN)
                        premiumPercentileInput.setText(
                            if (cachedPercentile.isFinite()) cachedPercentile.toString()
                            else percentile?.toString() ?: ""
                        )
                        r5Input.setText(if (r5.isFinite()) r5.toString() else "")
                        r15Input.setText(if (r15.isFinite()) r15.toString() else "")
                        r30Input.setText(if (r30.isFinite()) r30.toString() else "")
                        // Real subscription/redemption cash flow is not supplied by these TSETMC endpoints.
                        flowInput.text.clear()
                        Toast.makeText(
                            this@GoldFundStrategyActivity,
                            "قیمت، NAV و بازده‌های قابل‌محاسبه وارد شد؛ جریان واقعی صدور/ابطال همچنان نیازمند منبع جداگانه است.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                autoImportArea.addView(button, matchWrap())
                autoImportArea.addView(label(
                    "NAV: ${if (nav.isFinite()) fmt(nav) else "ناموجود"} تومان | تاریخچه جفت‌شده: ${history.pairedObservationCount(insCode)} از ۶۰ روز | درصدک: ${percentile?.let { fmt(it) } ?: "ناموجود"}",
                    11, false
                ))
            }
        } catch (_: Exception) {
            autoImportArea.addView(label(
                "خواندن داده‌های ذخیره‌شده ممکن نشد؛ دوباره از صفحه دریافت اطلاعات، داده‌ها را دریافت کن.",
                12, false
            ))
        }
    }

    /**
     * Imports only real, non-null automatic quote fields. Older/manual records
     * are marked ineligible for this automatic run so stale values cannot create
     * a buy/sell candidate alongside the newly fetched market snapshot.
     */
    private fun importCompleteAutoQuotes() {
        val raw = getSharedPreferences("tsetmc_fund_cache", MODE_PRIVATE)
            .getString("last_quotes", null)
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "ابتدا اطلاعات خودکار صندوق‌ها را دریافت کن.", Toast.LENGTH_LONG).show()
            return
        }

        val imported = mutableListOf<Fund>()
        var incomplete = 0
        try {
            val quotes = JSONArray(raw)
            for (i in 0 until quotes.length()) {
                val item = quotes.optJSONObject(i) ?: continue
                fun value(key: String): Double? =
                    if (item.isNull(key)) null
                    else item.optDouble(key, Double.NaN).takeIf { it.isFinite() }

                val name = item.optString("name").ifBlank { item.optString("symbol") }
                val asOf = item.optString("date")
                val price = value("marketPriceToman")
                val nav = value("navToman")
                val r5 = value("return5")
                val r15 = value("return15")
                val r30 = value("return30")
                val percentile = value("premiumPercentile60")
                val volume = value("tradeValueToman")
                val spread = value("spreadPercent")
                val paired = item.optInt("pairedHistoryCount", 0)

                val complete = name.isNotBlank() && asOf.isNotBlank() &&
                    price != null && price > 0.0 &&
                    nav != null && nav > 0.0 &&
                    r5 != null && r15 != null && r30 != null &&
                    percentile != null && percentile in 0.0..100.0 &&
                    volume != null && volume >= 0.0 &&
                    spread != null && spread >= 0.0 &&
                    paired >= 60
                if (!complete) {
                    incomplete++
                    continue
                }

                imported.add(Fund(
                    name = item.optString("symbol").ifBlank { name },
                    asOf = asOf,
                    price = price!!,
                    nav = nav!!,
                    r5 = r5!!,
                    r15 = r15!!,
                    r30 = r30!!,
                    premiumPercentile = percentile!!,
                    volume = volume!!,
                    spread = spread!!,
                    // TSETMC individual investor trading flow is not fund
                    // subscription/redemption cash flow and is not scored.
                    flowPct = 0.0,
                    dataComplete = true
                ))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "خواندن داده‌های خودکار ممکن نشد؛ دوباره دریافت کن.", Toast.LENGTH_LONG).show()
            return
        }

        // Disable older records for this run; only the fresh, complete auto
        // snapshot can participate in the ranking and candidate signals.
        for (i in funds.indices) funds[i] = funds[i].copy(dataComplete = false)
        imported.forEach { fresh ->
            val existing = funds.indexOfFirst {
                it.name.equals(fresh.name, ignoreCase = true)
            }
            if (existing >= 0) funds[existing] = fresh else funds.add(fresh)
        }

        saveFunds()
        renderFunds()
        analyze()
        Toast.makeText(
            this,
            "صندوق کامل واردشده: ${imported.size} | داده ناکامل/فاقد تاریخچه ۶۰روزه: $incomplete",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun addInput(
        parent: LinearLayout,
        title: String,
        hintText: String,
        numeric: Boolean
    ): EditText {
        parent.addView(label(title, 13, true).apply {
            setPadding(0, dp(7), 0, dp(2))
        })

        val field = EditText(this).apply {
            hint = hintText
            textSize = 14f
            maxLines = 1
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            } else {
                InputType.TYPE_CLASS_TEXT
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundColor(pageColor)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }

        parent.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ).apply { bottomMargin = dp(3) })

        return field
    }

    private fun addOrUpdateFund() {
        val name = nameInput.text.toString().trim()
        val asOf = asOfInput.text.toString().trim()

        if (name.isBlank()) {
            showValidationError("نام صندوق خالی است.")
            nameInput.requestFocus()
            return
        }
        if (asOf.isBlank()) {
            showValidationError("تاریخ و ساعت داده‌ها خالی است.")
            asOfInput.requestFocus()
            return
        }

        fun requiredNumber(field: EditText, title: String): Double? {
            val raw = field.text.toString()
            if (raw.isBlank()) {
                showValidationError("$title خالی است.")
                field.requestFocus()
                return null
            }
            val value = number(field)
            if (value == null) {
                showValidationError("$title نامعتبر است؛ فقط عدد وارد کن.")
                field.requestFocus()
                return null
            }
            return value
        }

        val price = requiredNumber(priceInput, "قیمت بازار") ?: return
        val nav = requiredNumber(navInput, "NAV") ?: return
        fun optionalNumber(field: EditText, title: String): Double? {
            if (field.text.toString().isBlank()) return null
            val value = number(field)
            if (value == null) {
                showValidationError("$title نامعتبر است؛ فقط عدد وارد کن یا فیلد را خالی بگذار.")
                field.requestFocus()
            }
            return value
        }

        // Missing NAV returns must never be converted to numeric zero.
        // Zero is a valid measured return and must be distinguishable from missing data.
        val r5 = requiredNumber(r5Input, "بازده ۵ روزه") ?: return
        val r15 = requiredNumber(r15Input, "بازده ۱۵ روزه") ?: return
        val r30 = requiredNumber(r30Input, "بازده ۳۰ روزه") ?: return
        val percentile = optionalNumber(premiumPercentileInput, "درصدک حباب ۶۰ روزه") ?: 50.0
        if (premiumPercentileInput.text.isNotBlank() && number(premiumPercentileInput) == null) return
        val volume = optionalNumber(volumeInput, "ارزش معاملات روزانه") ?: 0.0
        if (volumeInput.text.isNotBlank() && number(volumeInput) == null) return
        val spread = optionalNumber(spreadInput, "اختلاف قیمت خرید و فروش") ?: 0.0
        if (spreadInput.text.isNotBlank() && number(spreadInput) == null) return
        val flow = optionalNumber(flowInput, "خالص ورود پول") ?: 0.0
        if (flowInput.text.isNotBlank() && number(flowInput) == null) return
        // Subscription/redemption flow is not available from the current verified source.
        // Do not make it mandatory; all six measurable strategy inputs below are required.
        val dataComplete = listOf(r5Input, r15Input, r30Input, premiumPercentileInput,
            volumeInput, spreadInput).all { it.text.toString().isNotBlank() }

        if (price <= 0.0) {
            showValidationError("قیمت بازار باید بزرگ‌تر از صفر باشد.")
            priceInput.requestFocus()
            return
        }
        if (nav <= 0.0) {
            showValidationError("NAV باید بزرگ‌تر از صفر باشد.")
            navInput.requestFocus()
            return
        }
        if (percentile !in 0.0..100.0) {
            showValidationError("درصدک حباب ۶۰ روزه باید بین صفر تا ۱۰۰ باشد.")
            premiumPercentileInput.requestFocus()
            return
        }
        if (volume < 0.0) {
            showValidationError("ارزش معاملات روزانه نمی‌تواند منفی باشد.")
            volumeInput.requestFocus()
            return
        }
        if (spread < 0.0) {
            showValidationError("اختلاف قیمت خرید و فروش نمی‌تواند منفی باشد.")
            spreadInput.requestFocus()
            return
        }

        val fund = Fund(
            name = name,
            asOf = asOf,
            price = price,
            nav = nav,
            r5 = r5,
            r15 = r15,
            r30 = r30,
            premiumPercentile = percentile,
            volume = volume,
            spread = spread,
            flowPct = flow,
            dataComplete = dataComplete
        )

        val existingIndex = funds.indexOfFirst {
            it.name.equals(name, ignoreCase = true)
        }

        if (existingIndex >= 0) {
            funds[existingIndex] = fund
            Toast.makeText(this, "اطلاعات صندوق به‌روزرسانی شد.", Toast.LENGTH_SHORT).show()
        } else {
            funds.add(fund)
            Toast.makeText(this, "صندوق ثبت شد.", Toast.LENGTH_SHORT).show()
        }

        saveFunds()
        renderFunds()
        clearInputs()
    }

    private fun showValidationError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearInputs() {
        nameInput.text.clear()
        asOfInput.text.clear()
        priceInput.text.clear()
        navInput.text.clear()
        r5Input.text.clear()
        r15Input.text.clear()
        r30Input.text.clear()
        premiumPercentileInput.text.clear()
        volumeInput.text.clear()
        spreadInput.text.clear()
        flowInput.text.clear()
    }

    private fun analyze() {
        resultArea.removeAllViews()

        if (funds.isEmpty()) {
            resultArea.addView(card().apply {
                addView(label("ابتدا حداقل یک صندوق را ثبت کن.", 14, true))
            }, matchWrap())
            return
        }

        val eligibleFunds = funds.filter { it.dataComplete }
        if (eligibleFunds.isEmpty()) {
            resultArea.addView(card().apply {
                addView(label("فعلاً هیچ صندوقی داده کامل ندارد؛ رتبه‌بندی و سیگنال صادر نشد.", 15, true))
                addView(label(
                    "برای تحلیل، بازده‌های NAV، درصدک ۶۰روزه، ارزش معاملات و اسپرد باید با داده واقعی تکمیل شوند. جریان واقعی صدور/ابطال هنوز منبع تأییدشده ندارد و در شرط تکمیل داده و امتیازدهی لحاظ نمی‌شود. خانه‌های خالی را صفر یا مقدار فرضی تلقی نکن.",
                    12, false
                ))
            }, matchWrap())
            return
        }

        val performanceRanks = rankScores(
            eligibleFunds.associate { it.name to it.mediumReturn }, true
        )
        val momentumRanks = rankScores(
            eligibleFunds.associate { it.name to it.r5 }, true
        )
        val volumeRanks = rankScores(eligibleFunds.associate { it.name to it.volume }, true)
        val spreadRanks = rankScores(eligibleFunds.associate { it.name to it.spread }, false)
        // Subscription/redemption cash flow is intentionally excluded until a verified
        // per-fund daily numeric source is integrated. TSETMC retail flow is a different metric.

        val scored = eligibleFunds.map { f ->
            val premiumScore = when {
                f.premiumPercentile <= 10.0 -> 30.0
                f.premiumPercentile <= 25.0 -> 24.0
                f.premiumPercentile <= 50.0 -> 15.0
                f.premiumPercentile <= 75.0 -> 7.0
                else -> 0.0
            }

            val performanceScore = (performanceRanks[f.name] ?: 0.0) * 0.25
            val momentumScore = (momentumRanks[f.name] ?: 0.0) * 0.20
            val liquidityScore =
                ((volumeRanks[f.name] ?: 0.0) * 0.5 +
                    (spreadRanks[f.name] ?: 0.0) * 0.5) * 0.15
            // Available-factor score is normalized to 100; no synthetic zero is assigned to missing flow.
            val flowScore = 0.0
            val total = (premiumScore + performanceScore +
                momentumScore + liquidityScore) * (100.0 / 90.0)

            val action = when {
                !f.dataComplete -> "داده ناقص؛ بدون سیگنال خرید/فروش"
                f.premiumPercentile <= 25.0 &&
                    total >= 80.0 &&
                    (momentumRanks[f.name] ?: 0.0) >= 50.0 &&
                    liquidityScore >= 7.5 ->
                    "نامزد بررسی خرید"

                f.premiumPercentile >= 75.0 &&
                    total < 45.0 &&
                    performanceScore < 10.0 ->
                    "نامزد بررسی فروش/خروج"

                total >= 65.0 -> "زیر نظر؛ هنوز سیگنال کامل نیست"
                else -> "عدم معامله"
            }

            ScoredFund(
                f, total, performanceScore, momentumScore,
                liquidityScore, flowScore, action
            )
        }.sortedByDescending { it.score }

        val explanation = card()
        explanation.addView(label("نتیجه تحلیل", 18, true))
        explanation.addView(label(
            "رتبه‌بندی بر اساس صندوق‌های ثبت‌شده در همین صفحه انجام شده است. امتیازها نسبی‌اند و با تغییر مجموعه صندوق‌ها ممکن است تغییر کنند.",
            12, false
        ))
        explanation.addView(label(
            "امتیاز کل از حباب تاریخی، بازده ۱۵/۳۰روزه، مومنتوم ۵روزه و نقدشوندگی محاسبه و به مقیاس ۱۰۰ تبدیل می‌شود. جریان واقعی صدور/ابطال فعلاً در امتیاز وارد نشده است.",
            12, false
        ))
        explanation.addView(label(
            "سیگنال‌ها فقط نامزد بررسی‌اند؛ پیش از سفارش باید زمان NAV، ترکیب دارایی صندوق‌ها، قیمت قابل اجرا، کارمزد، صف و لغزش قیمت بررسی شود.",
            12, false
        ))
        resultArea.addView(explanation, matchWrap())

        scored.forEachIndexed { index, item ->
            val f = item.fund
            val c = card()
            c.addView(label("${index + 1}. ${f.name}  |  امتیاز ${fmt(item.score)} از ۱۰۰", 16, true))
            c.addView(label(
                "زمان داده: ${f.asOf}",
                12, false
            ))
            c.addView(label(
                "حباب بازار نسبت به NAV: ${fmt(f.premium)}٪ | درصدک ۶۰روزه: ${fmt(f.premiumPercentile)}",
                13, false
            ))
            c.addView(label(
                "بازده ۵/۱۵/۳۰ روزه: ${fmt(f.r5)}٪ / ${fmt(f.r15)}٪ / ${fmt(f.r30)}٪",
                13, false
            ))
            c.addView(label(
                "امتیاز بازده نسبی: ${fmt(item.performanceScore)} از ۲۵ | مومنتوم: ${fmt(item.momentumScore)} از ۲۰",
                12, false
            ))
            c.addView(label(
                "نقدشوندگی: ${fmt(item.liquidityScore)} از ۱۵ | جریان پول: ${fmt(item.flowScore)} از ۱۰",
                12, false
            ))
            c.addView(label(item.action, 14, true).apply {
                setTextColor(
                    if (item.action.contains("خرید")) green
                    else if (item.action.contains("فروش")) red
                    else blue
                )
                setPadding(0, dp(5), 0, 0)
            })
            resultArea.addView(c, matchWrap())
        }

        val allocation = card()
        allocation.addView(label("مدیریت سرمایه ۵۰۰ میلیون تومانی", 17, true))
        val buyCandidates = scored.filter { it.action == "نامزد بررسی خرید" }

        allocation.addView(label(
            if (buyCandidates.isEmpty()) {
                "در حال حاضر هیچ صندوقی تمام شروط اولیه ورود را ندارد؛ طبق استراتژی، وجه نقد حفظ شود و معامله‌ای انجام نشود."
            } else {
                "نامزدهای بررسی خرید: ${buyCandidates.joinToString("، ") { it.fund.name }}. سقف برنامه‌ریزی اولیه: موقعیت اول ۲۰۰ میلیون و موقعیت دوم ۱۰۰ میلیون تومان؛ ۲۰۰ میلیون تومان ذخیره نقد باقی بماند. این ارقام دستور خرید نیستند."
            },
            13, false
        ))
        resultArea.addView(allocation, matchWrap())
    }

    private fun rankScores(
        values: Map<String, Double>,
        higherIsBetter: Boolean
    ): Map<String, Double> {
        val sorted = values.entries.sortedWith(
            if (higherIsBetter) compareBy { it.value }
            else compareByDescending { it.value }
        )

        val result = mutableMapOf<String, Double>()
        if (sorted.size <= 1) return values.keys.associateWith { 50.0 }

        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1].value == sorted[i].value) j++

            val averageRank = (i + j) / 2.0
            val score = averageRank / (sorted.size - 1).toDouble() * 100.0

            for (k in i..j) result[sorted[k].key] = score
            i = j + 1
        }

        return result
    }

    private fun renderFunds() {
        listArea.removeAllViews()

        if (funds.isEmpty()) {
            listArea.addView(label("هنوز صندوقی ثبت نشده است.", 13, false))
            return
        }

        funds.forEach { fund ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }

            row.addView(label(
                if (fund.dataComplete) {
                    "${fund.name} | ${fund.asOf} | حباب ${fmt(fund.premium)}٪ | درصدک ${fmt(fund.premiumPercentile)}"
                } else {
                    "${fund.name} | ${fund.asOf} | داده ناقص؛ فعلاً بدون رتبه و سیگنال"
                },
                12, true
            ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(Button(this).apply {
                text = "حذف"
                textSize = 11f
                isAllCaps = false
                setOnClickListener {
                    funds.removeAll { it.name == fund.name }
                    saveFunds()
                    renderFunds()
                    resultArea.removeAllViews()
                }
            })

            listArea.addView(row)
        }
    }

    private fun saveFunds() {
        val array = JSONArray()
        funds.forEach { f ->
            array.put(JSONObject().apply {
                put("name", f.name)
                put("asOf", f.asOf)
                put("price", f.price)
                put("nav", f.nav)
                put("r5", f.r5)
                put("r15", f.r15)
                put("r30", f.r30)
                put("premiumPercentile", f.premiumPercentile)
                put("volume", f.volume)
                put("spread", f.spread)
                put("flowPct", f.flowPct)
                put("dataComplete", f.dataComplete)
            })
        }
        prefs.edit().putString("funds", array.toString()).apply()
    }

    private fun loadFunds() {
        val raw = prefs.getString("funds", null) ?: return

        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                funds.add(Fund(
                    o.getString("name"),
                    o.optString("asOf", "زمان ثبت نشده"),
                    o.getDouble("price"),
                    o.getDouble("nav"),
                    o.getDouble("r5"),
                    o.getDouble("r15"),
                    o.getDouble("r30"),
                    o.getDouble("premiumPercentile"),
                    o.getDouble("volume"),
                    o.getDouble("spread"),
                    o.getDouble("flowPct"),
                    o.optBoolean("dataComplete", true)
                ))
            }
        } catch (_: Exception) {
            funds.clear()
        }
    }

    private fun number(field: EditText): Double? {
        val normalized = field.text.toString().trim()
            .replace("٬", "")
            .replace(",", "")
            .replace("٫", ".")
            .replace("،", "")
            .replace("−", "-")
            .replace("－", "-")
            .replace("＋", "+")
            .map { ch ->
                when (ch) {
                    in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                    in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                    else -> ch
                }
            }.joinToString("")
            .filterNot { it.isWhitespace() }

        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(Color.WHITE)
    }

    private fun label(
        value: String,
        size: Int,
        bold: Boolean,
        gravityValue: Int = Gravity.START
    ) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ink)
        gravity = gravityValue
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(2), 0, dp(3))
    }

    private fun fmt(value: Double): String =
        NumberFormat.getNumberInstance(Locale("fa", "IR")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }.format(value)

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
