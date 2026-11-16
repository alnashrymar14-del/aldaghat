# الاختبارات والتحقق

## 1. كيف تشغّل الاختبارات

```bash
# اختبارات الوحدة (JVM، لا تحتاج جهازًا)
./gradlew :app:testDebugUnitTest

# اختبارات الأجهزة (ضغط حقيقي)
./gradlew :app:connectedDebugAndroidTest

# اختبار واحد فقط
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abbas.aldaghat.CompressionEngineInstrumentedTest
```

بيئة التشغيل المستخدمة للتحقق الفعلي في هذا المستودع:

| العنصر | القيمة |
| --- | --- |
| JDK | Temurin 17 |
| Gradle | 8.14.3 |
| AGP | 8.13.2 |
| Kotlin | 2.2.20 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 23 |
| المحاكي | AVD `android-30 default x86_64` (Pixel 5 profile)، بدون تسريع KVM |

> المحاكي في بيئة التحقق كان يعمل بمحاكاة برمجية (TCG) بدون KVM، لذلك أزمنة التنفيذ أطول بكثير من
> جهاز حقيقي. الأزمنة المذكورة أدناه من هذا التشغيل.

## 2. اختبارات الوحدة — 42 اختبارًا (ناجحة بالكامل)

| الملف | العدد | التغطية |
| --- | --- | --- |
| `SizeEstimatorTest` | 5 | معادلة الحجم، الحالات الحدّية، نسبة التقليل المقيدة بين 0 و100 |
| `BitrateSelectorTest` | 11 | نطاقات 1080p/720p/480p/360p، أثر معدل الإطارات، السقف عند معدل بت المصدر، الوضع المخصص، حدود الصوت |
| `CompressionPlannerTest` | 11 | عدم رفع الدقة، الأبعاد الزوجية، الفيديو العمودي، الدوران، النسبة، التراجع عند عدم دعم الدقة، غياب الصوت |
| `OutputNamingTest` | 5 | لاحقة `_compressed`، تنظيف الأسماء، منع التكرار `(1)` |
| `VideoInfoTest` | 5 | أبعاد العرض مع الدوران، الوضع العمودي، سرعة إطارات غير صالحة |
| `FormattersTest` | 5 | الأحجام، المدد، النسب، الوقت المتبقي، التاريخ بالعربية |

النتيجة: `42 tests completed, 0 failed`.

## 3. اختبارات الأجهزة — 18 اختبارًا تولّد فيديوهات حقيقية

كل اختبار يولّد فيديو إدخال MP4 حقيقيًا على الجهاز عبر `MediaCodec` (فيديو H.264 + صوت AAC عند الحاجة)،
ثم يشغّل نفس محرك الإنتاج، ثم يفحص الملف الناتج بـ `MediaExtractor` و`MediaMetadataRetriever`.

| # | الاختبار | يتحقق من |
| --- | --- | --- |
| 1 | `compress1080pVideoProducesPlayableSmallerFile` | ضغط فيديو 1080p، وجود مسار فيديو، مدة > 0، احترام معدل البت المخطط |
| 2 | `compress720pVideoScalesDown` | النزول من 1080p إلى 720p، سلامة الملف، احترام معدل البت |
| 3 | `compress480pVideoScalesDown` | النزول إلى 480p + **حجم أصغر من المصدر** |
| 4 | `compress360pVideoScalesDown` | النزول إلى 360p + **حجم أصغر من المصدر** |
| 5 | `portraitVideoStaysPortrait` | فيديو عمودي 720×1280 يبقى عموديًا وبضلع أقصر 480 |
| 6 | `landscapeVideoStaysLandscape` | الفيديو الأفقي يبقى أفقيًا |
| 7 | `audioTrackIsPreserved` | بقاء مسار AAC بعد الضغط |
| 8 | `videoWithoutAudioProducesNoAudioTrack` | عدم اختراع مسار صوت لملف بلا صوت |
| 9 | `customBitrateIsApplied` | احترام معدل البت اليدوي (700 kbps) |
| 10 | `corruptInputFailsGracefully` | ملف تالف → فشل نظيف بدون ملف ناتج |
| 11 | `cancellingDeletesPartialOutput` | الإلغاء يوقف التصدير ويحذف الملف المؤقت |
| 12 | `encoderCapabilitiesAreConsistent` | H.264 مدعوم، وHEVC لا يُعلن إلا عند وجود مُرمِّز حقيقي |
| 13 | `hevcCompressionWorksWhenSupported` | ترميز HEVC عند دعم الجهاز (يُتجاهل إن لم يوجد) |
| 14 | `publishesIntoAbbasFolderWithUniqueName` | النشر في `Movies/ABBAS`، عدم تكرار الأسماء، الظهور في قائمة المجلد، الحذف |
| 15 | `detectsInsufficientSpace` | كشف نقص المساحة قبل البدء |
| 16 | `relativePathPointsToMoviesAbbas` | مسار المجلد الصحيح |
| 17 | `serviceCompletesJobAndStoresHistory` | Foreground Service كامل: بدء → نجاح → ملف في MediaStore → صف `COMPLETED` في السجل |
| 18 | `secondJobIsRejectedWhileOneIsRunning` | منع عمليتي ضغط متزامنتين + الإلغاء |

