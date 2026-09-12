# TRT Bil Bakalım - Soru Arşivi ve Otomatik Oynama Botu

Bu Android Studio projesi, **TRT Bil Bakalım** oyunu için geliştirilmiş erişilebilirlik ve Room veritabanı tabanlı otomatik oynama ve soru arşivleme uygulamasıdır.

## 🚀 Yeni Özellikler (v2.0)
1. **Manuel / Otomatik Bot Modu:**
   - **Manuel Mod:** Ekrana tıklama yapmaz. Siz normal oynarken soruları ve doğru cevapları Room veritabanına kaydeder.
   - **Otomatik Bot Modu:** Soru geldiğinde hafızasında varsa doğrudan doğru şıkka tıklar. Yeni soru ise rastgele dener, doğru cevabı yeşil renkten hafızasına kaydeder.
2. **Ayarlanabilir Tıklama Gecikmesi (ms):**
   - İster el ile milisaniye girin (ör. 1200ms), ister hazır butonlardan (500ms Hızlı, 1200ms Normal, 2500ms Doğal) seçin.
3. **Kesintisiz 'Yeni Oyun' Döngüsü:**
   - Oyun bittiğinde beliren '108-84 Tebrikler kazandınız' skor ekranında sağ alttaki 'Yeni Oyun' butonunu otomatik algılar ve tıklar.
4. **Çift Katmanlı Tıklama Garantisi:**
   - Standart `ACTION_CLICK` düğüm tıklaması ve `dispatchGesture` koordinat dokunma simülasyonu ile her cihazda çalışır.

## 📱 Nasıl Yüklenir ve Çalıştırılır?
1. Bu zip dosyasını bir klasöre çıkartın.
2. **Android Studio**'yu açıp **Open** diyerek `SoruArsivi` klasörünü seçin.
3. Gradle senkronizasyonunun bitmesini bekleyin ve telefonunuza yükleyin.
4. Telefonunuzun **Ayarlar > Erişilebilirlik** menüsüne girip **Soru Arşivi ve Otomatik Bot** servisini açın.
5. TRT Bil Bakalım oyununu açın ve arkanıza yaslanın!
