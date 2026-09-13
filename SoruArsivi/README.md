# Soru Arşivi

Ekrandaki bilgi yarışması sorularını **otomatik olarak** okuyup telefonun kendi
veritabanına kaydeden küçük bir Android uygulaması. Soru metnini, dört şıkkı ve
(cevap açıldıktan sonra) doğru cevabı tutar; aynı soruyu iki kez kaydetmez.

İki modu var:

* **Manuel** — oyunu sen oynarsın, uygulama sadece okuyup kaydeder. Kayıt için
  hiçbir tuşa basmazsın.
* **Otomatik** — oyunu uygulama oynar: soruyu daha önce görmüşse doğru şıkka,
  görmemişse rastgele birine dokunur; tur bitince "Tekrar Oyna"ya basar.
  Telefonu bırakıp gidersin, arşiv kendi kendine dolar.

---

## Ne yapar, ne yapmaz

**Yapar**

* Seçtiğin uygulamada ekran her değiştiğinde soruyu okur.
* Önce Android'in erişilebilirlik ağacından metni **doğrudan** alır. Bu yol
  hatasızdır: ı, ğ, ş, ö, ç, ü hiç bozulmaz ve pil neredeyse hiç harcamaz.
* Metin bu yolla alınamıyorsa (uygulama yazıyı kendi çiziyorsa) ekran görüntüsü
  alıp **çevrimdışı OCR** yapar. İnternet gerekmez.
* Cevap verildikten sonra yeşile dönen şıkkı doğru cevap olarak işaretler.
* Her şeyi cihazdaki yerel veritabanında tutar. CSV / JSON / Anki olarak dışa aktarır.
* Otomatik modda oyunu kendisi oynar (aşağıya bak).

**Yapmaz**

* **Manuel modda** hedef uygulamaya dokunmaz: tıklama, kaydırma, jest
  göndermez. Sadece ekranda görüneni okur. Otomatik modda yalnızca iki yere
  dokunur: şıklara ve tur sonundaki yeniden başlatma düğmesine.
* Okuduğu hiçbir şeyi internete göndermez.
* Seçmediğin uygulamalarda çalışmaz — erişilebilirlik servisi yalnızca
  işaretlediğin paketleri dinleyecek şekilde ayarlanır.

---

## APK'yı nasıl alırım?

### Yol 1 — GitHub Actions (bilgisayar gerekmez, önerilen)

1. GitHub'da yeni ve **boş** bir depo (repository) aç.
2. Bu klasördeki dosyaları olduğu gibi depoya yükle.
3. Depodaki **Actions** sekmesine gir → **APK Derle** → **Run workflow**.
4. 3–5 dakika sonra çalışmanın altındaki **Artifacts** bölümünden
   `SoruArsivi-APK` dosyasını indir, içindeki `SoruArsivi-debug.apk`'yı kur.

Telefondan kurarken Android "bilinmeyen kaynak" uyarısı verir; tarayıcına veya
dosya yöneticine bu izni bir kez vermen gerekir.

### Yol 2 — Android Studio

Klasörü Android Studio ile aç, `Run` de. Gradle gerisini halleder.
Gereken: JDK 17, Android SDK 35.

### Yol 3 — Komut satırı

```bash
./gradlew assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
```

---

## İlk çalıştırma — sırayla

1. **Uygulamayı aç.** Kırmızı bir "Yakalama kapalı" kartı göreceksin.
2. **"Erişilebilirlik ayarlarını aç"** düğmesine bas. Açılan sistem listesinde
   *Soru Arşivi*'ni bul ve aç. (Android bunu genelde "İndirilen uygulamalar",
   "Yüklü servisler" veya "Ek indirilen hizmetler" başlığı altında gösterir.)
3. Uygulamaya dön, **"Hangi uygulamayı izleyeyim?"** düğmesine bas ve listeden
   yarışma uygulamasını işaretle.
4. Ana ekranda kaydedilecek **kategoriyi** seç (ör. *Felsefe*). Böylece o
   oturumda yakalanan her soru bu etiketle kaydedilir.
5. Kart yeşile döndüyse hazırsın. Oyunu aç ve normal şekilde oyna.

Kayıt sayısı arttıkça bildirim çubuğunda sessizce güncellenir; ana ekranda da
toplam, cevabı bilinen ve cevabı eksik sayılarını görürsün.

---

## Manuel ve Otomatik mod

Ana ekrandaki **Manuel / Otomatik** düğmelerinden seçilir; ayrıntılı ayarlar
Ayarlar → *Oynatma modu* altında.

