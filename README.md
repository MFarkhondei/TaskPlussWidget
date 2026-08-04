# تسک پلاس ویجت (TaskPluss Widget)

ویجت صفحه اصلی اندروید برای مدیریت سریع تسک‌ها، متصل به بک‌اند Google Apps Script پروژه تسک پلاس.

## ویژگی‌ها

- **ظاهر مدرن تیره** مطابق پالت طراحی (طلایی، کارت‌دار، گوشه گرد)
- **فقط** `LinearLayout` + `TextView` + `ImageView` (سازگار با RemoteViews و سامسونگ One UI)
- دکمه **افزودن تسک** → باز کردن Activity با فیلد متنی دو خطی
- **چیپ گروه‌ها**: «همه» + حداکثر ۴ گروه سفارشی
  - گروه «همه»: مرتب‌سازی بر اساس تاریخ ثبت (جدید → قدیم)
  - گروه‌های دیگر: مرتب‌سازی بر اساس اولویت (بالا → پایین)
- کلیک روی چک‌باکس / ردیف تسک → تغییر وضعیت به انجام‌شده / برگشت
- رفرش دستی با آیکن 🔄
- به‌روزرسانی خودکار قابل تنظیم (۱۵ / ۳۰ / ۶۰ / ۱۲۰ دقیقه یا فقط دستی)
- کش محلی + نمایش «آفلاین» در صورت قطع شبکه
- ساخت APK با GitHub Actions

## محدودیت‌های RemoteViews (الزامی)

| مجاز | ممنوع |
|------|--------|
| LinearLayout | RelativeLayout / ConstraintLayout / FrameLayout |
| TextView | EditText داخل ویجت |
| ImageView | RecyclerView / ListView / ScrollView |
| حداکثر ۶ ردیف ثابت تسک | addView پویا |

فیلد متنی داخل خود ویجت پشتیبانی نمی‌شود؛ دکمه «افزودن تسک جدید» Activity مخصوص را باز می‌کند.

## نصب و راه‌اندازی

1. ریپو را روی GitHub بسازید و این پروژه را push کنید.
2. از تب **Actions** ورک‌فلو **Build APK** را اجرا کنید (یا push به main).
3. Artifact به نام `TaskPlussWidget-debug` را دانلود و نصب کنید.
4. اپ را باز کنید → آدرس Web App (`.../exec`) + نام کاربری + رمز را وارد کنید → **ورود و ذخیره**.
5. ویجت را به صفحه اصلی اضافه کنید (Long-press → Widgets → تسک پلاس).

### نکات سامسونگ

- تنظیمات → اپ‌ها → تسک پلاس → باتری → **بدون محدودیت**
- در صورت نیاز Private DNS: `dns.shecan.ir`

## ساختار پروژه

```
app/src/main/
├── java/com/taskpluss/widget/
│   ├── TaskPlussWidgetProvider.kt
│   ├── WidgetRenderer.kt          # منطق مشترک رندر و fetch
│   ├── ApiClient.kt               # RPC به Google Apps Script
│   ├── ConfigActivity.kt
│   ├── AddTaskActivity.kt
│   ├── SilentRefreshActivity.kt
│   ├── GroupSelectActivity.kt
│   ├── ToggleTaskActivity.kt
│   ├── AlarmHelper.kt
│   ├── WidgetUpdateReceiver.kt
│   ├── BootReceiver.kt
│   ├── Prefs.kt
│   └── model/Models.kt
├── res/layout/widget_layout.xml   # فقط LinearLayout/TextView/ImageView
└── res/xml/widget_info.xml
```

## اتصال به بک‌اند

از همان RPC `doPost` با بدنه:

```json
{ "fn": "loginUser" | "getGroupsForUser" | "getTasksPage" | "upsertTask", "args": [...] }
```

استفاده می‌شود (مطابق کد Apps Script ارائه‌شده).

## مجوزها

- INTERNET
- RECEIVE_BOOT_COMPLETED
- SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM
