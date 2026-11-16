# الضغاط – Al-Daghat

تطبيق أندرويد عربي لضغط الفيديوهات وتقليل أحجامها مع المحافظة على جودة الصورة والصوت، ويعمل بالكامل **دون اتصال بالإنترنت**. لا توجد أي خدمة سحابية ولا صلاحية `INTERNET` في التطبيق.

- **معرّف الحزمة:** `com.abbas.aldaghat`
- **الحد الأدنى:** Android 6.0 (API 23) — **الهدف:** Android 16 (API 36)
- **محرك الضغط:** Android Media3 Transformer 1.8.0 (ترميز فعلي H.264/H.265 + AAC داخل حاوية MP4)
- **الواجهة:** Kotlin + Jetpack Compose + Material 3، عربية بالكامل مع RTL إجباري
- **المعمارية:** MVVM + Coroutines/StateFlow، مع Foreground Service للعمل في الخلفية

---

## 1. الميزات المنفّذة

| الميزة | الحالة |
| --- | --- |
| اختيار فيديو (Photo Picker + SAF) | ✅ يعمل عبر `PickVisualMedia` و`OpenDocument`، ويُدعم `content://` فقط دون افتراض مسار ملف |
| معاينة الفيديو مع الصورة المصغرة | ✅ مشغّل ExoPlayer داخل الشاشة + صورة مصغّرة مولّدة بـ `MediaMetadataRetriever` |
| خيارات 1080p / 720p / 480p / 360p | ✅ معدلات بت مركزية موثّقة في `docs/PRESETS.md` |
| وضع الحجم المخصص | ✅ شريط معدل بت الفيديو (200–8000 kbps) + معدل بت الصوت (64–256 kbps) + اختيار الدقة |
| تقدير الحجم قبل الضغط | ✅ `duration_s × (video_kbps + audio_kbps) / 8000` مع توضيح أنه تقديري |
| ضغط فعلي وليس تغيير امتداد | ✅ إعادة ترميز حقيقية عبر Media3 Transformer |
| عدم رفع الدقة فوق الأصل | ✅ مضمون في `CompressionPlanner` |
| الحفاظ على الاتجاه والأبعاد والنسبة | ✅ معالجة دوران الميتاداتا + `Presentation` بأبعاد زوجية |
| الحفاظ على الصوت عند وجوده | ✅ AAC بمعدل البت المخطط، ويُحذف فقط عند غياب المسار الأصلي |
| H.265/HEVC اختياري | ✅ يظهر فقط إذا وُجد مُرمِّز حقيقي على الجهاز (`EncoderCapabilities`) |
| Foreground Service + إشعار تقدم | ✅ نسبة حقيقية، الوقت المنقضي، الوقت المتبقي، زر إلغاء في الإشعار والتطبيق |
| إلغاء فعلي وتنظيف الملفات المؤقتة | ✅ `Transformer.cancel()` + حذف الملف المؤقت وسجل الحالة |
| منع عمليتي ضغط متزامنتين | ✅ `CompressionJobController` بمؤشر حجز واحد |
| الحفظ في `Movies/ABBAS` | ✅ MediaStore + Scoped Storage على Android 10+، ومسار عام مع `MediaScanner` على 6.0–9.0 |
| عدم استبدال الأصل + منع تكرار الأسماء | ✅ لاحقة `_compressed` ثم `(1)`, `(2)` عند التكرار |
| نافذة نجاح (فتح/مجلد/مشاركة) | ✅ مع Content URI وصلاحية قراءة مؤقتة |
| سجل العمليات | ✅ SQLite محلي: مصغّرة، اسم، حجم أصلي/ناتج، جودة، تاريخ، حالة، فتح/مشاركة/حذف |
| عرض مجلد ABBAS داخل التطبيق | ✅ شاشة مستقلة تعرض محتوى المجلد إن تعذر فتحه خارجيًا |
| العمل دون إنترنت | ✅ لا صلاحية INTERNET، ولا أي تنزيل وقت التشغيل |
| حفظ حالة المهام | ✅ صف حالة `RUNNING` في قاعدة البيانات، ويُحوَّل إلى `INTERRUPTED` عند إغلاق النظام للمعالجة |

### ما لم يُنفَّذ عن قصد

- **الإيقاف المؤقت (Pause):** واجهة Media3 Transformer لا تدعم إيقاف/استئناف تصدير قيد التشغيل بشكل صحيح، لذلك لا يوجد زر إيقاف مؤقت وهمي. الإلغاء وإعادة البدء هما المسار المدعوم.
- **FFmpegKitNext:** لم يُضمَّن. كل ما يحتاجه التطبيق (تغيير الدقة، التحكم بمعدل البت، AAC، MP4، الدوران) توفره Media3. إضافة FFmpeg كانت ستضيف عشرات الميغابايت ومكتبات أصلية بتراخيص LGPL/GPL ومخاطر توافق 16KB دون حاجة وظيفية. التفاصيل في `docs/COMPRESSION_ENGINE.md`.

---

## 2. البناء والتشغيل

### المتطلبات

