package com.example.investmentdecision

import android.app.Activity
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
        val flowPct: Double
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
            "در این نسخه اطلاعات صندوق‌ها خودکار دریافت نمی‌شود. داده‌های واقعی و هم‌زمان را از منبع معتبر وارد کن. قیمت و NAV باید مربوط به یک زمان و یک واحد باشند. بازده‌ها بر حسب درصد و برای روزهای معاملاتی هستند.",
            13, false
        ))
        warning.addView(label(
            "درصدک حباب باید از تاریخچه همان صندوق در ۶۰ روز معاملاتی اخیر محاسبه شده باشد؛ صفر یعنی در پایین‌ترین محدوده تاریخی و ۱۰۰ یعنی در بالاترین محدوده. زمان داده همه صندوق‌ها باید تا حد امکان یکسان باشد و ترکیب دارایی صندوق‌های مقایسه‌شده نیز باید مشابه باشد.",
            12, false
        ))
        page.addView(warning, matchWrap())

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
            singleLine = true
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
        val price = number(priceInput)
        val nav = number(navInput)
        val r5 = number(r5Input)
        val r15 = number(r15Input)
        val r30 = number(r30Input)
        val percentile = number(premiumPercentileInput)
        val volume = number(volumeInput)
        val spread = number(spreadInput)
        val flow = number(flowInput)

        val asOf = asOfInput.text.toString().trim()
        if (name.isBlank() || asOf.isBlank() || price == null || nav == null || r5 == null ||
            r15 == null || r30 == null || percentile == null ||
            volume == null || spread == null || flow == null ||
            price <= 0.0 || nav <= 0.0 || volume < 0.0 || spread < 0.0 ||
            percentile !in 0.0..100.0
        ) {
            Toast.makeText(
                this,
                "همه فیلدها را با مقادیر معتبر تکمیل کن؛ درصدک حباب باید بین صفر و ۱۰۰ باشد.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val fund = Fund(
            name, asOf, price, nav, r5, r15, r30,
            percentile, volume, spread, flow
        )
        val oldIndex = funds.indexOfFirst { it.name.equals(name, true) }
        if (oldIndex >= 0) funds[oldIndex] = fund else funds.add(fund)
        saveFunds()
        renderFunds()
        listOf(
            nameInput, asOfInput, priceInput, navInput, r5Input, r15Input,
            r30Input, premiumPercentileInput, volumeInput, spreadInput, flowInput
        ).forEach { it.text.clear() }
        Toast.makeText(this, "اطلاعات صندوق ذخیره شد.", Toast.LENGTH_SHORT).show()
    }

    private fun analyze() {
        resultArea.removeAllViews()
        if (funds.size < 3) {
            resultArea.addView(card().apply {
                addView(label(
                    "برای رتبه‌بندی نسبی حداقل ۳ صندوق ثبت کن؛ با تعداد بیشتر، مقایسه معنادارتر می‌شود.",
                    14, true
                ))
            })
            return
        }

        val performanceRanks = rankScores(funds.associate { it.name to it.mediumReturn }, true)
        val momentumRanks = rankScores(funds.associate { it.name to it.r5 }, true)
        val volumeRanks = rankScores(funds.associate { it.name to it.volume }, true)
        val spreadRanks = rankScores(funds.associate { it.name to it.spread }, false)
        val flowRanks = rankScores(funds.associate { it.name to it.flowPct }, true)

        val scored = funds.map { f ->
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
            val flowScore = (flowRanks[f.name] ?: 0.0) * 0.10
            val total = premiumScore + performanceScore +
                momentumScore + liquidityScore + flowScore

            val action = when {
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
            "امتیاز کل = حباب تاریخی ۳۰ + بازده نسبی ۱۵/۳۰ روزه ۲۵ + مومنتوم ۵ روزه ۲۰ + نقدشوندگی ۱۵ + جریان پول ۱۰.",
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

    private fun rankScores(values: Map<String, Double>, higherIsBetter: Boolean): Map<String, Double> {
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
                "${fund.name} | ${fund.asOf} | حباب ${fmt(fund.premium)}٪ | درصدک ${fmt(fund.premiumPercentile)}",
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
                    o.getString("name"), o.optString("asOf", "زمان ثبت نشده"), o.getDouble("price"), o.getDouble("nav"),
                    o.getDouble("r5"), o.getDouble("r15"), o.getDouble("r30"),
                    o.getDouble("premiumPercentile"), o.getDouble("volume"),
                    o.getDouble("spread"), o.getDouble("flowPct")
                ))
            }
        } catch (_: Exception) {
            funds.clear()
        }
    }

    private fun number(field: EditText): Double? {
        val normalized = field.text.toString().trim()
            .replace(",", "")
            .replace("٬", "")
            .replace("٫", ".")
            .map { ch ->
                when (ch) {
                    in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                    in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                    else -> ch
                }
            }.joinToString("")
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
