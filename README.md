# BestPhotoSelect 📸

Galerinizdeki **benzer/tekrar eden fotoğrafları bulan**, her grupta yapay zeka ile
**en iyi kareyi seçen** ve gerisini onayınızla (ya da otomatik pilotta onaysız)
temizleyen Android uygulaması.

Tüm analiz **cihaz içinde** yapılır: fotoğraflarınız telefonunuzdan asla çıkmaz,
internet bağlantısı gerekmez.

## İndir

📥 **[En son sürümü indir (GitHub Releases)](https://github.com/yusufkilinc44/BestPhotoSelect/releases/latest)**
(Android 11+, ARM). Bağlantı her zaman en son yayını açar; sayfadaki **Assets** bölümünden
`BestPhotoSelect-v<sürüm>.apk` dosyasını indirin. Yeni sürümler ntfy'de
`yusufkilinc-bestphotoselect` konusuna bildirilir.

## Kurulum

1. Yukarıdaki Release sayfasından APK'yı telefona indirin.
2. Gerekirse tarayıcınıza/dosya yöneticinize "bilinmeyen kaynaklardan yükleme" izni verin.
3. APK'yı açıp kurun. Eski bir BestPhotoSelect (lite dahil) kuruluysa üzerine
   güncellenir; ayarlar, yoksayılan gruplar ve geçmiş korunur.
4. İlk açılışta fotoğraflara erişim izni verin. Sessiz silme ve otomatik pilot için
   Ayarlar'dan **Medya yönetimi** iznini, sonuç bildirimleri için bildirim iznini açın.

Gereksinim: Android 11 (API 30) veya üzeri, ARM işlemcili telefon.

## Özellikler

- **Albüm seçimi** — galerideki albümlerden hangilerinin taranacağını siz seçersiniz.
- **Benzerlik gruplama** — algısal hash (dHash) + çekim zamanı yakınlığı ile art arda
  çekilmiş benzer kareler otomatik gruplanır. Yüz sayısı belirgin şekilde farklı kareler
  (ör. 1 kişi / 4 kişi) aynı gruba konmaz. Hassasiyet ve zaman penceresi ayarlanabilir.
- **Yapay zeka ile "en iyi" seçimi** — her grupta:
  - 👁 Gözler açık mı? (ML Kit yüz analizi)
  - 🙂 Yüz kameraya dönük mü, gülümseme var mı?
  - 👄 Konuşurken yakalanmış garip açık ağız var mı?
  - 🔍 Netlik (Laplacian varyansı) ve pozlama (histogram analizi)
  - 📐 Çözünürlük

  bileşik puanlanır; en yüksek puanlı kare **EN İYİ** işaretlenir, diğerleri silme adayı olur.
- **Ekran görüntüleri** — klasör yolu veya dosya adından otomatik tanınır, normal
  fotoğraflarla karışmaz; yalnızca neredeyse birebir aynı kopyalar (sıkı piksel
  karşılaştırması) gruplanır ve en yeni kopya korunur.
- **İnceleme ve onay** — grupları tek tek inceleyebilir, "en iyi"yi elle değiştirebilir,
  silinecekleri işaretleyebilir/çıkarabilirsiniz. İster sonuçlar ekranından tümünü tek
  seferde, ister grup ekranındaki **"İşaretlileri sil"** tuşuyla yalnızca o grubu silip
  otomatik olarak sıradaki gruba geçerek teker teker temizleyebilirsiniz.
- **Tam ekran karşılaştırma** — grup içinde yan yana kaydırma, pinch/çift dokunuş zoom;
  tam ekrandan "en iyi yap" / "sil işaretle".
- **Bir daha gösterme** — yoksayılan gruplar sonraki taramalarda (otomatik pilot dahil)
  hiç önerilmez; Ayarlar'dan sıfırlanabilir.
- **Ayarlanabilir puanlama** — "en iyi" seçim ağırlıkları (yüz kalitesi, netlik, pozlama,
  çözünürlük; göz, yüz yönü, gülümseme, ağız) Ayarlar'dan değiştirilebilir.
- **"Nasıl çalışır?" ekranı** — kullanılan model, algoritma ve oranlar açıkça anlatılır.
- **Artımlı tarama** — analiz sonuçları önbelleğe alınır; sonraki taramalarda yalnızca
  yeni/değişen fotoğraflar işlenir.
- **Çöp kutusu modu** (varsayılan) — silinenler 30 gün sistem çöp kutusunda bekler, geri alınabilir.
- **Sessiz silme** — Medya yönetimi izni verildiyse elle silmede de sistem diyaloğu çıkmaz.
- **Otomatik pilot** — günlük/haftalık arka plan taraması (pil düşük değilken);
  **Medya yönetimi** izni verildiyse sistem diyaloğu olmadan otomatik temizler,
  sonuç bildirimle raporlanır.
- **Geçmiş** — tüm manuel/otomatik silmelerin kaydı.
- Türkçe (varsayılan) ve İngilizce arayüz, açık/koyu tema.

## Teknik mimari

| Katman | Teknoloji |
|---|---|
| UI | Kotlin 2.0, Jetpack Compose (BOM 2024.10), Material 3, Navigation |
| Mimari | MVVM + Repository, Hilt, Room, WorkManager, DataStore |
| Görüntü | Coil, MediaStore thumbnail API |
| AI | ML Kit Face Detection (cihaz içi), saf Kotlin dHash / Laplacian / histogram / piksel karşılaştırma |
| Silme | `MediaStore.createTrashRequest` / `createDeleteRequest`, sessiz silme için `MANAGE_MEDIA` |
| Derleme | AGP 8.7, Gradle 8.14, compileSdk/targetSdk 35, minSdk 30 |

`app/src/main/java/com/bestphotoselect/` altında:

- `domain/` — algoritma çekirdeği (**Android bağımsız, birim testli**):
  `DHash`, `PhotoGrouper`, `QualityScorer`, `BestPhotoSelector`,
  `ScreenshotDetector`, `ScreenshotMatcher`; ayrıca ML Kit sarmalayıcısı `FaceAnalyzer`
- `data/` — MediaStore erişimi, Room önbelleği (artımlı tarama), ayarlar, tarama motoru,
  silme ve yoksayılan gruplar depoları, lite sürümden veri taşıma
- `work/` — otomatik pilot WorkManager işleri
- `ui/` — ekranlar: albümler, tarama, sonuçlar, grup detayı + tam ekran görüntüleyici,
  ayarlar, geçmiş, "Nasıl çalışır?"

## Derleme ve sürüm

Tek uygulama, tek sürüm: `app/` (v2.5.0, versionCode 28). APK yalnızca ARM
(arm64-v8a, armeabi-v7a) içerir.

```bash
./gradlew assembleRelease      # imzalı APK: app/build/outputs/apk/release/
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # birim testleri (saf JVM, emülatör gerekmez)
```

Gereksinimler: JDK 17+, Android SDK Platform 35 ve Build-Tools 35
(`local.properties` içinde `sdk.dir`).

Birim testleri: `DHash`, `PhotoGrouper`, `QualityScorer`, `BestPhotoSelector`, ekran görüntüsü
tanıma/eşleştirme, bir aradalık ve kompozisyon temizliği ölçütleri.

Release APK, eski lite APK'larla aynı anahtarla (`keystore/bestphoto.keystore`)
imzalanır; telefonda kurulu eski sürümün üzerine güncelleme olarak kurulur.
APK'lar repoda tutulmaz; yalnızca GitHub Releases üzerinden dağıtılır.

**Yeni sürüm yayınlama:**
1. `app/build.gradle.kts` içinde `versionCode` ve `versionName`'i artırın, push edin.
2. GitHub → Actions → **Release** → *Run workflow*, sürüm numarasını girin
   (önce denemek için *dry_run* işaretlenebilir; APK yalnızca artifact olarak yüklenir).
3. Workflow birim testlerini çalıştırır, APK'yı derleyip imzalar; paket adı, sürüm,
   ABI'ler ve imza parmak izi beklenenle eşleşmezse Release oluşturmaz.
4. Sürüm notlarını güncelleyin (indirme bağlantısı her zaman en son Release'i açar).
5. Bildirim telefona ulaşmadıysa Actions → **Bildirim gönder** ile yayınlanmış bir
   sürümün ntfy bildirimi derleme yapmadan yeniden gönderilebilir.

İmza anahtarı GitHub Secrets'tan okunur (`BESTPHOTO_KEYSTORE_BASE64`,
`BESTPHOTO_KEYSTORE_PASSWORD`, `BESTPHOTO_KEY_ALIAS`, `BESTPHOTO_KEY_PASSWORD`);
tanımlı değilse repodaki `keystore/bestphoto.keystore` kullanılır. Yerel derlemede
aynı adlı ortam değişkenleri verilebilir.

## Sürüm notları

### v2.5.0 (versionCode 28)
- Uygulama adı **"Best Photo Select"** oldu.
- **Yeni ikon:** lacivert zeminde altı renkli kamera diyaframı, ortada yeşil onay işareti.
  Android 13+ temalı (tek renk) ikonu da destekleniyor.
- **Albümler: ızgara / liste görünümü** — üst çubuktaki düğmeyle değiştirilir, seçim saklanır.
- **"WhatsApp klasörlerini tara" anahtarı** — kapalıyken WhatsApp (ve WhatsApp Business)
  klasörleri soluk görünür, seçilemez, seçimden çıkarılır ve "Tümünü seç"e dahil edilmez.

### v2.4.0 (versionCode 27)
- **Yeni puan ölçütü — kompozisyon temizliği:** cihaz içi görüntü etiketleme (ML Kit
  image-labeling 17.0.9) ile karedeki nesneler tanınır; grubun diğer karelerinde olmayan
  ama bu karede beliren yabancı öğe (araya giren gemi, araba, kuş vb.) puanı düşürür.
  Nesne düzeyinde karşılaştırıldığı için kadraj kayması sonucu etkilemez.
- **Yeni puan ölçütü — kişilerin bir arada olması:** birden fazla kişili karelerde kişiler
  birbirine yakınsa yüksek, bir ya da birkaç kişi gruptan kopuksa düşük puan.
- İki ölçüt de Ayarlar > "En iyi seçim ağırlıkları"ndan ayarlanabilir (varsayılan 25 ve 20);
  uygulanamadıkları gruplarda hesaptan çıkarılır.
- Benzer fotoğraf zaman penceresi varsayılanı 60 saniyeden **3 dakikaya** çıktı
  (kayıtlı değer eski varsayılan 60 sn ise bir kez yükseltilir).
- Analiz önbelleği yeni alanlar için bir kez sıfırlanır; ilk tarama biraz uzun sürer.

### v2.3.0 (versionCode 26)
- **Yatay mod için yeni grup ekranı:** başlık ve açıklama yazıları kalkar; fotoğraflar
  ekran yüksekliğinin tamamını kullanır, kırpılmadan gösterilir ve yan yana
  kaydırılır (bir sonrakinin kenarı görünür, kaydırınca ortaya oturur). Her fotoğrafın
  sil/koru, puan ve "en iyi" kontrolleri üst kenarında yarı saydam şeritte; silinecek
  kareler kırmızı çerçeveli. Grubun genel tuşları (← önceki · bir daha gösterme ·
  işaretlileri sil · sonraki →) fotoğrafların üstünde yüzen tek bir kapsülde.
- Tam ekran görüntüleyicide yatayda bilgi ve tuşlar tek satıra iner.
- Telefon çevrildiğinde açık olan tam ekran görüntüleyici kapanmaz.
- Dikey mod değişmedi.

### v2.2.4 (versionCode 25)
- Tam ekran görüntüleyicinin alt tuşları v2.2.2 düzeltmesine rağmen gezinme
  çubuğuyla çakışmaya devam ediyordu: ayrı Dialog penceresi sistem çubuğu
  boşluklarını iletmiyordu. Görüntüleyici artık ana pencerede tüm ekranı kaplayan
  bir katman; alt tuşlar gezinme çubuğunun hemen üstünde.

### v2.2.3 (versionCode 24)
- Grup ekranına **"← Önceki grup"** tuşu eklendi ("Sonraki grup →" ile aynı satırda).
  Listenin başında/sonunda karşı uca geçer.

### v2.2.2 (versionCode 23)
- Tam ekran görüntüleyicide alttaki puan ve "en iyi yap / sil işaretle" tuşları
  Android'in gezinme çubuğunun altında kalıyordu; artık hemen üstünde duruyor
  (fotoğraf yine tam ekran). Tarama ekranı da sistem çubuklarına göre hizalandı.

### v2.2.1 (versionCode 22)
- **Silme onayı bir kez:** kurulumdan sonra ilk açılışta "Medya yönetimi" izni bir
  kez açıklanıp ilgili sistem ekranı açılır; izin verildikten sonra silmelerde
  Android'in onay penceresi bir daha çıkmaz. Soru yalnızca bir kez gösterilir.

### v2.2.0 (versionCode 21)
- **Grup bazında silme:** grup ekranında "İşaretlileri sil" tuşu yalnızca o gruptaki
  işaretli fotoğrafları siler (Medya yönetimi izni varsa sistem onayı sorulmaz) ve
  otomatik olarak sıradaki gruba geçer; son gruptan sonra sonuçlara dönülür.
- **Sonraki grup →** tuşuyla silmeden geçilebilir; "Bir daha gösterme" de artık
  sıradaki gruba geçirir. Geri tuşu doğrudan sonuçlar ekranını açar.

### v2.1.1 (versionCode 20)
- Yeniden imzalanmış bakım sürümü; uygulama davranışında değişiklik yok.
- GitHub Release oluşturmak için `Release` workflow'u eklendi
  (`.github/workflows/release.yml`, elle tetiklenir).

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

- [x] Grup içi tam ekran karşılaştırma (yan yana kaydırma, zoom) — v2.0.0
- [x] Kalıcı "Bir daha gösterme" ve ayarlanabilir "en iyi" ağırlıkları — v2.0.0
- [x] Lite sürümü birleştirme: tek uygulama, tek sürüm — v2.0.0
- [x] Yalnızca ARM mimarileri ile küçültülmüş APK — v2.0.1
- [x] Ekran görüntülerini otomatik tanıma ve sıkı kopya eşleştirme — v2.1.0
- [ ] iOS versiyonu (en son, ayrı proje olarak)
