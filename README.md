# 📖 Quranic Creation Tool - أداة الإنشاء القرآنية

<div align="center">
  <img src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" width="100%" alt="Quranic Tool Banner" style="border-radius: 12px;"/>
  
  ![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin)
  ![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
  ![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge)
</div>

---

## 📱 نبذة عن التطبيق

أداة متقدمة وذكية لإنشاء محتوى قرآني رقمي، تجمع بين تقنيات الذكاء الاصطناعي والمعارف القرآنية الأصيلة.

---

## ✨ المميزات الرئيسية

- 📖 **مرجع قرآني شامل:** دليل كامل للقرآن الكريم
- 🔍 **بحث ذكي:** البحث السريع عن الآيات والسور
- 🎨 **تصميم جميل:** واجهة عصرية وسهلة الاستخدام
- 💾 **حفظ المفضلة:** احفظ الآيات المفضلة لديك
- 🔊 **تلاوات مسموعة:** استمع لتلاوات من قارئين محترفين
- 📝 **تفسيرات:** معاني وتفسيرات الآيات
- 🌙 **وضع الليل:** حماية العينين بوضع ليلي مريح

---

## 🛠️ المتطلبات المسبقة

- **Android Studio** (Ladybug 2024.2.1 أو أحدث)
- **JDK 17+**
- **Android 8.0** (API 26) أو أحدث

---

## 🚀 كيفية التشغيل

### 1. استنساخ المستودع
```bash
git clone https://github.com/RED1MOOD/tool.git
cd tool
```

### 2. فتح وتشغيل المشروع
```bash
# فتح في Android Studio
# أو بناء من الأوامر:
./gradlew assembleDebug
```

### 3. تثبيت وتشغيل
```bash
./gradlew installDebug
```

---

## 🏗️ البنية التقنية

| المكون | التفاصيل |
|-------|---------|
| **اللغة** | Kotlin 100% |
| **واجهة المستخدم** | Jetpack Compose |
| **قاعدة البيانات** | Room Database |
| **المشغل الصوتي** | ExoPlayer |
| **Android SDK** | API 26+ |

---

## 📂 هيكل المشروع

```
app/src/main/java/com/red1mood/quranic/
├── ui/
│   ├── screens/         # الشاشات الرئيسية
│   ├── components/      # المكونات المشتركة
│   └── theme/           # التصاميم والألوان
├── data/
│   ├── model/           # نماذج البيانات
│   ├── quran/           # بيانات القرآن الكريم
│   └── repository/      # إدارة البيانات
└── audio/               # معالجة الصوت
```

---

## 🕌 دليل الاستخدام

1. **استعرض السور:** قائمة كاملة بـ 114 سورة
2. **ابحث عن آية:** استخدم خاصية البحث المتقدمة
3. **اقرأ التفسير:** اطلع على معاني الآيات
4. **استمع للتلاوة:** اختر قارئك المفضل
5. **احفظ المفضلة:** أضف الآيات المهمة لديك

---

## 📄 الترخيص

جميع الحقوق محفوظة © 2026 RED1MOOD

---

<p align="center">
  <b>تطبيق مكرس لخدمة كتاب الله ❤️📖</b>
</p>