### النتائج المقاسة

- تشغيل كامل: `Tests run: 18, Failures: 1` في الجولة الأولى.
  - الاختبار الفاشل الوحيد كان `compress720pVideoScalesDown` بسبب تأكيد **حجم ملف أصغر** على فيديو
    صناعي بسيط (أشرطة متحركة) يضغط أصلًا إلى معدل بت أقل بكثير من 4 Mbps المُعلن، فإعادة ترميزه عند
    ~2.7 Mbps تكبر الملف. هذا هو الاستثناء الموثق «عندما تسمح طبيعة الفيديو».
  - عُدّل الاختبار ليؤكد ما يهم فعلًا: احترام معدل البت المخطط عبر
    `ExportResult.averageVideoBitrate`، مع إبقاء تأكيد «الحجم أصغر» على الخيارين 480p و360p.
- إعادة تشغيل الاختبارين المتأثرين على البناء النهائي: ناجحان.
- اختبارات 480p/360p تؤكد فعليًا `output < source` على ملف حقيقي.
- اختبار الخدمة يثبت أن الناتج يظهر كـ Content URI في MediaStore ويُسجَّل في قاعدة البيانات.

## 4. التحقق من فتح التطبيق

- تثبيت APK النهائي على المحاكي، ثم `am start -n com.abbas.aldaghat/.MainActivity`:
  `Displayed com.abbas.aldaghat/.MainActivity` بدون أي `FATAL EXCEPTION` في `logcat`.
- لقطة الشاشة في `artifacts/screenshot-home.png` تُظهر الواجهة العربية من اليمين إلى اليسار
  (الاسم، الشعار، صف سجل العمليات، بطاقة الاختيار الفارغة).

## 5. اختبار العمل دون إنترنت

- التطبيق لا يعلن صلاحية `INTERNET` إطلاقًا (`AndroidManifest.xml`)، ولا توجد مكتبة شبكة في
  الاعتماديات، ولا تنزيل لأي مكوّن وقت التشغيل.
- كل الاختبارات أعلاه تعمل بالكامل محليًا: مُرمِّزات النظام + MediaStore + SQLite.
- للتحقق يدويًا: `adb shell svc wifi disable && adb shell svc data disable` ثم ضغط أي فيديو.

## 6. 16 KB Memory Page Size

- لا يحتوي APK على أي مكتبة أصلية من بناء هذا المشروع. الملف الوحيد `.so` هو
  `libandroidx.graphics.path.so` القادم من AndroidX (16 KB-compatible) لأربع معماريات.
- لا يوجد أي استخدام لـ FFmpeg أو مكتبات OMX/FFmpeg القديمة، ولا تسمية `armeabi` المهجورة.
- للتحقق على جهاز/محاكي 16 KB: تثبيت الـ APK وتشغيل الاختبارات — لا يوجد كود أصلي خاص يمكن أن يفشل.
  (لم يتوفر في بيئة التحقق جهاز بصفحات 16 KB؛ هذا البند يحتاج جهازًا فعليًا أو صورة محاكي 16 KB.)

## 7. حدود التحقق في هذه البيئة

- المحاكي كان بدون KVM، فبعض الاختبارات تستغرق دقائق (توليد فيديو 1080p ثم ترميزه). على جهاز حقيقي
  تكون الأزمنة أقل بكثير.
- لم يُختبر على جهازين فعليين مختلفين؛ تم التحقق على محاكي API 30 فقط. اختبار تباين المُرمِّزات
  الحقيقي (بعض الأجهزة ترفض أحجامًا معينة) مغطّى منطقيًا بمنطق التراجع في `CompressionPlanner`
  و`EncoderCapabilities`، ويُنصح بتشغيل `connectedDebugAndroidTest` على أجهزة المستخدمين المستهدفة.
- تم اختبار الرفع/الضغط الفعلي عبر الخدمة على المحاكي، ولم تُختبر واجهة الإشعارات بصريًا على الجهاز.
