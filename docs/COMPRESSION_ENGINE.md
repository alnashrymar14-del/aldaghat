# محرك الضغط — كيف يعمل فعليًا

## 1. نظرة عامة على المسار

```
اختيار الفيديو (Photo Picker / SAF)
        │  content:// URI
        ▼
VideoInspector  ── MediaExtractor + MediaMetadataRetriever
        │  VideoInfo: أبعاد، دوران، مدة، fps، معدل بت، وجود صوت، حجم
        ▼
CompressionPlanner ── QualityPreset + EncoderCapabilities
        │  CompressionPlan: دقة نهائية (زوجية)، معدل بت فيديو/صوت، كوديك
        ▼
CompressionJobController.start(spec)   ← يمنع أي عملية ثانية
        │
        ▼
CompressionService (Foreground)  ── إشعار تقدم + زر إلغاء
        │
        ▼
Media3CompressionEngine ── Transformer
        │  ملف مؤقت في cacheDir/compression/temp_*.mp4
        ▼
MediaStorePublisher ── Movies/ABBAS (IS_PENDING ثم نشر)
        │  Content URI نهائي
        ▼
HistoryDatabase + ThumbnailStore + إشعار نتيجة + نافذة نجاح
```

## 2. إعدادات Media3 Transformer

```kotlin
Transformer.Builder(context)
    .setVideoMimeType(spec.videoMimeType)               // video/avc أو video/hevc
    .setAudioMimeType(MimeTypes.AUDIO_AAC)              // AAC دائماً
    .setEncoderFactory(
        DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(spec.videoBitrateKbps * 1000)
                    .setiFrameIntervalSeconds(1f)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(spec.audioBitrateKbps * 1000)
                    .build()
            )
            .setEnableFallback(true)                    // يسمح بالتراجع لإعدادات يدعمها الجهاز
            .build()
    )
    .setPortraitEncodingEnabled(true)                   // ترميز عمودي أصلي على API 24+
    .setLooper(Looper.getMainLooper())
    .build()
```

عناصر المعالجة (Effects):

- عند الحاجة إلى تصغير الدقة: `Presentation.createForWidthAndHeight(outW, outH, LAYOUT_SCALE_TO_FIT)`
  بأبعاد **زوجية** محسوبة مسبقًا، ما يحافظ على نسبة العرض إلى الارتفاع ويمنع أي تشويه.
- عند عدم الحاجة إلى تصغير (مصدر أصغر من الخيار): لا تُضاف أي تأثيرات، ويُعاد الترميز لتقليل معدل البت فقط.
- الصوت: `EditedMediaItem.setRemoveAudio(!spec.hasAudio)`؛ لا يُحذف الصوت إلا إذا كان المصدر بلا مسار صوتي.
- الحاوية: MP4 دائماً (`.mp4`) لأن H.264/H.265 + AAC متوافقة مع كل المشغلات.

## 3. حساب الدقة (بلا رفع للدقة)

- يُستخدم **الضلع الأقصر** كمرجع للخيارات، لذلك يتصرف 720p بالطريقة نفسها للفيديو الأفقي والعمودي.
  - 1920×1080 + 720p → 1280×720
  - 1080×1920 + 720p → 720×1280
  - 1080×1920 بدوران 90° في الميتاداتا → يُعامل كـ 1080×1920 (أي عمودي)
- إذا كان المصدر أصغر من الخيار المطلوب، تُترك الدقة الأصلية كما هي (`scaled = false`).
- كل الأبعاد النهائية تُقرَّب للأسفل إلى عدد زوجي (متطلب مُرمِّزات الأجهزة).
- إذا لم يدعم مُرمِّز الجهاز الدقة المطلوبة، تُخفَّض تدريجيًا (×0.85) حتى يجد المحرك دقة مدعومة،
  وإن لم يجد أي دقة أصغر يُرفض البدء برسالة واضحة بدل إنتاج ملف معطوب.

## 4. اختيار معدل البت

```
pixels = outW × outH
fps    = clamp(sourceFps, 24, 60)
raw    = pixels × fps × bpp(preset) / 1000        [kbps]
kbps   = clamp(raw, preset.min, preset.max)
kbps   = min(kbps, sourceVideoKbps × 0.92)        // لا نطلب أكثر من المصدر
kbps   = clamp(kbps, 150, 50000)
```

`bpp` لكل خيار: 1080p = 0.09، 720p = 0.10، 480p = 0.11، 360p = 0.13، مخصص = قيمة المستخدم.
التفاصيل الكاملة والجدول في [PRESETS.md](PRESETS.md).

الصوت: AAC بمعدل البت المختار (128 kbps افتراضيًا، 64–256 في الوضع المخصص)، ويُصفَّر عند غياب الصوت.

