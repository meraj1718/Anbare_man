# انبار من — Android

این پروژه برای ساخت APK با Codemagic آماده شده است.

## ساخت APK

1. پروژه را در یک Git repository قرار دهید.
2. در Codemagic یک Android Native application اضافه کنید.
3. repository و branch را انتخاب کنید.
4. workflow با نام `android-apk` را اجرا کنید.
5. پس از موفقیت Build، فایل داخل Artifacts با نامی شبیه `app-debug.apk` قابل دریافت و نصب روی گوشی اندرویدی است.

این workflow از `assembleDebug` استفاده می‌کند تا بدون نیاز به keystore شخصی، APK قابل نصب برای تست ساخته شود.
