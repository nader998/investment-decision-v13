package com.example.investmentdecision

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var gold: EditText
    private lateinit var silver: EditText
    private lateinit var coin: EditText
    private lateinit var ounce: EditText
    private lateinit var dollar: EditText
    private lateinit var dirham: EditText
    private lateinit var resultArea: LinearLayout
    private lateinit var fetchStatus: TextView

    // Keep the exact fetched ounce price while displaying only its integer part.
    private var exactOuncePrice: Double? = null

    private val prefs by lazy {
        getSharedPreferences("investment_inputs", MODE_PRIVATE)
    }

    private val ink = Color.rgb(30, 41, 59)
    private val blue = Color.rgb(21, 101, 192)
    private val muted = Color.rgb(100, 116, 139)
    private val pageColor = Color.rgb(243, 246, 250)
    private val lineColor = Color.rgb(226, 232, 240)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pageColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        page.addView(TextView(this).apply {
            text = "تصمیم‌یار سرمایه‌گذاری"
            textSize = 25f
            setTextColor(ink)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(5))
        })

        page.addView(TextView(this).apply {
            text = "محاسبه چهار قانون معاملاتی شخصی شما"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val inputsCard = card()
        inputsCard.addView(sectionTitle("قیمت‌های ورودی"))

        inputsCard.addView(TextView(this).apply {
            text = "قیمت‌ها را از یک منبع و با واحد پولی یکسان وارد کن. مقادیر پس از محاسبه روی همین گوشی ذخیره می‌شوند."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(8))
        })

        gold = input(
            inputsCard,
            "هر گرم طلای ۱۸ عیار",
            "مثلاً قیمت بازار ایران"
        )

        silver = input(
            inputsCard,
            "هر گرم نقره",
            "واحد قیمت نقره باید با طلا سازگار باشد"
        )

        coin = input(
            inputsCard,
            "سکه امامی جدید",
            "قیمت یک عدد سکه"
        )

        ounce = input(
            inputsCard,
            "اونس جهانی طلا",
            "معمولاً به دلار آمریکا"
        )

        dollar = input(
            inputsCard,
            "قیمت دلار در سایت",
            "از همان منبع قیمت درهم"
        )

        dirham = input(
            inputsCard,
            "قیمت درهم در سایت",
            "از همان منبع قیمت دلار"
        )

        page.addView(inputsCard, matchWrap())

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(12), 0, dp(2))
        }

        buttons.addView(Button(this).apply {
            text = "محاسبه تصمیم‌ها"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener {
                calculate()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginEnd = dp(6)
        })

        buttons.addView(Button(this).apply {
            text = "پاک‌کردن"
            textSize = 14f
            isAllCaps = false
            setOnClickListener {
                clearInputs()
            }
        }, LinearLayout.LayoutParams(dp(100), dp(52)))

        page.addView(buttons)

        page.addView(Button(this).apply {
            text = "راهنمای چهار قانون معاملاتی"
            textSize = 14f
            isAllCaps = false
            setOnClickListener {
                showRulesPage()
            }
        }, matchWrap())

        page.addView(Button(this).apply {
            text = "نوسان‌گیری صندوق‌های طلا"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 120, 75))
            setOnClickListener {
                startActivity(
                    android.content.Intent(
                        this@MainActivity,
                        GoldFundStrategyActivity::class.java
                    )
                )
            }
        }, matchWrap())

        page.addView(Button(this).apply {
            text = "دریافت قیمت‌های جدید از TGJU"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 163, 74))
            setOnClickListener {
                fetchPrices()
            }
        }, matchWrap())

        fetchStatus = TextView(this).apply {
            text = "برای دریافت آخرین قیمت‌های منتشرشده، دکمه سبز را بزن."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(3), dp(4), dp(8))
        }

        page.addView(fetchStatus)

        resultArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val resultCard = card()
        resultCard.addView(sectionTitle("نتیجه تحلیل"))

        resultCard.addView(TextView(this).apply {
            text = "بعد از وارد کردن هر شش قیمت، «محاسبه تصمیم‌ها» را بزن."
            textSize = 14f
            setTextColor(muted)
        })

        resultArea.addView(resultCard, matchWrap())
        page.addView(resultArea)

        page.addView(TextView(this).apply {
            text = "هشدار: قیمت‌ها از فایل به‌روزشده توسط GitHub Actions دریافت می‌شوند؛ اگر دریافت قیمت ناموفق باشد، آن خانه را دستی پر کن. نتیجه تضمین سود یا توصیه مالی قطعی نیست."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(14), dp(4), 0)
        })

        restoreInputs()
        setContentView(scroll)
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            setBackgroundColor(Color.WHITE)
        }

    private fun sectionTitle(title: String) = TextView(this).apply {
        text = title
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
        setPadding(0, 0, 0, dp(8))
    }

    private fun input(
        parent: LinearLayout,
        title: String,
        helper: String
    ): EditText {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            setPadding(0, dp(7), 0, dp(1))
        })

        parent.addView(TextView(this).apply {
            text = helper
            textSize = 11f
            setTextColor(muted)
        })

        val field = EditText(this).apply {
            hint = "مقدار را وارد کن"
            textSize = 16f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(pageColor)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textAlignment = EditText.TEXT_ALIGNMENT_VIEW_END
        }

        parent.addView(
            field,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(3)
            }
        )

        addThousandsSeparator(
            field,
            allowDecimal = title == "اونس جهانی طلا"
        )

        return field
    }

    private fun addThousandsSeparator(
        field: EditText,
        allowDecimal: Boolean
    ) {
        field.addTextChangedListener(object : TextWatcher {
            private var formatting = false

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (formatting || editable == null) return

                val original = editable.toString()
                val cursor = field.selectionStart
                    .coerceAtLeast(0)
                    .coerceAtMost(original.length)

                val digitsBeforeCursor =
                    original.take(cursor).count { it.isDigit() }

                val normalized = original
                    .replace(",", "")
                    .replace("٬", "")
                    .replace("٫", ".")
                    .filter {
                        it.isDigit() ||
                            (allowDecimal && it == '.')
                    }

                if (normalized.isEmpty()) return

                val decimalAt =
                    if (allowDecimal) normalized.indexOf('.') else -1

                val integerPart =
                    if (decimalAt >= 0) {
                        normalized.substring(0, decimalAt)
                    } else {
                        normalized
                    }

                val decimalPart =
                    if (decimalAt >= 0) {
                        normalized.substring(decimalAt + 1)
                            .filter { it.isDigit() }
                    } else {
                        ""
                    }

                val integerValue = integerPart.map { ch ->
                    when (ch) {
                        in '۰'..'۹' ->
                            ('0'.code + ch.code - '۰'.code).toChar()

                        in '٠'..'٩' ->
                            ('0'.code + ch.code - '٠'.code).toChar()

                        else -> ch
                    }
                }.joinToString("")

                val groupedInteger =
                    integerValue.toLongOrNull()?.let {
                        NumberFormat.getIntegerInstance(
                            Locale("fa", "IR")
                        ).format(it)
                    } ?: integerValue

                val localizedDecimal =
                    if (decimalAt >= 0) "٫$decimalPart" else ""

                val formatted = groupedInteger + localizedDecimal

                if (formatted != original) {
                    formatting = true
                    field.setText(formatted)

                    var newCursor = 0
                    var seenDigits = 0

                    while (
                        newCursor < formatted.length &&
                        seenDigits < digitsBeforeCursor
                    ) {
                        if (formatted[newCursor].isDigit()) {
                            seenDigits++
                        }
                        newCursor++
                    }

                    field.setSelection(
                        newCursor.coerceIn(0, formatted.length)
                    )

                    formatting = false
                }
            }
        })
    }

    private fun showRulesPage() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(pageColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        page.addView(TextView(this).apply {
            text = "راهنمای چهار قانون معاملاتی"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ink)
            setPadding(0, dp(8), 0, dp(14))
        })

        page.addView(TextView(this).apply {
            text = "این صفحه منطق محاسبه و محدوده‌های هر قانون را توضیح می‌دهد. سیگنال‌ها بر اساس قیمت‌های ورودی هستند و سود را تضمین نمی‌کنند."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(12))
        })

        addRuleCard(
            page,
            "قانون ۱: نسبت طلا به نقره",
            "فرمول: قیمت هر گرم طلای ۱۸ عیار ÷ قیمت هر گرم نقره",
            """اگر نسبت بیشتر از ۸۵ باشد: طلا را بفروش و به نقره تبدیل کن.
اگر نسبت کمتر از ۶۵ باشد: نقره را بفروش و به طلا تبدیل کن.
اگر نسبت بین ۶۵ تا ۸۵ (با احتساب دو سر بازه) باشد: سیگنالی صادر نمی‌شود."""
        )

        addRuleCard(
            page,
            "قانون ۲: نسبت سکه به طلای ۱۸ عیار",
            "فرمول: قیمت سکه امامی ÷ قیمت یک گرم طلای ۱۸ عیار",
            """اگر نسبت از ۱۰٫۳ تا ۱۰٫۸ باشد: طلا را بفروش و سکه بخر.
اگر نسبت از ۱۱٫۸ تا ۱۲٫۸ باشد: سکه را بفروش و به طلا تبدیل کن.
در سایر محدوده‌ها: سیگنالی صادر نمی‌شود."""
        )

        addRuleCard(
            page,
            "قانون ۳: ارزش محاسباتی طلا",
            "فرمول: قیمت اونس جهانی × قیمت دلار سایت ÷ ۴۱٫۴۵",
            """اگر ارزش محاسباتی بیشتر از قیمت بازار طلای ۱۸ عیار باشد: طلا بخر.
اگر ارزش محاسباتی کمتر از قیمت بازار باشد: طلا بفروش.
اگر دو مقدار برابر باشند: نگهداری کن."""
        )

        addRuleCard(
            page,
            "قانون ۴: دلار محاسباتی از درهم",
            "فرمول: قیمت درهم سایت × ۳٫۶۷۲",
            """اگر دلار محاسباتی از قیمت دلار سایت بیشتر از حد اختلاف مجاز باشد: دلار بخر.
اگر قیمت دلار سایت بیشتر از مقدار محاسباتی و اختلاف از حد مجاز بیشتر باشد: دلار بفروش.
اگر اختلاف حداکثر ۰٫۵٪ باشد: نگهداری کن."""
        )

        page.addView(Button(this).apply {
            text = "بازگشت به صفحه اصلی"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener {
                recreate()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply {
            topMargin = dp(8)
        })

        setContentView(scroll)
    }

    private fun addRuleCard(
        parent: LinearLayout,
        title: String,
        formula: String,
        explanation: String
    ) {
        val ruleCard = card()
        ruleCard.addView(sectionTitle(title))

        ruleCard.addView(TextView(this).apply {
            text = formula
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
            setPadding(0, 0, 0, dp(8))
        })

        ruleCard.addView(TextView(this).apply {
            text = explanation
            textSize = 14f
            setTextColor(ink)
            setLineSpacing(dp(4).toFloat(), 1f)
        })

        parent.addView(ruleCard, matchWrap())
    }

    private fun parse(field: EditText): Double? {
        val raw = field.text.toString().trim()
            .replace(",", "")
            .replace("٬", "")
            .replace("٫", ".")
            .map { ch ->
                when (ch) {
                    in '۰'..'۹' ->
                        ('0'.code + ch.code - '۰'.code).toChar()

                    in '٠'..'٩' ->
                        ('0'.code + ch.code - '٠'.code).toChar()

                    else -> ch
                }
            }
            .joinToString("")

        return raw.toDoubleOrNull()
            ?.takeIf { it > 0.0 && it.isFinite() }
    }

    private fun calculate() {
        val values = listOf(
            gold, silver, coin, ounce, dollar, dirham
        ).map { parse(it) }

        val labels = listOf(
            "طلای ۱۸ عیار",
            "نقره",
            "سکه امامی",
            "اونس جهانی",
            "دلار",
            "درهم"
        )

        val badIndex = values.indexOfFirst { it == null }

        if (badIndex >= 0) {
            Toast.makeText(
                this,
                "قیمت «${labels[badIndex]}» را با عددی بزرگ‌تر از صفر وارد کن.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val g = values[0]!!
        val s = values[1]!!
        val c = values[2]!!
        val displayedOunce = values[3]!!
        val savedExactOunce = exactOuncePrice
        val o = if (
            savedExactOunce != null &&
            displayedOunce == displayedOunce.toLong().toDouble() &&
            displayedOunce.toLong() == savedExactOunce.toLong()
        ) {
            savedExactOunce
        } else {
            // If the user changes the displayed ounce value manually,
            // use that new value instead of the previously fetched decimal.
            exactOuncePrice = null
            displayedOunce
        }
        val d = values[4]!!
        val a = values[5]!!

        prefs.edit()
            .putString("gold", gold.text.toString())
            .putString("silver", silver.text.toString())
            .putString("coin", coin.text.toString())
            .putString("ounce", ounce.text.toString())
            .putString(
                "exact_ounce",
                exactOuncePrice?.takeIf {
                    parse(ounce)?.let { visible ->
                        visible == visible.toLong().toDouble() &&
                            visible.toLong() == it.toLong()
                    } == true
                }?.toString() ?: ""
            )
            .putString("dollar", dollar.text.toString())
            .putString("dirham", dirham.text.toString())
            .apply()

        val gs = g / s
        val cg = c / g
        val theoreticalGold = o * d / 41.45
        val theoreticalDollar = a * 3.672
        val tolerance = d * 0.005

        resultArea.removeAllViews()

        val resultCard = card()
        resultCard.addView(sectionTitle("نتیجه تحلیل"))

        resultCard.addView(TextView(this).apply {
            text = "مقادیر زیر بر اساس قیمت‌هایی است که همین الان وارد کردی."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(8))
        })

        addResult(
            resultCard,
            "۱. نسبت طلا به نقره",
            fmt(gs),
            when {
                gs > 85.0 ->
                    "سیگنال: طلا را بفروش و به نقره تبدیل کن."

                gs < 65.0 ->
                    "سیگنال: نقره را بفروش و به طلا تبدیل کن."

                else ->
                    "سیگنالی صادر نمی‌شود؛ نسبت در محدوده خنثی ۶۵ تا ۸۵ است."
            }
        )

        addResult(
            resultCard,
            "۲. نسبت سکه به طلای ۱۸ عیار",
            fmt(cg),
            when {
                cg in 10.3..10.8 ->
                    "سیگنال: طلا را بفروش و سکه بخر."

                cg in 11.8..12.8 ->
                    "سیگنال: سکه را بفروش و به طلا تبدیل کن."

                else ->
                    "سیگنالی صادر نمی‌شود؛ نسبت خارج از بازه‌های تعریف‌شده است."
            }
        )

        addResult(
            resultCard,
            "۳. ارزش محاسباتی طلا",
            "محاسباتی: ${fmt(theoreticalGold)} | بازار: ${fmt(g)}",
            when {
                theoreticalGold > g ->
                    "سیگنال: بازار پایین‌تر از ارزش محاسباتی است؛ طلا بخر."

                theoreticalGold < g ->
                    "سیگنال: بازار بالاتر از ارزش محاسباتی است؛ طلا بفروش."

                else ->
                    "قیمت‌ها برابرند؛ طبق قانون تو فعلاً نگهداری کن."
            }
        )

        addResult(
            resultCard,
            "۴. دلار محاسباتی از درهم",
            "محاسباتی: ${fmt(theoreticalDollar)} | سایت: ${fmt(d)}",
            when {
                theoreticalDollar - d > tolerance ->
                    "سیگنال: دلار بخر."

                d - theoreticalDollar > tolerance ->
                    "سیگنال: دلار بفروش."

                else ->
                    "قیمت‌ها برابر یا نزدیک‌اند (اختلاف حداکثر ۰٫۵٪)؛ نگهداری کن."
            }
        )

        resultArea.addView(resultCard, matchWrap())
    }

    private fun addResult(
        parent: LinearLayout,
        title: String,
        value: String,
        decision: String
    ) {
        val separator = View(this).apply {
            setBackgroundColor(lineColor)
        }

        parent.addView(
            separator,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        parent.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })

        parent.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(3), 0, dp(5))
        })

        parent.addView(TextView(this).apply {
            text = decision
            textSize = 14f
            setTextColor(ink)
        })
    }

    private fun fetchPrices() {
        fetchStatus.text = "در حال دریافت قیمت‌ها..."

        Thread {
            try {
                val connection = (
                    URL(
                        "https://raw.githubusercontent.com/nader998/investment-prices/main/prices.json"
                    ).openConnection() as HttpURLConnection
                ).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty(
                        "User-Agent",
                        "InvestmentDecisionAndroid/1.0"
                    )
                }

                val jsonText = try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException(
                            "HTTP ${connection.responseCode}"
                        )
                    }

                    connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                } finally {
                    connection.disconnect()
                }

                val json = JSONObject(jsonText)

                val fields = listOf(
                    "gold18" to gold,
                    "silver" to silver,
                    "emamiCoin" to coin,
                    "ounce" to ounce,
                    "dollar" to dollar,
                    "dirham" to dirham
                )

                runOnUiThread {
                    var updated = 0
                    val failed = mutableListOf<String>()

                    for ((key, field) in fields) {
                        if (!json.isNull(key) && json.has(key)) {
                            val price = json.optDouble(
                                key,
                                Double.NaN
                            )

                            if (price.isFinite() && price > 0.0) {
                                if (key == "ounce") {
                                    // Keep exact decimal for calculations,
                                    // but display only the integer part.
                                    exactOuncePrice = price
                                    field.setText(price.toLong().toString())
                                } else {
                                    field.setText(price.toLong().toString())
                                }
                                updated++
                            } else {
                                field.setText("")
                                failed.add(key)
                            }
                        } else {
                            field.setText("")
                            failed.add(key)
                        }
                    }

                    prefs.edit()
                        .putString("gold", gold.text.toString())
                        .putString("silver", silver.text.toString())
                        .putString("coin", coin.text.toString())
                        .putString("ounce", ounce.text.toString())
                        .putString(
                            "exact_ounce",
                            exactOuncePrice?.toString() ?: ""
                        )
                        .putString("dollar", dollar.text.toString())
                        .putString("dirham", dirham.text.toString())
                        .apply()

                    fetchStatus.text = if (failed.isEmpty()) {
                        "هر ۶ قیمت دریافت شد. برای محاسبه، «محاسبه تصمیم‌ها» را بزن."
                    } else {
                        "$updated قیمت دریافت شد؛ ${failed.size} قیمت ناموفق بود. خانه‌های خالی را دستی وارد کن."
                    }

                    Toast.makeText(
                        this,
                        fetchStatus.text,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    listOf(
                        gold, silver, coin, ounce, dollar, dirham
                    ).forEach {
                        it.setText("")
                    }

                    prefs.edit().clear().apply()
                    exactOuncePrice = null

                    fetchStatus.text =
                        "دریافت قیمت ناموفق بود؛ قیمت‌های ذخیره‌شده پاک شدند تا قدیمی تلقی نشوند. قیمت‌ها را دستی وارد کن."

                    Toast.makeText(
                        this,
                        "دریافت قیمت ناموفق بود",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun restoreInputs() {
        gold.setText(prefs.getString("gold", "") ?: "")
        silver.setText(prefs.getString("silver", "") ?: "")
        coin.setText(prefs.getString("coin", "") ?: "")
        ounce.setText(prefs.getString("ounce", "") ?: "")
        exactOuncePrice = prefs.getString("exact_ounce", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 && it.isFinite() }
            ?.takeIf { exact ->
                parse(ounce)?.let { visible ->
                    visible == visible.toLong().toDouble() &&
                        visible.toLong() == exact.toLong()
                } == true
            }
        dollar.setText(prefs.getString("dollar", "") ?: "")
        dirham.setText(prefs.getString("dirham", "") ?: "")
    }

    private fun clearInputs() {
        listOf(
            gold, silver, coin, ounce, dollar, dirham
        ).forEach {
            it.setText("")
        }

        prefs.edit().clear().apply()
        exactOuncePrice = null
        resultArea.removeAllViews()

        val resultCard = card()
        resultCard.addView(sectionTitle("نتیجه تحلیل"))

        resultCard.addView(TextView(this).apply {
            text = "ورودی‌ها پاک شدند. قیمت‌های جدید را وارد کن."
            textSize = 14f
            setTextColor(muted)
        })

        resultArea.addView(resultCard, matchWrap())
    }

    private fun fmt(value: Double): String =
        NumberFormat.getNumberInstance(
            Locale("fa", "IR")
        ).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }.format(value)

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