### Otomatik mod ne yapıyor?

1. Soru ekrana gelip dört şık da yerine oturunca, ayarlanan bekleme süresi
   (varsayılan **900 ms**) sonunda bir şıkka dokunur:
   * soru arşivde varsa ve cevabı biliniyorsa **doğru şıkka**,
   * bilinmiyorsa **rastgele** birine.
2. Dokunuş normal bir parmak dokunuşuyla aynı yoldan gider; oyun ayırt etmez.
   Cevabın rengini okuyan mantık hiç değişmeden çalışır, yani doğru cevap yine
   kaydedilir.
3. Ekranda bir süredir soru görünmüyorsa tur bitmiş sayılır ve ekranda
   **"Tekrar Oyna"**, "Yeni Oyun", "Devam Et" gibi bir yazı aranır; bulunursa
   ona basılıp yeni tur başlatılır.

Böylece başında beklemeden, oyunun soru havuzu tükenene kadar arşiv dolar.

### Ayarlar

| Ayar | Ne işe yarar |
|---|---|
| **Otomatik oyna** | Modu açar/kapatır. Kapalıyken ekrana hiç dokunulmaz. |
| **Tur bitince yeniden başlat** | Kapalıysa soruları cevaplar ama tur bitince bekler. |
| **Bilinen cevabı kullan** | Varsayılan **açık**. Arşivde cevabı olan bir soru yeniden çıkarsa doğru şık seçilir; oyunda daha uzun kalırsın, tur başına daha çok **yeni** soru görürsün. Kapatırsan seçim her zaman rastgele olur. |
| **Dokunmadan önce bekleme** | 300–3000 ms. Çok kısa tutarsan soru dört şık tamamlanmadan cevaplanır; çok uzun tutarsan süre dolar. |

### Şıklar karışıyor — sıra değil metin eşleştiriliyor

Oyun şıkları her turda karıştırıyor. Bu yüzden "doğru cevap 2. şık" bilgisi
tek başına hiçbir işe yaramaz: ilk karşılaşmada 2. sırada duran şık ikinci
karşılaşmada 4. sırada olabilir. Uygulama bu yüzden her iki yönde de **metni**
eşleştiriyor:

* **Dokunurken** — kayıttaki doğru cevabın metni alınıp o anki ekran listesinde
  aranır; bulunan sıraya dokunulur.
* **Kaydederken** — ekranda yeşile dönen şıkkın metni alınıp kayıttaki listede
  aranır; bulunan sıra yazılır.

Eşleştirme Türkçe'ye duyarlıdır (büyük/küçük harf, ı/ğ/ş/ö/ç/ü, noktalama ve
"A)" gibi şık işaretleri yok sayılır) ve OCR'ın bir iki harfi yanlış okumasına
dayanıklıdır. Hiçbir şık yeterince benzemiyorsa eşleştirme başarısız sayılır:
dokunurken rastgele seçime düşülür, kaydederken satır olduğu gibi bırakılır.
Yanlış şıkka basmak ya da arşive yanlış cevap yazmaktansa bilmediğini söylemek
yeğdir.

### Dokunuş yutulursa ne oluyor?

Jest sisteme başarıyla gönderilse bile oyunun onu yuttuğu oluyor: soru ekranda
kalıyor, uygulama ise dokunduğunu sanıp bekliyordu. Sonuç, süre dolana kadar
boşa geçen bir dakikaydı.

Artık dokunuş **doğrulanıyor**: soru hâlâ cevaplanmamışsa 2,5 saniye sonra
aynı şıkka yeniden dokunuluyor (bu sefer biraz daha uzun basarak), en fazla
üç kez. Ayrıca şıklar animasyonla yerine oturduğu için, soru her yeniden
okunduğunda şık kutuları tazeleniyor — ilk okumadaki konum kaymışsa dokunuş
boşluğa gitmiyor.

### Güvenlik frenleri

* Yalnızca **tanıdığı** yazılara basar. "Çıkış", "Hayır", "Vazgeç" listede yok.
* "Atla", "Devam", "Kapat" gibi soru ekranında da bulunabilen yazılara ancak
  **7 saniyedir** hiç soru görülmediyse dokunur.
* Aynı düğmeye üst üste 4 kez basıp hiçbir şey değişmezse 30 saniye ara verir —
  sonsuz döngüye girmez.
