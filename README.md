# BestPhotoSelect 📸

Galerinizdeki **benzer/tekrar eden fotoğrafları bulan**, her grupta yapay zeka ile
**en iyi kareyi seçen** ve gerisini onayınızla (ya da otomatik pilotta onaysız)
temizleyen Android uygulaması.

Tüm analiz **cihaz içinde** yapılır: fotoğraflarınız telefonunuzdan asla çıkmaz,
internet bağlantısı gerekmez.

## İndir

📥 **[BestPhotoSelect v2.1.0 APK'sını indir](https://github.com/yusufkilinc44/BestPhotoSelect/raw/claude/android-photo-dedup-yfscf8/releases/BestPhotoSelect-v2.1.0.apk)**
(Android 11+, ARM; [`releases/BestPhotoSelect-v2.1.0.apk`](releases/BestPhotoSelect-v2.1.0.apk))

## Özellikler

- **Albüm seçimi** — galerideki albümlerden hangilerinin taranacağını siz seçersiniz.
- **Benzerlik gruplama** — algısal hash (dHash) + çekim zamanı yakınlığı ile art arda
  çekilmiş benzer kareler otomatik gruplanır. Hassasiyet ve zaman penceresi ayarlanabilir.
- **Yapay zeka ile "en iyi" seçimi** — her grupta:
  - 👁 Gözler açık mı? (ML Kit yüz analizi)
  - 🙂 Yüz kameraya dönük mü, gülümseme var mı?
  - 🔍 Netlik (Laplacian varyansı) ve pozlama (histogram analizi)
  - 📐 Çözünürlük
  bileşik puanlanır; en yüksek puanlı kare **EN İYİ** işaretlenir, diğerleri silme adayı olur.
- **İnceleme ve onay** — grupları tek tek inceleyebilir, "en iyi"yi elle değiştirebilir,
  silinecekleri işaretleyebilir/çıkarabilirsiniz. Silme, tek bir sistem onayıyla toplu yapılır.
- **Tam ekran karşılaştırma** — grup içinde yan yana kaydırma, pinch/çift dokunuş zoom.
- **Bir daha gösterme** — yoksayılan gruplar sonraki taramalarda hiç önerilmez
  (Ayarlar'dan sıfırlanabilir).
- **Ayarlanabilir puanlama** — "en iyi" seçim ağırlıkları Ayarlar'dan değiştirilebilir;
  "Nasıl çalışır?" ekranı modeli ve algoritmayı açıkça anlatır.
- **Ekran görüntüleri** — otomatik tanınır, normal fotoğraflarla karışmaz; yalnızca
  neredeyse birebir aynı kopyalar (sıkı piksel karşılaştırması) gruplanır, en yenisi korunur.
- **Sessiz silme** — Medya yönetimi izni verildiyse elle silmede de sistem diyaloğu çıkmaz.
- **Çöp kutusu modu** (varsayılan) — silinenler 30 gün sistem çöp kutusunda bekler, geri alınabilir.
- **Otomatik pilot** — günlük/haftalık arka plan taraması; **Medya yönetimi** özel izni
  (Android 12+) verildiyse sistem diyaloğu olmadan otomatik temizler, sonuç bildirimle raporlanır.
- **Geçmiş** — tüm manuel/otomatik silmelerin kaydı.
- Türkçe (varsayılan) ve İngilizce arayüz.

## Teknik mimari

| Katman | Teknoloji |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3, Navigation |
| Mimari | MVVM + Repository, Hilt, Room, WorkManager, DataStore |
| Görüntü | Coil, MediaStore thumbnail API |
| AI | ML Kit Face Detection (cihaz içi), saf Kotlin dHash / Laplacian / histogram |
| Silme | `MediaStore.createTrashRequest` / `createDeleteRequest`, otomatik pilot için `MANAGE_MEDIA` |

- `app/src/main/java/com/bestphotoselect/domain/` — algoritma çekirdeği
  (**Android bağımsız, birim testli**: `DHash`, `PhotoGrouper`, `QualityScorer`, `BestPhotoSelector`)
- `data/` — MediaStore erişimi, Room önbelleği (artımlı tarama), ayarlar, tarama motoru
- `work/` — otomatik pilot WorkManager işleri
- `ui/` — ekranlar (albümler, tarama, sonuçlar, grup detayı, ayarlar, geçmiş)

## Derleme ve sürüm

Tek uygulama, tek sürüm: `app/` (v2.1.0, versionCode 19). APK yalnızca ARM
(arm64-v8a, armeabi-v7a) içerir.

```bash
./gradlew assembleRelease      # imzalı APK: app/build/outputs/apk/release/
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # birim testleri (saf JVM, emülatör gerekmez)
```

Gereksinimler: JDK 17+, Android SDK Platform 35. `minSdk 30` (Android 11+).

Release APK, eski lite APK'larla aynı anahtarla (`keystore/bestphoto.keystore`)
imzalanır; telefonda kurulu eski sürümün üzerine güncelleme olarak kurulur ve
eski sürümün ayarları, yoksayılan grupları ve geçmişi ilk açılışta otomatik
taşınır. Kurulabilir APK'yı `releases/BestPhotoSelect-v<sürüm>.apk` olarak
eklemeniz yeterli (yalnızca son sürüm tutulur).

## Sürüm notları

### v2.1.0 (versionCode 19)
- **Ekran görüntüsü tanıma:** klasör yolu (Screenshots, DCIM/Screenshots,
  Ekran görüntüleri…) ya da dosya adından (`Screenshot_…`) otomatik tanınır;
  normal fotoğraflarla asla aynı gruba konmaz.
- **Sıkı eşleştirme:** dHash yalnızca ön eleme yapar; adaylar durum/gezinme
  çubukları hariç piksel düzeyinde doğrulanır. Yalnızca saat veya sıkıştırma
  farkı olan kopyalar gruplanır, içeriği farklı ekranlar gruplanmaz.
- Ekran görüntüsü gruplarında kalite puanı yerine **en yeni kopya korunur**.
- Albümler, sonuçlar, grup detayı ve tam ekranda ekran görüntüsü grupları
  ayrıca işaretlenir; "Nasıl çalışır?" ekranına ilgili bölüm eklendi.

### v2.0.1 (versionCode 18)
- APK'dan emülatöre özel x86/x86_64 kütüphaneleri çıkarıldı; yalnızca
  arm64-v8a ve armeabi-v7a paketleniyor (daha küçük APK).

### v2.0.0 (versionCode 17)
- Lite sürüm kaldırıldı: **tek uygulama, tek sürüm** (Jetpack Compose).
- Lite'taki tüm özellikler taşındı: grup içi tam ekran karşılaştırma,
  kalıcı "Bir daha gösterme", ayarlanabilir "en iyi" ağırlıkları,
  "Nasıl çalışır?" ekranı, manuel silmede de sessiz mod (`MANAGE_MEDIA`).
- Aynı imza anahtarı: lite sürümün üzerine güncelleme olarak kurulur;
  ayarlar, yoksayılan gruplar ve geçmiş ilk açılışta otomatik taşınır.
- Tam ekran görüntüleyicide kaydırma ile zoom çakışması düzeltildi.

### v1.x (lite sürüm, arşiv)
v1.0–v1.10 arasında yüz analizi ile gruplama, konuşma anındaki açık ağız
tespiti, renkli tema, WCAG kontrast düzeltmeleri, "Nasıl çalışır?" ekranı ve
yoksayılan grupların kalıcı hale getirilmesi gibi özellikler eklendi. Bu
sürümler artık dağıtılmıyor.

## Yol haritası

- [x] Grup içi tam ekran karşılaştırma (yan yana kaydırma)
- [x] Ekran görüntüsü/screenshot klasörlerini otomatik tanıma
- [ ] iOS versiyonu (en son, ayrı proje olarak)
