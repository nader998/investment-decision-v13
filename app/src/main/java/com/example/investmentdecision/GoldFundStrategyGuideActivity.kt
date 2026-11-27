package com.example.investmentdecision

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GoldFundStrategyGuideActivity : Activity() {
    private val ink = Color.rgb(30, 41, 59)
    private val blue = Color.rgb(21, 101, 192)
    private val muted = Color.rgb(85, 98, 115)
    private val green = Color.rgb(22, 120, 75)
    private val red = Color.rgb(185, 55, 55)
    private val pageColor = Color.rgb(243, 246, 250)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(28))
            setBackgroundColor(pageColor)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }

        page.addView(text("راهنمای نوسان‌گیری صندوق‌های طلا", 22, true, ink, Gravity.CENTER))
        page.addView(text(
            "این راهنما منطق فعلی نسخه برنامه را توضیح می‌دهد. سیگنال‌ها غربال‌گر و نامزد بررسی هستند؛ دستور قطعی معامله یا تضمین سود نیستند.",
            13, false, muted
        ))

        section(page, "۱) برنامه چه زمانی اصلاً سیگنال می‌دهد؟", blue,
            "فقط صندوق‌هایی وارد رتبه‌بندی خودکار می‌شوند که داده تازه و کامل داشته باشند: قیمت بازار و NAV معتبر، بازده NAV در ۵/۱۵/۳۰ روز معاملاتی، درصدک حباب ۶۰روزه، ارزش معاملات، اسپرد و دست‌کم ۶۰ مشاهده روزانه جفت‌شده قیمت و NAV. تاریخ داده نیز باید معتبر و تازه باشد.",
            "حداقل ۵ صندوق واجد شرایط لازم است. اگر کمتر از ۵ صندوق داده کامل داشته باشند، برنامه رتبه‌بندی و سیگنال صادر نمی‌کند. داده ناقص نباید به‌جای صفر تفسیر شود."
        )

        section(page, "۲) سیگنال «نامزد بررسی خرید»", green,
            "این برچسب فقط وقتی صادر می‌شود که همه شرط‌های زیر هم‌زمان برقرار باشند:",
            "• درصدک حباب ۶۰روزه حداکثر ۲۵ باشد؛ یعنی حباب فعلی نسبت به تاریخچه همان صندوق در یک‌چهارم پایین توزیع قرار گرفته باشد.\n• امتیاز کل رتبه‌بندی حداقل ۸۰ از ۱۰۰ باشد.\n• رتبه مومنتوم بازده ۵روزه حداقل ۵۰ باشد؛ یعنی صندوق در نیمه بالاتر مجموعه صندوق‌های واجد شرایط قرار بگیرد.\n• امتیاز نقدشوندگی حداقل ۷٫۵ از ۱۵ باشد."
        )
        section(page, "پس از دیدن نامزد خرید چه کار کنیم؟", green,
            "نامزد خرید به‌تنهایی مجوز خرید نیست. برنامه ابتدا دو صندوق برترِ واجد شرایط را برای جایگاه اول و دوم خرید مشخص می‌کند؛ سقف بودجه تعریف‌شده در کد فعلی به‌ترتیب ۲۰۰ و ۱۰۰ میلیون تومان است و ۲۰۰ میلیون تومان ذخیره نقد می‌ماند.",
            "در دستور خرید فعلی، قیمت بازار باید مساوی یا کمتر از NAV باشد؛ اگر قیمت بالاتر از NAV باشد، برنامه می‌گوید فعلاً خرید نکن. سفارش فقط محدود و حداکثر تا NAV در نظر گرفته شده است. سطوح ورود اول و دوم، حد ضرر و اهداف سود از حمایت/مقاومت و میانگین حرکت قیمت محاسبه می‌شوند؛ این سطوح تخمینی‌اند و بک‌تست نشده‌اند. خرید تعقیبی پس از جهش قیمت انجام نده."
        )

        section(page, "۳) سیگنال «نامزد بررسی فروش/خروج»", red,
            "این برچسب وقتی صادر می‌شود که همه شرط‌های زیر هم‌زمان برقرار باشند:",
            "• درصدک حباب ۶۰روزه حداقل ۷۵ باشد؛ یعنی حباب در یک‌چهارم بالای محدوده تاریخی همان صندوق قرار گرفته باشد.\n• امتیاز کل رتبه‌بندی کمتر از ۴۵ از ۱۰۰ باشد.\n• امتیاز بازده نسبی کمتر از ۱۰ از ۲۵ باشد."
        )
        section(page, "پس از دیدن نامزد خروج چه کار کنیم؟", red,
            "خرید جدید آن صندوق را متوقف و وضعیت دارایی موجود را بررسی کن. کارت فروش فقط نقاط خروج را نشان می‌دهد، نه نقاط ورود خرید. نقطه خروج اول در نسخه فعلی قیمت فعلی بازار است؛ نقطه دوم فقط وقتی نمایش داده می‌شود که مقاومت ۲۰روزه بالاتر از قیمت فعلی باشد.",
            "برنامه تعداد واحد و قیمت تمام‌شده دارایی شخصی تو را نمی‌داند؛ بنابراین این نقاط، پیشنهاد عمومی خروج پله‌ای‌اند و سفارش فروش شخصی‌سازی‌شده نیستند. صف فروش، کارمزد، شکاف قیمت و امکان اجرای سفارش را پیش از اقدام بررسی کن."
        )

        section(page, "۴) «زیر نظر؛ هنوز سیگنال کامل نیست»", blue,
            "اگر امتیاز کل حداقل ۶۵ باشد، اما شرط‌های کامل خرید یا فروش برقرار نباشند، صندوق در وضعیت زیر نظر قرار می‌گیرد. این وضعیت به معنی خرید یا فروش نیست؛ فقط یعنی صندوق ارزش پیگیری دارد و باید منتظر تغییر داده‌ها و تکمیل شروط ماند."
        )
        section(page, "۵) «عدم معامله»", muted,
            "اگر صندوق واجد شرایط داده باشد اما هیچ‌یک از قواعد خرید، فروش یا زیرنظر برقرار نباشد، وضعیت عدم معامله نمایش داده می‌شود. در این حالت برنامه ورود یا خروج جدید پیشنهاد نمی‌کند و حفظ نقدینگی می‌تواند انتخاب منضبط‌تری باشد."
        )
        section(page, "۶) امتیاز رتبه‌بندی از چه ساخته می‌شود؟", blue,
            "امتیاز نهایی از چهار جزء قابل‌اندازه‌گیری ساخته می‌شود و سپس برای مقیاس ۱۰۰ نرمال می‌شود:",
            "• حباب تاریخی: حداکثر ۳۰ امتیاز؛ درصدک‌های پایین‌تر امتیاز بیشتری می‌گیرند: تا ۱۰=۳۰، تا ۲۵=۲۴، تا ۵۰=۱۵، تا ۷۵=۷ و بالاتر از ۷۵=صفر.\n• عملکرد میان‌مدت: ۲۵٪ رتبه نسبی میانگین بازده NAV در ۱۵ و ۳۰ روز.\n• مومنتوم: ۲۰٪ رتبه نسبی بازده NAV پنج‌روزه.\n• نقدشوندگی: ۱۵٪؛ ترکیبی برابر از رتبه ارزش معاملات بالاتر و رتبه اسپرد پایین‌تر."
        )
        section(page, "۷) مواردی که در امتیاز فعلی وارد نمی‌شوند", muted,
            "خالص خرید حقیقی معاملات ثانویه ممکن است جداگانه نمایش داده شود، اما در امتیاز فعلی لحاظ نشده است. جریان نقدی صدور و ابطال واحدهای صندوق نیز با خالص خرید حقیقی سهامداران در معاملات ثانویه یکی نیست و از منبع فعلی تأیید نشده؛ بنابراین نباید آن دو را با هم اشتباه گرفت."
        )
        section(page, "۸) سطوح ورود، حد ضرر و اهداف سود چگونه برآورد می‌شوند؟", blue,
            "برای محاسبه سطوح تکنیکال حداقل ۲۰ قیمت پایانی معتبر لازم است. حمایت و مقاومت از کمترین و بیشترین قیمت ۲۰ جلسه اخیر گرفته می‌شوند و میانگین حرکت مطلق قیمت در ۱۵ مشاهده اخیر برای برآورد نوسان به کار می‌رود.",
            "ورود اول و دوم به‌صورت پله‌ای و پایین‌تر از قیمت جاری برآورد می‌شوند؛ حد ضرر زیر حمایت و ورود دوم قرار می‌گیرد. اهداف اول و دوم به‌ترتیب بر مبنای نسبت سود به ریسک ۱٫۵ و ۲٫۵ برابر محاسبه می‌شوند. اگر تاریخچه کافی یا سطوح معتبر نباشد، برنامه باید اعلام کند سطح عددی صادر نشده است؛ عدد حدسی جایگزین داده نمی‌شود."
        )
        section(page, "۹) چک‌لیست پیش از هر معامله", green,
            "۱. تاریخ و ساعت داده و تازه‌بودن قیمت/NAV را کنترل کن.\n۲. مطمئن شو صندوق حداقل تاریخچه لازم را دارد و داده‌ای با عنوان «داده پیدا نشد» وجود ندارد.\n۳. قیمت بازار را با NAV هم‌زمان مقایسه کن.\n۴. اسپرد، ارزش معاملات، صف و امکان اجرای سفارش را بررسی کن.\n۵. فقط در صورت برقرار بودن همه شروط سیگنال و تناسب اندازه موقعیت با ریسک اقدام کن.\n۶. حد ضرر را پیش از ورود مشخص کن و پس از خرید آن را بی‌دلیل دورتر نبر.\n۷. بعد از بسته‌شدن بازار و دریافت داده جدید، سیگنال را دوباره ارزیابی کن."
        )

        page.addView(text(
            "محدودیت مهم: آستانه‌های بالا قواعد برنامه‌نویسی فعلی هستند، نه قوانینی که سودآوری آن‌ها اثبات شده باشد. تا زمانی که بک‌تست خارج از نمونه، لحاظ‌کردن کارمزد و لغزش قیمت و آزمون اجرای واقعی انجام نشده، از این خروجی به‌عنوان ابزار کمکی استفاده کن و همه سرمایه را بر اساس یک سیگنال وارد نکن.",
            12, true, red
        ))
        page.addView(Button(this).apply {
            text = "بازگشت به تحلیل صندوق‌ها"
            isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(16)
        })
        setContentView(scroll)
    }

    private fun section(parent: LinearLayout, title: String, accent: Int, vararg paragraphs: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        card.addView(text(title, 16, true, accent))
        paragraphs.forEach { card.addView(text(it, 13, false, ink)) }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(9)
        })
    }

    private fun text(value: String, size: Int, bold: Boolean, color: Int, gravity: Int = Gravity.RIGHT): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = gravity
            textDirection = View.TEXT_DIRECTION_RTL
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(5))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