* Hedef oyun önplandan çıkarsa hiçbir şeye dokunmaz.
* Aynı soruya iki kez basmaz; dokunuş üç kez başarısız olursa o soruyu geçer
  (süre dolunca oyun doğru cevabı zaten açıyor, uygulama da onu okuyor).

Dokunuş için erişilebilirlik servisinin **jest izni** kullanılır
(`canPerformGestures`). Servisi bu sürümden önce açtıysan bir kez kapatıp
yeniden açman gerekebilir; yoksa dokunuşlar sessizce başarısız olur ve Teşhis
ekranında `otomatik: … dokunulamadı` satırlarını görürsün.

Otomatik mod hızlı yakalama açıkken belirgin şekilde iyi çalışır: karar
ekranı yarım saniyede geçtiği için, saniyede tek kare alınabilen yolda bazı
cevaplar kaçar.

---

## Hızlı yakalama (önemli)

Uygulama ekran karesini iki yoldan alabiliyor ve aradaki fark işe yarayıp
yaramamasını belirliyor:

| | Erişilebilirlik görüntüsü | Ekran yansıtma |
|---|---|---|
| Hız sınırı | **saniyede 1 kare** (Android'in sabit sınırı) | sınır yok |
| Şıkların teker teker belirmesi | sık sık kaçar | yakalanır |
| Cevabın açıldığı ~0,5 sn | çoğu zaman kaçar | saniyede ~8 kez bakılır |
| İzin | tek seferlik | her açılışta onay |

Ana ekrandaki **"Hızlı yakalamayı aç"** düğmesi ekran yansıtmayı başlatır.
Telefonu yeniden başlattığında kapanır, tekrar açman gerekir. Görüntü
telefondan dışarı gönderilmez.

Yansıtma açıkken ekran değişmediğinde sistem yeni kare üretmez; uygulama bunu
bedava bir "değişiklik yok" sinyali olarak kullanır, yani boşta dönerken
neredeyse hiçbir iş yapmaz.

## Şıkları sayı olan sorular

Matematik sorularında şıkların kendisi sayıdır: *25 / 55 / 5 / 15*. Uygulama
sayaç, altın, süre ve soru numarası balonlarını elemek için sayı süzgeci
kullanıyor — ama bu süzgeç bütün ekrana aynı sertlikte uygulandığında bu
şıkların dördü birden eleniyor, geriye üçten az metin kalıyor ve soru hiç
yakalanamıyordu. (Teşhis'te `anlamlı metin 3'ten az` / `şık bölgesinde 3'ten
az metin` satırları bunun izidir.) Otomatik mod da dokunacak şık bulamadığı
için ekranda süre dolana kadar bekliyordu.

Süzgeç artık konuma duyarlı: sayı kuralları yalnızca **şık bölgesinin
dışında** işletiliyor. Aynı "55" metni ekranın tepesinde sayaçtır ve elenir,
şık bölgesinde şıktır ve kalır. Tek haneli şıklar ("5") de artık düşmüyor.

Şık bölgesinin alt ucuna giren joker bedeli rozetleri ("200 200 100") için
ayrı bir kural var: şıklar alt alta tek tek dizilir, rozetler ise aynı satırda
üç ya da daha fazla sayıdır — o satır atılır. Atmak listeyi üçün altına
düşürecekse dokunulmaz, çünkü o zaman rozet sandıklarımız gerçekten şıktır.

Aynı düzeltme yıl, tarih ve yüzde şıklarını da kurtarıyor.

## Soru numarası: "bu hâlâ aynı soru"

Soru bir kez düzgün okunduktan sonra ekran değişmeye devam ediyor: cevap
açılıyor, şıklar renk değiştiriyor, üstlerine "+5" puan balonu düşüyor,
sonra şıklar sırayla sönüyor. Bunların hiçbiri yeni bir soru değil — ama
metin değiştiği için parmak izi de değişiyor ve uygulama yeni soru sanıp
bozuk bir kayıt daha açıyordu. Arşivde şıkkı **"5 +5"** olan sorular bundan.

Soru kartının sol üstündeki sıra numarası ("2.") bu karmaşada değişmeyen tek
şey. Dört şıkkıyla birlikte kaydedilen soru artık o numaraya kilitleniyor:
numara artana kadar ekranda ne olursa olsun yeni kayıt açılmıyor (şık
kutuları yine de tazeleniyor, çünkü otomatik modun dokunacağı yer orası).

Ekrandaki başka sayılardan (altın, yıldız, sayaç, üstteki "1 2 3 4 5 6 7"
şeridi) ayırmak için üç işaret kullanılıyor: aynı satırda üç ya da daha fazla
sayı varsa o ilerleme şerididir; numara solda durur (sağdaki aynı hizadaki
sayı geri sayım sayacıdır); ve soru metnine yakın olmak zorundadır.

Son kural en önemlisi. Onsuz, numara bir karede okunamadığında altın sayısı
gibi **hiç değişmeyen** bir sayı seçilebilir — o da "soru hâlâ aynı" demek
olur ve yakalama tümden durur. Emin olunamadığında numara yok sayılıyor ve
eski davranışa dönülüyor. İkinci bir emniyet daha var: soru metni tanınmaz
hâle geldiyse kilit açılıyor.

Numaranın doğru okunup okunmadığı Teşhis günlüğünde görünür:
`4 şık %96 · KAYDEDİLDİ #133 · soru 2`.

## Geçiş karesinde yakalanan sahte sorular

Sorular birbirine solarak geçiyor. Kart henüz çizilmemişken OCR yarım kalmış
metni okuyor ve her karede başka türlü bozuyor — bu karelerden biri dört
"şık" bulabildiğinde arşive harfleri karışmış bir soru düşüyordu, hemen
ardından gerçek soru ayrıca kaydediliyordu. Yani her geçişte bir çöp kayıt.
Günlükte deseni şöyle görünüyordu:

```
14:15:35  4 şık %100 · KAYDEDİLDİ #879 · soru 14
14:15:36  karartma #879: (56,24,152) (72,40,168) ...   ← kart henüz koyu
14:15:37  4 şık %100 · tekrar #433 · soru 14           ← gerçek 14. soru
```

Artık aynı okuma **iki kez üst üste** görülmeden yeni kayıt açılmıyor.
Gerçek soru saniyede birkaç kez okunduğu için beklemenin maliyeti yok; bozuk
okuma ise kendini iki kez aynı biçimde tekrar edemiyor.

## Jokerler ve puan balonu

Ekranın altında üç joker var: 50/50, çift cevap ve soru değiştirme. Bunlar
şık bölgesinin alt ucuna giriyordu ve sayı süzgeci orada gevşetildiği için
şık sanılıyorlardı — arşivde gerçek şıklar yerine "50" ve "50" yazan kayıtlar
bundan. İki önlem alındı: şık bölgesinin varsayılan alt sınırı %97'den
**%90'a** çekildi (jokerler dışarıda kalıyor), ve joker yazıları ile puan
balonları metin olarak da eleniyor.

Puan balonunun ("+5") ayrı bir yolu daha var: OCR onu bazen **soruyla aynı
blokta** döndürüyor. O zaman parça bazlı hiçbir süzgeç göremiyor — arşivde
"…gezegen hangisidir? **+5**" ve "**KOMBO** Türkiye'nin…" diye duran kayıtlar
bundan. Bu yüzden soru metninin iki ucundan da puan balonu ve "KOMBO",
"Muhteşem!", "Çok Yaklaştın!" gibi banner'lar sökülüyor.

Sondaki kural işaretten önceki karaktere bakıyor: rakamsa dokunmuyor. Yoksa
"Sonuç kaçtır: 2 + 2" sorusunun sonundaki toplama da puan balonu sanılıp
kesiliyordu.

## Şıklar neden eksik yakalanıyordu?

Şıklar ekrana hep birlikte değil, teker teker geliyor. Saniyede tek kare
alınabildiği için üçü gelmişken bakılıp soru üç şıkla kaydediliyordu — üstelik
bir daha da düzelmiyordu.

İki değişiklik bunu kapattı: hızlı yakalama sayesinde çok daha sık bakılıyor,
ve *Dört şık tamamlanmadan kaydetme* ayarı (varsayılan açık) dördü de görünene
kadar bekletiyor. Eksik şıkla kaydedilmiş eski bir kayıt varsa, soru tekrar
karşına çıktığında eksik şıklar tamamlanıyor.

## Teşhis ekranı

İki bölümü var:

**Tarama geçmişi** — her taramanın tek satırlık özeti. Ekranda son **80**
satır görünür, arkada **4000** satıra kadar tutulur ve *Paylaş* hepsini dışarı
verir. Bir sorunu fark ettiğinde onu doğuran satırlar çoktan ekrandan kaymış
oluyor; asıl iş o yığında.

Bir tur oynayıp buraya bakınca hangi sorunun neden kaçtığı satır satır
görünür:

```
15:15:41  4 şık %96 · KAYDEDİLDİ #43
15:15:42  CEVAP #43 → B · bildin
15:15:47  düğüm:1 ocr:14 · RED: şıklar henüz tamamlanmadı (3/4)
15:15:48  4 şık %94 · KAYDEDİLDİ #44
15:16:02  atlandı · önplanda: com.android.systemui
```

Otomatik modda kendi dokunuşları da aynı listeye düşer:

```
15:15:40  4 şık %96 · KAYDEDİLDİ #43
15:15:41  OTOMATİK #43 → B · rastgele · bu oturumda 12 cevap
15:15:42  CEVAP #43 → D · bilemedin
15:16:05  OTOMATİK: "TEKRAR OYNA" → yeni tur (3. kez)
```

*Paylaş* düğmesi bu listeyi düz metin olarak dışarı verir. *Temizle* ile
sıfırlayıp temiz bir tur kaydedebilirsin.

**Son tarama** — en son karede ekranda tam olarak hangi metinlerin, hangi
dikey konumlarda görüldüğü. Bölge ayarlarını buna bakarak düzeltebilirsin.

## Sorular yakalanmıyorsa

**Teşhis** ekranı (ana ekran sağ üst, böcek simgesi) uygulamanın en son taramada
ekranda tam olarak ne gördüğünü ve soru çıkaramadıysa **neden** çıkaramadığını yazar.

1. Oyunda bir soru ekranında birkaç saniye bekle.
2. Uygulamaya dön → **Teşhis**.
3. "Sonuç" satırına bak:

| Teşhis ekranında yazan | Anlamı | Yapılacak |
|---|---|---|
| `soru cümlesine benzemiyor` | Ekran gerçek bir soru değil (lobi, skor tablosu) — doğru davranış | Bir şey yapma. Gerçek sorularda da çıkıyorsa Ayarlar → *Sadece soru cümlelerini kaydet*'i kapat |
| `şık bölgesinde 3'ten az metin` | Şıklar aranan bölgenin dışında | Listedeki dikey konumlara bak, Ayarlar → *Ekran bölgeleri* → *Şıklar — üst* değerini kaydır |
| `soru bölgesi boş` | Soru metni aranan bölgenin dışında | *Soru — üst / alt* değerlerini kaydır |
| `anlamlı metin 3'ten az` | Ne erişilebilirlik ne OCR bir şey okuyabildi | Ayarlar → *Metin okunamazsa OCR'a düş* açık olsun; Android 10 ve altındaysan ekran yakalama iznini ver |
| Ayrıştırma doğru ama kayıt yok | Güven eşiği yüksek | Ayarlar → *Kayıt eşiği*'ni düşür |

Bazı uygulamalar `FLAG_SECURE` ile ekran görüntüsü alınmasını tamamen engeller.
O durumda OCR yolu hiç çalışmaz; sadece erişilebilirlik metni işe yarar.

### Neden hem olay hem de düzenli yoklama var?

Android, ekran içeriği değiştiğinde erişilebilirlik servislerine haber verir —
ama bu sadece normal arayüz bileşenleri için geçerlidir. Oyun ekranları çoğu
zaman kendi çizim yüzeylerine çizilir ve **hiç olay üretmez**. Bu yüzden
uygulama, hedef oyun önplandayken olayları beklemeden ~1,4 saniyede bir de
kendiliğinden bakar. Oyundan çıktığında yoklama kendiliğinden durur.

Pil için: her yoklamada ekranın kaba bir imzası çıkarılır; görüntü bir önceki
turla neredeyse aynıysa OCR hiç çalıştırılmaz. Geri sayan sayaç gibi küçük
değişiklikler "aynı ekran" sayılır.

## "Mevcut paketle çakışıyor" — APK neden kurulmuyordu?

Android bir uygulamanın üstüne ancak **aynı anahtarla imzalanmış** bir APK'yı
kurdurur. Varsayılan debug anahtarı her makinede ayrı ayrı üretiliyor ve
GitHub Actions her çalışmada sıfırdan bir sanal makine açtığı için, her
derleme farklı imzalanıyordu: yeni APK "mevcut paketle çakıştığından
güncellenemedi" deyip kurulmuyor, uygulamayı silmek gerekiyor, arşiv de
onunla birlikte gidiyordu.

Artık depoda sabit bir imza anahtarı var (`keystore/`), debug ve release
derlemeleri bununla imzalanıyor. Bundan sonraki bütün APK'lar birbirinin
üstüne kurulur.

**Bu sürüme geçerken bir kereliğine yine silmen gerekiyor** — telefondaki
mevcut kurulum eski, rastgele bir anahtarla imzalı. Sıra şöyle:

1. Mevcut uygulamada **Ayarlar → Yedekleme → Dışa aktar**, JSON'u bir yere kaydet.
2. Uygulamayı kaldır.
3. Yeni APK'yı kur.
4. **Ayarlar → Yedekleme → İçe aktar**, kaydettiğin JSON'u seç.

Bir dahaki güncellemede bunların hiçbiri gerekmeyecek; APK doğrudan üstüne
kurulacak.

## Yedekleme: dışa ve içe aktarma

Ayarlar → **Yedekleme** altında iki düğme var.

* **Dışa aktar** — arşivi JSON olarak verir; paylaş menüsünden istediğin yere
  kaydedebilirsin.
* **İçe aktar** — kaydettiğin JSON'u geri yükler.

Uygulamayı silip yeniden kurman gerektiğinde (ya da telefon değiştirdiğinde)
sorularını böyle taşırsın. **Silmeden önce dışa aktarmayı unutma:** uygulama
kaldırılınca veritabanı da gider.

İçe aktarma **hiçbir şeyi silmez, hiçbir şeyi ezmez** — ekler ve tamamlar:

* Soru arşivde yoksa yeni kayıt olarak eklenir.
* Varsa yalnızca **eksikleri** tamamlanır: bilinmeyen doğru cevap, eksik şık,
  boş kategori. Cihazdaki kayıt neyi biliyorsa o kalır.
* Sayaçlarda ("kaç kez çıktı", "kaç denemede bildin") toplama değil **büyük
  olan** alınır. Bu yüzden aynı dosyayı iki kez içe aktarmanın zararı yoktur;
  ikinci seferde her şey "değişmedi" diye geçer.
* Şıklar karışık sırada olsa bile doğru cevap sıraya göre değil **metnine
  göre** yerleştirilir — yedekteki 2. şık cihazdaki 4. şık olabilir.

Sonunda kaç kayıt eklendiği, kaçının tamamlandığı ve kaçının değişmediği
ekranda yazar.

## Dışa aktarma

Arşiv ekranının sağ üstündeki paylaş simgesi:

* **CSV** — Excel / Google E-Tablolar (Türkçe karakterler için BOM'lu, `;` ayraçlı)
* **JSON** — başka bir programa aktarmak için
* **Anki (TSV)** — ön yüz soru + şıklar, arka yüz doğru cevap. Sadece doğru
  cevabı bilinen kayıtlar dışa aktarılır.

---

## Dosya haritası

```
app/src/main/java/com/emre/bilbakalim/arsiv/
├── MainActivity.kt              ekranlar arası gezinme
├── ArsivApp.kt                  bildirim kanalı
├── data/
│   ├── QuestionEntity.kt        veritabanı satırı
│   ├── QuestionDao.kt           sorgular
│   ├── ArsivDatabase.kt         Room veritabanı
│   ├── Prefs.kt                 ayarlar
│   └── Repo.kt                  kaydetme + tekrar eleme mantığı
├── capture/
│   ├── CaptureAccessibilityService.kt   motor: olay → tarama → kayıt
│   ├── NodeHarvester.kt         erişilebilirlik ağacından metin toplama
│   ├── OcrEngine.kt             ML Kit çevrimdışı metin tanıma
│   ├── QuestionParser.kt        metin yığınından soru + şık çıkarma
│   ├── AnswerColorDetector.kt   yeşile dönen şıkkı bulma
│   ├── AutoPlayer.kt            otomatik mod: şıkka ve "Tekrar Oyna"ya dokunma
│   ├── ProjectionService.kt     Android ≤10 için ekran yakalama yedeği
│   └── ProjectionPermissionActivity.kt
├── util/
│   ├── TurkishText.kt           Türkçe normalleştirme, parmak izi, şık eşleştirme
│   └── Exporters.kt             CSV / JSON / Anki
└── ui/                          Compose ekranları
```

---

## Doğru cevap nasıl bulunuyor?

Oyunun akışı şöyle:

1. Şıkka dokunursun → şıkkın rengi **anında** değişmeye başlar.
2. Karar açılır: doğru bildiysen şıkkın yeşil kalır; bilemediysen şıkkın
   **kırmızıya**, doğru olan şık **yeşile** döner.
3. Sonraki soruya geçilir.

### Renkler

Gerçek ekran görüntülerinden ölçülen değerler:

| Anlam | Renk | Ton | Doygunluk |
|---|---|---|---|
| Dokunduğun şık (karar yok) | `RGB(110,240,220)` | **171** | 0.54 |
| Doğru bildiğinde senin şıkkın | `RGB(120,240,170)` | **145** | 0.50 |
| Bilemediğinde açılan doğru cevap | `RGB(70,250,60)` | **117** | 0.76 |
| Senin yanlış seçimin | `RGB(250,90,90)` | **0** | 0.64 |

Kritik ayrım 171 ile 145 arasında. Dokunduğun anda şıkkın turkuaza dönüyor
ama bu henüz karar değil; karar açılınca ya yeşile (145) ya kırmızıya dönüyor.
Ton sınırı **158** ikisini ayırıyor:

* **85–158 → karar yeşili.** Görüldüğü an doğru cevap kesindir.
* **158–195 → dokunuş turkuazı.** Kaydedilmez, beklenir.

Bu ayrım olmadan ikisi tek bantta kalıyordu ve dokunuşu karardan ayırmak için
zaman beklemek gerekiyordu. Ama doğru cevapladığında oyun seni bekletmeden
sonraki soruya geçtiği için o bekleme cevabı kaçırıyordu — arşivde hiç
"bildin" kaydı çıkmamasının sebebi buydu.

### Karar kuralları

* **Kırmızı varsa** karar kesin açılmıştır: yeşil olan doğru cevap, sen
  bilememişsin. Anında kaydedilir.
* **Karar yeşili var, kırmızı yok:** doğru bilmişsin. Kırmızı senin şıkkında
  birkaç kare geç belirebileceği için 300 ms doğrulama payı bırakılır.
* **Sadece turkuaz:** karar bekleniyor, kaydedilmez. 900 ms boyunca yeşile de
  kırmızıya da dönmezse ölçüm dışı bir durum sayılıp doğru kabul edilir.

Kontrol, hızlı yakalama açıkken **50 ms'de bir** yapılır.

### Süre dolduğunda

Bu ekran tamamen farklı çalışıyor: oyun ekranı **karartıp** doğru cevabı
kendisi işaretliyor. Yeşil de kırmızı da kalmıyor, her şey mor tonlarına
dönüyor. Ölçülen değerler:

| Şık | Renk | Ton | Doygunluk | Parlaklık |
|---|---|---|---|---|
| Dokunulmamış şıklar | `RGB(80,80,140)` | 240 | 0.43 | 0.55 |
| Doğru cevap | `RGB(80,40,100)` | 280 | 0.60 | 0.39 |

Burada renk adına göre kural yazmak kırılgan olurdu. Onun yerine **yapıya**
bakılıyor: diğer üç şık birbirinin tıpatıp aynısıyken tek bir şık ayrışıyorsa,
doğru cevap odur. Bu kural oyunun renkleri değişse bile ayakta kalır ve
yalnızca ekran kararmışken — yani şıklar beyaz değilken — devreye girer.

Bu kuralın iki freni var, çünkü yanlış tetiklendiğinde arşive **yanlış bir
doğru cevap** yazıyor ve otomatik mod sonraki turlarda o yanlış şıkka basmaya
başlıyor — sessiz ve kalıcı bir hata:

* **Ekran gerçekten karartılmış olmalı.** Kural eskiden rengin ne kadar koyu
  olduğuna hiç bakmıyordu; şıklar bembeyazken bile "biri ötekilerden farklı"
  deyip süre dolmuş sayabiliyordu. Şıkların beliriş animasyonu tam da bu
  deseni üretiyor.
* **Soru ekranda en az beş saniyedir duruyor olmalı.** Sayaç bir dakikanın
  üstünde olduğu için bu eşik gerçek bir süre dolmasını hiç kaçırmaz, ama
  soru belirir belirmez gelen animasyon karelerini eler.

Ayrışan şıkkın ötekilerden **daha koyu ya da daha doygun** olması da şart.
Yön kontrolü bilerek tek taraflı: ekran geçişlerinde şıklar sırayla sönüyor
ve bir kare boyunca "üçü koyu, biri parlak" deseni oluşuyor; o kareye cevap
yazmak arşive çöp yazmak olurdu.

Doğru cevap yine kaydedilir, kaynak olarak `süre doldu` yazar, ama **deneme
sayılmaz**. Soru 3 kez çıkıp birinde süreye takıldıysan başarı oranın diğer
2 tur üzerinden hesaplanır: *3 kez çıktı · 2 denemede %50*.

## Soru başına istatistik

Her kayıt üç sayı tutar: kaç kez karşına çıktı, kaçında cevabın gözlendi,
kaçını doğru bildin. Liste ekranında şu şekilde görünür:

> **4 kez çıktı · 3 denemede %67**

Detay ekranında ayrıntısı var, CSV ve JSON çıktılarına da sütun olarak giriyor.
Böylece sürekli yanıldığın soruları `Cevabı eksik` yerine başarı oranına
bakarak ayıklayabilirsin.

## Cevabı ilk seferde kaçırdıysa ne oluyor?

Hiçbir şey kaybolmuyor: soru bir dahaki çıkışında cevabı yakalanınca **aynı
satır** doldurulur, kayıt artık "cevabı eksik" görünmez. Bunun çalışması için
sorunun yeniden bulunabilmesi gerekiyor ve bulanık eşleştirme eskiden yalnızca
**son 300 kayda** bakıyordu — arşiv birkaç yüz soruyu geçince eski satır
pencerenin dışında kalıyor, OCR bir harfi farklı okuduysa parmak izi de
tutmuyordu. O zaman ikinci bir satır açılıyor, cevap ona yazılıyor, eskisi
sonsuza kadar cevapsız kalıyordu.

Artık kontrole **cevabı eksik olan kayıtlar da** dahil ediliyor, ne kadar eski
olurlarsa olsunlar. Böylece cevabı kaçmış bir soru yeniden çıktığında yeni bir
satır açılmıyor; var olan satır bulunup dolduruluyor.

## Aynı soru neden iki kez kaydedilmiyor?

Önce metin temizlenir: soru numarası ("17.") ve "Süre Bitti", "MUHTEŞEM!" gibi
uyarılar soru kartının üstünde durduğu için OCR bunları bazen soruyla aynı
blokta döndürüyor ve metne yapışıyorlar. Bunlar baştan sökülür — yoksa aynı
soru bir kez temiz bir kez fazlalıkla okunup iki ayrı kayıt oluyordu.

Ardından dört kademeli eşleştirme:

1. **Parmak izi** — soru ve şıklar Türkçe'ye duyarlı biçimde sadeleştirilip
   SHA-256'sı alınır. Şıklar sıralanarak eklenir, böylece şık sırası değişse
   bile aynı kayda düşer.
2. **Bulanık eşleşme** — OCR bir iki harfi yanlış okuduğunda parmak izi tutmaz;
   son 300 kayıtla Levenshtein benzerliğine bakılır (%92 eşik).
3. **Kapsama** — soru yarım yakalandıysa ("…kaç" ile "…kaç adettir?") biri
   diğerini içeriyorsa aynı sayılır.
4. **Şık eşleşmesi** — dört şıkkın tamamı birebir aynıysa neredeyse kesinlikle
   aynı sorudur. Metnin başına fazlalık yapışıp üstüne bir de OCR harf hatası
   geldiğinde 2 ve 3 tutmuyordu; bu kural ikisini birden kurtarıyor.

Bütün bu kuralların üstünde bir **olumsuzluk kontrolü** var: "Hangisi ...
özelliklerinden**dir**?" ile "... özelliklerinden **değildir**?" arasındaki
karakter benzerliği %91'e çıkıyor ve şıkları da aynı oluyor — ama bunlar zıt
sorular. Metinlerden biri olumsuzluk taşıyıp diğeri taşımıyorsa hiçbir
benzerlik ölçüsü onları birleştiremez.

Birleşirken hangi metnin kalacağına da bakılır: arayüz uyarısı içermeyen kayıt
kazanır, ikisi de temizse daha eksiksiz olan alınır.

## "Kaç kez çıktı" nasıl sayılıyor?

Bir soru ekranda dururken saniyede birkaç kez taranıyor ve her taramada OCR
metni birkaç harf farklı okuyabiliyor. Bu yüzden tek bir soru için arka arkaya
birkaç kayıt denemesi oluyor — ama bunların hepsi **tek bir karşılaşmadır**.

Sayaç bu yüzden kaydetme mantığından ayrıldı: yalnızca gerçekten başka bir
soruya geçildiğinde artıyor. Önceki sürümde her tarama sayıldığı için ilk kez
gördüğün soru "3 kez çıktı" görünüyordu.

## Gereksinimler

* Android 8.0 (API 26) ve üzeri
* Erişilebilirlik ekran görüntüsü Android 11+ üzerinde izin penceresi olmadan
  çalışır. Android 10 ve altında OCR yedeği için ayrı bir ekran yakalama izni
  gerekir (Ayarlar ekranından).
