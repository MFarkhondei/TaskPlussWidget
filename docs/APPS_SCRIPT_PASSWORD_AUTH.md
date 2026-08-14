# پشتیبانی ورود با رمز عبور در API ویجت

اپ اندروید از نسخه **1.1.0** به‌جای `token` پارامتر `password` می‌فرستد:

```
GET {WEB_APP_URL}?action=widget&user={USERNAME}&password={PASSWORD}
```

باید در فایل `Code.gs` پروژه Apps Script این تغییرات را اعمال و **Deploy → New version** کنید.

## ۱. تغییر `doGet`

```javascript
function doGet(e) {
  try {
    const params = (e && e.parameter) ? e.parameter : {};
    if (params.action === 'widget') {
      // پشتیبانی از password یا token (سازگاری با نسخه قدیم)
      return serveWidgetApi_(params.user, params.token, params.password);
    }
  } catch (err) {
    return ContentService
      .createTextOutput(JSON.stringify({ success: false, message: String(err) }))
      .setMimeType(ContentService.MimeType.JSON);
  }

  return HtmlService.createHtmlOutputFromFile('index')
    .setTitle('ترید پلاس | TradePluss')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1')
    .setFaviconUrl('https://cdn.jsdelivr.net/npm/emoji-datasource-apple/img/apple/64/1f4b0.png');
}
```

## ۲. تغییر ابتدای `serveWidgetApi_`

امضای تابع و بخش احراز هویت را این‌گونه جایگزین کنید (بقیهٔ بدنهٔ تابع همان قبلی بماند):

```javascript
function serveWidgetApi_(username, token, password) {
  const out = function(obj) {
    return ContentService
      .createTextOutput(JSON.stringify(obj))
      .setMimeType(ContentService.MimeType.JSON);
  };

  if (!username || (!token && !password)) {
    return out({ success: false, message: 'user و password (یا token) الزامی است' });
  }

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const usersSheet = ss.getSheetByName('کاربران');
  if (!usersSheet) return out({ success: false, message: 'سیستم تنظیم نشده' });

  const users = usersSheet.getDataRange().getValues();
  let found = false;
  let status = '';

  // هش رمز عبور مثل loginUser
  const hashedPassword = password
    ? Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(password))
        .map(b => ('0' + (b & 0xFF).toString(16)).slice(-2))
        .join('')
    : null;

  for (let i = 1; i < users.length; i++) {
    if (String(users[i][0]).trim() === String(username).trim()) {
      found = true;
      status = users[i][5];

      if (hashedPassword) {
        // ورود با رمز عبور (ستون B = هش پسورد)
        const storedHash = users[i][1] ? String(users[i][1]).trim() : '';
        if (!storedHash || storedHash !== hashedPassword) {
          return out({ success: false, message: 'نام کاربری یا رمز عبور اشتباه است' });
        }
      } else {
        // سازگاری با توکن قدیم (ستون H)
        const storedToken = users[i][7] ? String(users[i][7]).trim() : '';
        if (!storedToken || storedToken !== String(token).trim()) {
          return out({ success: false, message: 'توکن نامعتبر' });
        }
      }
      break;
    }
  }
  if (!found) return out({ success: false, message: 'کاربر یافت نشد' });
  if (status !== 'فعال') return out({ success: false, message: 'کاربر غیرفعال است' });

  // ... ادامهٔ کد قبلی (getLiveAssets و بقیه) بدون تغییر ...
}
```

## ۳. Deploy

1. در ادیتور Apps Script ذخیره کنید
2. **Deploy → Manage deployments → Edit → New version → Deploy**
3. همان URL قبلی را در اپ نگه دارید (معمولاً با New version همان `/exec` کار می‌کند)

## تست سریع در مرورگر

```
https://script.google.com/macros/s/XXXX/exec?action=widget&user=YOUR_USER&password=YOUR_PASS
```

باید JSON با `"success":true` برگردد.
