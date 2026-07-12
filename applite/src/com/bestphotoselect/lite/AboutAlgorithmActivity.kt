package com.bestphotoselect.lite

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bestphotoselect.lite.Ui.dp

/**
 * Şeffaflık ekranı: hangi yapay zeka modelinin/motorunun ve hangi sürümün
 * kullanıldığını, benzerlik algoritmasının adımlarını ve "en iyi" seçim
 * puanlamasındaki ağırlıkları kullanıcıya açıkça anlatır.
 *
 * ÖNEMLİ: Buradaki sayılar BestPhotoSelector.kt / DHash.kt / PhotoGrouper.kt /
 * FaceAnalyzerLite.kt içindeki gerçek sabitlerden elle kopyalanmıştır. O
 * dosyalardaki ağırlıklar/eşikler değişirse bu metin de güncellenmelidir.
 */
class AboutAlgorithmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.screenRoot(this, statusBarColor = Ui.Screens.SETTINGS.dark)
        root.addView(
            Ui.gradientHeader(
                this, "🧠 Yapay Zeka ve Algoritma",
                "Bu uygulama fotoğrafları nasıl karşılaştırıyor?",
                Ui.Screens.SETTINGS
            )
        )

        val content = Ui.vbox(this).apply {
            val p = dp(this@AboutAlgorithmActivity, 16)
            setPadding(p, dp(this@AboutAlgorithmActivity, 8), p, dp(this@AboutAlgorithmActivity, 28))
        }

        // ---- 1) Model ve sürüm ----
        content.addView(card {
            addView(Ui.sectionTitle(this@AboutAlgorithmActivity, "👁 Yüz Analizi Modeli"))
            addView(bullet("Model: Google MediaPipe Face Mesh — 468 noktalı yüz ağı (face_landmark.tflite)"))
            addView(bullet("Çalıştırma motoru: TensorFlow Lite 2.14.0"))
            addView(bullet("Ön adım: Android'in yerleşik yüz dedektörü (android.media.FaceDetector) ile önce kaba yüz kutusu ve sayısı bulunur, sonra her yüz kırpılıp MediaPipe modeline verilir"))
            addView(bullet("Tamamı cihaz içinde çalışır: internet bağlantısı gerekmez, hiçbir fotoğraf cihazdan dışarı çıkmaz"))
        })

        // ---- 2) Benzerlik nasıl bulunuyor ----
        content.addView(card {
            addView(Ui.sectionTitle(this@AboutAlgorithmActivity, "🔎 Benzerlik Nasıl Bulunuyor? (sırayla)"))
            addView(step(1, "Her fotoğraf 384 piksellik bir küçük resme indirgenir (analiz hızlı olsun diye)."))
            addView(step(2, "Algısal parmak izi (dHash): küçük resim 9×8 gri tonlamaya indirgenir, komşu piksellerin parlaklığı karşılaştırılarak 64 bitlik bir \"parmak izi\" üretilir."))
            addView(step(3, "Zaman penceresi: yalnızca birbirine belirli bir süre içinde çekilmiş fotoğraflar karşılaştırılır — varsayılan 60 saniye, Ayarlar'dan 10-600 saniye arasında değiştirilebilir."))
            addView(step(4, "Hash mesafesi: iki parmak izi arasındaki farklı bit sayısına (Hamming mesafesi) bakılır; eşiğin altındaysa \"görsel olarak benzer\" sayılır — varsayılan eşik 64 bit üzerinden 10, Ayarlar'daki \"hassasiyet\" kaydırıcısıyla 4-16 arası ayarlanabilir."))
            addView(step(5, "Yüz sayısı kontrolü: görsel olarak benzer bulunsa bile, iki karedeki kişi sayısı 1'den fazla farklıysa (ör. 3 kişi vs 1 kişi) bu ikili HİÇBİR ZAMAN aynı gruba konmaz. Bu, aynı arka planda farklı kişileri gösteren kareleri yanlışlıkla \"benzer\" saymayı önler."))
            addView(step(6, "Gruplama: yukarıdaki kriterlerle birbirine bağlanan tüm fotoğraflar tek bir grupta toplanır (geçişli: A~B ve B~C benzerse A, B, C aynı grupta sayılır)."))
        })

        // ---- 3) En iyi seçim puanlaması ----
        content.addView(card {
            addView(Ui.sectionTitle(this@AboutAlgorithmActivity, "🏆 \"En İyi\" Nasıl Seçiliyor?"))
            addView(Ui.body(this@AboutAlgorithmActivity, "Bir grupta en az bir yüz tespit edildiyse:", dim = true))
            addView(weightRow("%40", "Yüz kalitesi", "İçinde: gözler açık %45 · yüze dönüklük %35 · gülümseme %20"))
            addView(weightRow("%35", "Netlik", "Laplacian varyansı, grup içindeki en netine oranla"))
            addView(weightRow("%15", "Pozlama", "Aşırı karanlık/patlamış piksel oranı + ortalama parlaklık"))
            addView(weightRow("%10", "Çözünürlük", "Grup içindeki en yükseğe oranla"))
            addView(Ui.body(this@AboutAlgorithmActivity, "(Bu karede yüz tespit edilemediyse ama grupta başka karelerde yüz varsa, yüz kalitesi yerine sabit düşük bir taban puan olan %15 kullanılır.)", dim = true).apply {
                setPadding(0, dp(this@AboutAlgorithmActivity, 6), 0, 0)
            })
            addView(Ui.body(this@AboutAlgorithmActivity, "Grupta hiç yüz yoksa (manzara, nesne vb.):", dim = true).apply {
                setPadding(0, dp(this@AboutAlgorithmActivity, 12), 0, 0)
            })
            addView(weightRow("%55", "Netlik", null))
            addView(weightRow("%30", "Pozlama", null))
            addView(weightRow("%15", "Çözünürlük", null))
        })

        // ---- 4) Gizlilik ----
        content.addView(card {
            addView(Ui.sectionTitle(this@AboutAlgorithmActivity, "🔐 Gizlilik"))
            addView(bullet("Tüm analiz (hash, netlik/pozlama ölçümü, yüz analizi) cihazınızda çalışır."))
            addView(bullet("Hiçbir fotoğraf veya analiz sonucu internete/bir sunucuya gönderilmez."))
        })

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    // ---------- Yardımcılar ----------

    private fun card(builder: LinearLayout.() -> Unit): LinearLayout {
        val c = Ui.vbox(this)
        Ui.cardify(c, Ui.card(this), radiusDp = 18, elevationDp = 2)
        val p = dp(this, 14)
        c.setPadding(p, dp(this, 12), p, p)
        c.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(this@AboutAlgorithmActivity, 10), 0, 0) }
        c.builder()
        return c
    }

    private fun bullet(text: String): TextView = TextView(this).apply {
        this.text = "•  $text"
        textSize = 13f
        setTextColor(Ui.text(this@AboutAlgorithmActivity))
        setPadding(0, dp(this@AboutAlgorithmActivity, 6), 0, 0)
    }

    private fun step(n: Int, text: String): TextView = TextView(this).apply {
        this.text = "$n.  $text"
        textSize = 13f
        setTextColor(Ui.text(this@AboutAlgorithmActivity))
        setPadding(0, dp(this@AboutAlgorithmActivity, 8), 0, 0)
    }

    private fun weightRow(percent: String, label: String, sub: String?): LinearLayout {
        val row = Ui.hbox(this).apply {
            setPadding(0, dp(this@AboutAlgorithmActivity, 8), 0, 0)
        }
        row.addView(Ui.chip(this, percent, Ui.Screens.SETTINGS.main))
        val texts = Ui.vbox(this).apply {
            setPadding(dp(this@AboutAlgorithmActivity, 10), 0, 0, 0)
        }
        texts.addView(TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@AboutAlgorithmActivity))
        })
        sub?.let { texts.addView(Ui.body(this@AboutAlgorithmActivity, it, dim = true)) }
        row.addView(texts)
        return row
    }
}
