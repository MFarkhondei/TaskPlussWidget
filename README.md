# ترید پلاس ویجت | TradePluss Android Widget

ویجت صفحهٔ اصلی اندروید برای پروژهٔ **ترید پلاس** که داده‌ها را از API ویجت Apps Script دریافت می‌کند.

![Widget Preview](docs/preview.png)

## ویژگی‌ها

- نمایش **ارزش کل دارایی** (تومان)
- **سود/زیان خرید روزانه** + درصد
- **مجموع خرید روزانه**
- نمودار **روند ۷ روزه** ارزش سبد
- لیست دارایی‌ها با ارزش جاری و درصد تغییر
- دکمهٔ **رفرش** دستی
- بروزرسانی خودکار هر ۳۰ دقیقه
- ظاهر تیره مطابق طراحی اسکرین‌شات

## پیش‌نیاز در سمت سرور (Apps Script)

1. Web App را Deploy کنید (`Execute as: Me` ، `Who has access: Anyone`).
2. برای هر کاربر توکن ویجت بسازید:

```javascript
generateWidgetToken('نام_کاربری')
```

توکن در ستون H شیت «کاربران» ذخیره می‌شود.

3. آدرس نهایی شبیه این است:
```
https://script.google.com/macros/s/XXXX/exec
```

API ویجت:
```
GET ?action=widget&user=USERNAME&token=TOKEN
```

## ساخت APK با GitHub Actions

1. این پوشه را به یک ریپازیتوری GitHub پوش کنید.
2. به تب **Actions** بروید → workflow **Build TradePluss Widget APK** را اجرا کنید (یا push روی `main`).
3. پس از اتمام بیلد، از بخش **Artifacts** فایل `TradePluss-Widget-v1.0.0.apk` را دانلود کنید.

### بیلد محلی (اختیاری)

```bash
# نیاز به Android SDK و JDK 17
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

## نصب و راه‌اندازی روی گوشی

1. APK را نصب کنید (ممکن است نیاز به اجازهٔ «نصب از منابع ناشناس» باشد).
2. اپ را باز کنید و این مقادیر را وارد کنید:
   - **آدرس Web App**
   - **نام کاربری**
   - **توکن ویجت**
3. ذخیره کنید.
4. روی صفحهٔ اصلی گوشی → Widgets → **ترید پلاس** را اضافه کنید.

## ساختار پروژه

```
TradePlussWidget/
├── app/
│   ├── src/main/
│   │   ├── java/com/tradepluss/widget/
│   │   │   ├── TradePlussWidgetProvider.kt
│   │   │   ├── ConfigActivity.kt
│   │   │   ├── ApiClient.kt
│   │   │   ├── ChartHelper.kt
│   │   │   └── model/WidgetData.kt
│   │   ├── res/layout/widget_layout.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/build-apk.yml
└── README.md
```

## نکات امنیتی

- توکن ویجت را عمومی نکنید.
- Web App را با دسترسی مناسب Deploy کنید.
- در صورت نیاز می‌توانید توکن را در Apps Script منقضی یا عوض کنید (`generateWidgetToken` دوباره).

## نسخه

- **1.0.0** – ویجت اولیه با API موجود پروژه ترید پلاس

---
ساخته‌شده برای پروژه ترید پلاس
