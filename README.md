<div align="center">

# 🎙️ Voicescript (صدانگار)

**تبدیل فایل‌های صوتی به متن و تهیه‌ی خلاصه‌ی هوشمند، مستقیماً روی دستگاه اندرویدی شما**

[![Release](https://img.shields.io/github/v/release/Bahram-PAB/Voicescript?style=flat-square)](https://github.com/Bahram-PAB/Voicescript/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android-green?style=flat-square&logo=android)](https://github.com/Bahram-PAB/Voicescript/releases/latest)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-blueviolet?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![AI](https://img.shields.io/badge/AI-Gemini%20API%20%7C%20Files%20API-4285F4?style=flat-square&logo=google)](https://ai.google.dev)

*اپ همین حالا را دانلود کنید:* **[📦 آخرین نسخه (APK)](https://github.com/Bahram-PAB/Voicescript/releases/latest)**

</div>

---

## ✨ امکانات

| | |
|---|---|
| 🎤 | **ضبط صدا** مستقیم از میکروفن گوشی |
| 📁 | ورود فایل از حافظه‌ی گوشی (MP3، M4A، WAV، OGG و...) |
| 📝 | **رونویسی متن** به همان زبان گفتار اورجینال |
| 📋 | **خلاصه‌سازی حرفه‌ای** به زبان فارسی (موضوعات اصلی، نتیجه‌ها، موارد اقدام) |
| ⏱️ | **پشتیبانی از فایل‌های طولانی** (درباره‌ی ۱ ساعت صدا) بدون خطا |
| 🔄 | ادامه‌ی پردازش در صورت اختلال اینترنت (Retry خودکار) |

## 🤖 چطور کار می‌کند؟

1. صدا به‌صورت استریمی روی **Google Files API** آپلود می‌شود — بدون محدودیت حجم
2. مدل هوش مصنوعی **Gemini** کل فایل را یکجا پردازش می‌کند (برای فایل‌های طولانی نیازی به تقسیم نیست)
3. متن رونویسی‌شده + خلاصه‌ی فارسی نمایش داده می‌شود و در تاریخچه ذخیره می‌ماند

فایل‌های کوتاه و سبک مستقیماً داخل درخواست ارسال می‌شوند تا پردازش سریع‌تر باشد.

## 📥 نصب

1. از بخش [**Releases**](https://github.com/Bahram-PAB/Voicescript/releases/latest) آخرین فایل `APK` را دانلود کنید
2. فایل را باز کنید (اگر نیاز شد اجازه‌ی نصب از منابع ناشناس را بدهید)
3. مطمئن شوید اینترنت گوشی فعال است
4. اولین بار، کلید API خود را از **دکمه‌ی تنظیمات (⚙️) بالای صفحه** وارد کنید
   - کلید رایگان را از [Google AI Studio](https://aistudio.google.com/apikey) بگیرید

## 🛠️ ساخت سورس (برای توسعه‌دهندگان)

**پیش‌نیازها:** [Android Studio](https://developer.android.com/studio) (نسخه‌ی جدید) + JDK 17

1. ریپو را کلون کنید:
   ```bash
   git clone https://github.com/Bahram-PAB/Voicescript.git
   ```
2. پروژه را در Android Studio باز کنید و اجازه‌ی Sync/Fix بدهید
3. در ریشه‌ی پروژه فایل `.env` بسازید و کلید خود را قرار دهید (الگو: `.env.example`):
   ```
   GEMINI_API_KEY=YOUR_KEY_HERE
   ```
4. اگر در `app/build.gradle.kts` این خط وجود دارد، آن را حذف کنید (در بیلد محلی فایل کلید دیباگ موجود نیست):
   `signingConfig = signingConfigs.getByName("debugConfig")`
5. روی امولاتور یا گوشی اپ را اجرا کنید

<details>
<summary>🖥️ بیلد از خط فرمان</summary>

اگر `ANDROID_HOME` تنظیم باشد:
```bash
./gradlew assembleDebug
```
خروجی: `app/build/outputs/apk/debug/`

همچنین هر push روی شاخه‌ی اصلی، به‌صورت خودکار با **GitHub Actions** بیلد و در Releases منتشر می‌شود؛ پس جدیدترین APK همیشه در [Releases](https://github.com/Bahram-PAB/Voicescript/releases/latest) در دسترس است.
</details>

## 🧩 تکنولوژی‌ها

- **زبان:** Kotlin + Jetpack Compose (Material 3)
- **هوش مصنوعی:** Google Gemini API + Files API (آپلود استریمی برای فایل‌های بزرگ)
- **دیتابیس:** Room
- **شبکه:** Retrofit / Moshi / OkHttp
- **ضبط صدا:** MediaRecorder (AAC در کانتینر MP4)

## 📁 ساختار پروژه

```
app/src/main/java/com/example/
├── ui/                  # رابط کاربری Compose و ViewModel
├── data/network/        # سرویس Gemini و آپلودر Files API
├── data/database/       # موجودیت‌ها و DAO (تاریخچه نوت‌ها)
├── data/repository/     # لایه دسترسی دیتا
├── util/                # ضبط، پخش و تفکیک صدا
└── MainActivity.kt
```

---

<div align="center">

*ساخته‌شده با Android Studio و Google AI Studio — با ❤️ توسط [Bahram-PAB](https://github.com/Bahram-PAB)*

</div>