## 5. التقدم والوقت المتبقي

- التقدم من الواجهة الرسمية: `Transformer.getProgress(ProgressHolder)` كل 400ms، بقيمة 0–100 حقيقية.
- الوقت المنقضي من `SystemClock.elapsedRealtime()` منذ بدء المهمة.
- الوقت المتبقي = `elapsed × (100 − progress) / progress` (لا يُعرض قبل مرور 1.5 ثانية أو قبل 1%).
- الإشعار يُحدَّث عند تغيّر 2% على الأقل أو كل ثانية، لتجنّب إغراق `NotificationManager`.

## 6. الإلغاء والتنظيف

1. زر الإلغاء (واجهة التطبيق أو الإشعار) → `CompressionService.requestCancel()`.
2. `Transformer.cancel()` + إلغاء الـ Coroutine الخاص بالمهمة.
3. `invokeOnCancellation` يحذف الملف المؤقت فورًا.
4. سجل العملية يصبح `CANCELLED`، ويُزال إشعار التقدم، وتظهر رسالة إلغاء.

## 7. دورة حياة الملفات

| الملف | الموقع | يُحذف متى |
| --- | --- | --- |
| مؤقت المعالجة | `cacheDir/compression/temp_*.mp4` | بعد النشر الناجح، أو عند الفشل/الإلغاء، أو عند بدء التطبيق بعد توقف النظام |
| الصورة المصغّرة | `filesDir/thumbs/*.jpg` | عند حذف السجل/الملف |
| الناتج | `Movies/ABBAS` عبر MediaStore | لا يُحذف إلا بطلب صريح من المستخدم |

على Android 10+ يُنشأ الصف بـ `IS_PENDING=1` ثم يُمسح بعد اكتمال الكتابة. إن فشلت الكتابة يُحذف الصف
فورًا، لذلك لا يظهر ملف نصف مكتوب في المعرض أو في مجلد ABBAS.

## 8. الخلفية والاستمرارية

- الخدمة من نوع `mediaProcessing` على Android 15+ (النوع المخصص لمعالجة الوسائط) و`dataSync` قبله.
- `START_NOT_STICKY`: لا إعادة تشغيل صامتة بمهام ناقصة.
- عند إغلاق واجهة التطبيق تستمر الخدمة (Foreground) وتستمر النسبة في الإشعار.
- إذا قتل النظام العملية: عند التشغيل التالي تُعلَّم كل الصفوف `RUNNING` كـ `INTERRUPTED` وتُحذف الملفات
  المؤقتة، ولا تُترك حالة "قيد التنفيذ" كاذبة.

## 9. لماذا لا يوجد إيقاف مؤقت؟

`Transformer` لا يوفّر واجهة `pause()/resume()` صحيحة خلال تصدير قيد التشغيل (بدء التصدير يعيد الكتابة من
البداية). لذلك أُسقط الزر بدل تقديم زر لا يعمل. الإلغاء يعمل فعليًا ويعيد كل شيء لحالته النظيفة.

## 10. لماذا لا يوجد FFmpegKitNext؟

كل ما يحتاجه التطبيق يوفره Media3: تحجيم، تحكم بمعدل البت، AAC، MP4، احترام الدوران، ترميز عبر العتاد.
إضافة FFmpegKitNext كانت ستضيف:
- عشرات الميغابايت لكل معمارية،
- مكتبات أصلية تحتاج بناءً محليًا واختبار 16KB،
- التزامات LGPL (أو GPL لو فُعّل x264/x265)، وعبء براءات إضافي.

ولأن التطبيق يشترط العمل دون إنترنت وأن يحتوي كل شيء وقت البناء، فإن أخف خيار وأكثره أمانًا هو
الاعتماد على مُرمِّزات النظام نفسها.

## 11. ترميز H.265 / HEVC

- يُختبر توفره عبر `MediaCodecList` و`VideoCapabilities.isSizeSupported` قبل عرض الخيار.
- إذا لم يوجد مُرمِّز HEVC، يظهر الخيار معطّلًا مع توضيح "غير مدعوم على هذا الجهاز".
- عند اختياره، يمرّر `video/hevc` إلى Transformer كـ MIME للترميز، وتبقى الحاوية MP4.

## 12. التوافق

- API 23–36 مدعومة. تصدير الفيديو العمودي الأصلي متاح من API 24، وقبل ذلك يحافظ Media3 على
  اتجاه الفيديو عبر ميتاداتا الدوران.
- الصور المصغّرة تستخدم `getScaledFrameAtTime` على API 27+ وتراجع إلى `getFrameAtTime` + تحجيم يدوي.
- لا توجد مكتبات `.so` خاصة بالتطبيق، لذا لا يوجد ما قد يفشل على أجهزة بصفحات ذاكرة 16KB.
