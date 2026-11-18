# وضعیت ساخت (یادداشت)

- کامپایل Kotlin و KSP در همین محیط با موفقیت کامل عبور کرد (همه خطاها رفع شدند؛ `kspDebugKotlin` و `compileDebugKotlin` سبز شدند).
- مرحله نهایی **dex/Packaging** در این سندباکس به‌دلیل محدودیت حافظه (۴ گیگابایت) و OOM شدن دیسپچر Gradle کامل نشده است؛ این محدودیتِ محیط است، نه خطای کد.
- برای گرفتن APK کافی است یکی از این‌ها را اجرا کنید:
  1. Android Studio → باز کردن پوشه `TaghzieHoshmand` → Run ▶
  2. `./gradlew :app:assembleDebug` روی سیستم با حداقل ۸ گیگ رم
  3. Push به GitHub → ورک‌فلو آماده `.github/workflows/build.yml` خودکار APK می‌سازد (artifact: `TaghzieHoshmand-debug-apk`)
- خروجی APK در مسیر `app/build/outputs/apk/debug/app-debug.apk` قرار می‌گیرد (نسخه debug مستقیم روی گوشی نصب می‌شود).
