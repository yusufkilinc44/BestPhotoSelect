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

## Derleme

```bash
# Android Studio (önerilen): projeyi açın, çalıştırın.
# Komut satırı:
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # birim testleri (saf JVM, emülatör gerekmez)
```

Gereksinimler: JDK 17+, Android SDK Platform 35. `minSdk 30` (Android 11+).

## Yol haritası

- [ ] iOS versiyonu (ayrı proje olarak planlandı)
- [ ] Ekran görüntüsü/screenshot klasörlerini otomatik tanıma
- [ ] Grup içi tam ekran karşılaştırma (yan yana kaydırma)