- JDK 17 (تم البناء والاختبار باستخدام Temurin 17)
- Android SDK Platform 36 + Build-Tools 35.0.0 (يُثبَّتها Gradle تلقائيًا إذا قبلت التراخيص)
- Android Studio Ladybug أو أحدث (اختياري)
- Gradle 8.14.3 (عبر الـ wrapper المرفق)

```bash
# 1) اضبط مسار SDK (أو استخدم ANDROID_HOME)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2) بناء APK للتطوير
./gradlew :app:assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk

# 3) حزمة توزيع
./gradlew :app:bundleRelease
# الناتج: app/build/outputs/bundle/release/app-release.aab
```

### توقيع إصدار الإنتاج

لا يحتوي المستودع على أي مفتاح توقيع. لتوقيع نسخة الإصدار أنشئ مفتاحًا خاصًا بك ثم مرّر خصائصه:

```bash
keytool -genkeypair -v -keystore aldaghat-release.jks -alias aldaghat \
        -keyalg RSA -keysize 4096 -validity 10000

./gradlew :app:assembleRelease \
  -PALDAGHAT_STORE_FILE=/absolute/path/aldaghat-release.jks \
  -PALDAGHAT_STORE_PASSWORD=****** \
  -PALDAGHAT_KEY_ALIAS=aldaghat \
  -PALDAGHAT_KEY_PASSWORD=******
```

بدون هذه الخصائص يبقى `assembleRelease` غير موقّع، وهو السلوك المقصود.

### التثبيت

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. الاختبارات

```bash
# اختبارات الوحدة (JVM) — 42 اختبارًا
./gradlew :app:testDebugUnitTest

# اختبارات الأجهزة (ضغط حقيقي) — تحتاج جهازًا/محاكيًا متصلًا
./gradlew :app:connectedDebugAndroidTest
```

اختبارات الأجهزة تولّد فيديو إدخال حقيقيًا على الجهاز نفسه بـ `MediaCodec` ثم تضغطه وتفحص ملف MP4 الناتج (دقة، وجود مسار صوت، مدة، تقليل الحجم، إلغاء، ملف تالف، النشر في ABBAS). التفاصيل والنتائج في `docs/TESTING.md`.

---

## 4. بنية المشروع

```
app/src/main/java/com/abbas/aldaghat/
├── AlDaghatApplication.kt        # نقطة البداية + تهيئة الحاوية
├── MainActivity.kt               # Activity واحدة + RTL إجباري
├── di/AppContainer.kt            # حقن تبعيات يدوي (لا Hilt/KSP)
├── domain/                       # منطق نقي قابل للاختبار (لا يعتمد على Android)
│   ├── QualityPreset.kt          # خيارات الجودة ومعدلات البت المركزية
│   ├── BitrateSelector.kt        # خوارزمية اختيار معدل البت
│   ├── CompressionPlanner.kt     # الدقة النهائية + التوافق مع الجهاز + عدم رفع الدقة
│   ├── SizeEstimator.kt          # معادلة الحجم التقديري
│   ├── CompressionJob.kt         # JobSpec + JobState + CompressionResult
│   └── OutputNaming.kt           # _compressed ومنع التكرار
├── data/                         # Android I/O
│   ├── VideoInspector.kt         # MediaExtractor/MediaMetadataRetriever
│   ├── EncoderCapabilities.kt    # فحص مُرمِّزات الجهاز (H.264/H.265)
│   ├── MediaStorePublisher.kt    # الحفظ في Movies/ABBAS
│   ├── HistoryDatabase.kt        # SQLite للسجل
│   └── ThumbnailStore.kt
├── media/Media3CompressionEngine.kt  # محرك الضغط الفعلي
├── service/                      # Foreground Service + الإشعارات + حالة المهمة
└── ui/                           # Compose: home / history / folder / components / theme
```

---

## 5. الصلاحيات

| الصلاحية | السبب |
| --- | --- |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` + `FOREGROUND_SERVICE_MEDIA_PROCESSING` | استمرار الضغط في الخلفية (نوع `mediaProcessing` على Android 15+، و`dataSync` قبله) |
| `POST_NOTIFICATIONS` | إشعار التقدم والنتيجة على Android 13+ |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | حفظ الملف في `Movies/ABBAS` على Android 6–9 فقط |

لا توجد صلاحية `INTERNET`، ولا `MANAGE_EXTERNAL_STORAGE`، ولا `READ_MEDIA_VIDEO`: القراءة تتم عبر منتقي النظام الذي يمنح وصولًا للعنصر المختار فقط.

---

## 6. التراخيص

- كود التطبيق: MIT — انظر [LICENSE](LICENSE)
- المكتبات وإشعاراتها: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- اعتبارات الترميز وبراءات الاختراع موثّقة في `THIRD_PARTY_NOTICES.md` و`docs/COMPRESSION_ENGINE.md`

## 7. توثيق إضافي

- [docs/COMPRESSION_ENGINE.md](docs/COMPRESSION_ENGINE.md) — كيف يعمل محرك الضغط خطوة بخطوة
- [docs/PRESETS.md](docs/PRESETS.md) — جدول الإعدادات ومعدلات البت والمعادلات
- [docs/TESTING.md](docs/TESTING.md) — خطة الاختبارات ونتائج التشغيل الفعلية
