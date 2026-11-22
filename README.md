# BestPhotoSelect 📸

Galerinizdeki **benzer/tekrar eden fotoğrafları bulan**, her grupta yapay zeka ile
**en iyi kareyi seçen** ve gerisini onayınızla (ya da otomatik pilotta onaysız)
temizleyen Android uygulaması.

Tüm analiz **cihaz içinde** yapılır: fotoğraflarınız telefonunuzdan asla çıkmaz,
internet bağlantısı gerekmez.

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

## Yol haritası

- [x] Grup içi tam ekran karşılaştırma (yan yana kaydırma)
- [x] Ekran görüntüsü/screenshot klasörlerini otomatik tanıma
- [ ] iOS versiyonu (en son, ayrı proje olarak)
