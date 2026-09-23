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
* Şıkların **kaç tane olduğunu ve nerede durduğunu** yazıdan değil, ekrandaki
  parlak hapların kendisinden ölçer; her hap ayrı ayrı okunur. Şıkları sayı
  olan sorular ("1 / 3 / 4 / 2") ancak böyle okunabiliyor — aşağıya bak.
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

Eşleştirme Türkçe'ye duyarlıdır (büyük/küçük harf, noktalama ve "A)" gibi
şık işaretleri yok sayılır) ve OCR'ın bir iki harfi yanlış okumasına
dayanıklıdır. ı/ğ/ş/ö/ç/ü **önce korunur**, ancak birebir eşleşme
bulunamazsa yok sayılır: "Öz / Toz / Oz / Töz" sorusunda cevap "Töz" iken
eşleştirme harfleri baştan katladığı (ö→o) ve ilk eşleşeni aldığı için bot
"Toz"a basıyor, arşive de "Toz" yazılıyordu. Artık harfleri koruyan
anahtarla aranıyor; iki şık yine de ayırt edilemiyorsa tahmin yapılmıyor.
Arşive böyle yanlış yazılmış bir cevap, bot ona basıp oyun kırmızı
gösterdiğinde kendiliğinden düzelir. Hiçbir şık yeterince benzemiyorsa eşleştirme başarısız sayılır:
dokunurken rastgele seçime düşülür, kaydederken satır olduğu gibi bırakılır.
Yanlış şıkka basmak ya da arşive yanlış cevap yazmaktansa bilmediğini söylemek
yeğdir.

### Kart çizilmeden dokunma

Soru kartı ekrana solarak geliyor ve bu sırada şıklar yerlerine kayıyor.
Metin daha kart kararmışken okunabildiği için dokunuş **eski konumlara**
gidiyor ve başka bir şıkka basılıyordu. Günlükteki iz:

```
15:20:41  4 şık %100 · tekrar #441 · soru 15
15:20:43  OTOMATİK #441 → C · bilinen cevap
15:20:43  karartma #441: (56,24,152) (72,40,168) ...   ← kart hâlâ koyu
15:20:44  karartma #441: (248,248,248) x4              ← ancak şimdi çizildi
15:20:47  renk #441: turkuaz · · ·                     ← A turkuaz, oysa C'ye basılmıştı
```

Bunun iki zararı birden var: bilinen doğru cevap ıskalanıyor, **ve** oyunun
o yanlış şıkka verdiği tepki doğru cevap diye arşive yazılıyor. Ekran
görüntüsünde "Dünya, Güneş sistemindeki kaçıncı gezegendir? → 2" gibi
kayıtlar bundan.

Artık bekleme süresi sorunun okunduğu andan değil, **şık kutularının
çizildiği andan** sayılıyor. Kutuların çizilip çizilmediği zaten her karede
ölçülen renklerden anlaşılıyor: oturmuş şıklar bembeyaz, geçiş kareleri koyu
mor. Kart dört saniyede oturmazsa yine de dokunuluyor, yoksa hiç dokunmamış
oluruz.

### Şık metni ile şık kutusu hep birlikte

Her şıkkın iki yüzü var: **metni** (ne yazdığı) ve **kutusu** (nerede
durduğu). Bot kutuya dokunuyor, cevap ise metinle kaydediliyor. İkisi
birbirinden ayrılırsa bot bir şıkka basıp oyun başka şıkta tepki veriyor ve
arşive yanlış cevap yazılıyor:

```
16:58:59  OTOMATİK #951 → C · rastgele    ← bota göre C
16:58:59  renk #951: · · · YESIL          ← tepki D'de
16:59:00  CEVAP #951 → D · bildin         ← D doğru diye kaydedildi
```

İki yerden ayrılabiliyorlardı, ikisi de kapatıldı:

* **Ayrıştırıcıda:** şık işareti soyulduktan sonra boş kalan metinler
  listeden atılıyor ama kutuları kalıyordu. Artık ikisi birlikte eleniyor.
* **Tazelemede:** kutular yeni okumadan tazelenirken metinler ilk okumadan
  kalıyordu. Parmak izi şıkları **sıralayarak** hesaplandığı için sırası
  değişmiş bir okuma da aynı anahtarı üretiyor — yani tazeleme kutuları
  sessizce permüte edebiliyordu. Artık kutular yalnızca şık metinleri
  **aynı sırada** çıktığında tazeleniyor.

Son bir emniyet olarak, kutu sayısı ile metin sayısı tutmuyorsa hiç cevap
yazılmıyor: hangi rengin hangi şıkka ait olduğunu bilmiyoruz demektir.

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
* Kapatma yazılarında yalnızca birebir eşleşme kabul edilir. Parça eşleşmesi
  "Tümünü kapat" düğmesini de yakalıyordu: bot son kullanılanlar ekranında
  ona basıp oyunu tamamen kapatmıştı.
* "Atla", "Devam", "Kapat" gibi soru ekranında da bulunabilen yazılara ancak
  **7 saniyedir** hiç soru görülmediyse dokunur.
* Aynı düğmeye üst üste 4 kez basıp hiçbir şey değişmezse 30 saniye ara verir —
  sonsuz döngüye girmez.
* Hedef oyun önplandan çıkarsa hiçbir şeye dokunmaz. Pencere adı her zaman
  okunamadığı için (bu cihazda geçici olarak boş dönüyor) dokunmadan hemen
  önce karenin kendisine de bakılır: dokunulacak yerlerde şık hapları
  görünmüyorsa dokunulmaz.
* Aynı soruya iki kez basmaz; dokunuş üç kez başarısız olursa o soruyu geçer
  (süre dolunca oyun doğru cevabı zaten açıyor, uygulama da onu okuyor).
  Yeniden deneme ancak soru dokunuştan sonra ekranda **yeniden okunduysa**
  yapılır; yoksa ekranda hâlâ o soru olduğunu bilmiyoruz demektir.

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
satır görünür, arkada **10 000** satıra kadar tutulur ve *Paylaş* hepsini dışarı
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

*Paylaş* düğmesi bu listeyi bir `.txt` dosyası olarak dışarı verir. *Temizle*
ile sıfırlayıp temiz bir tur kaydedebilirsin.

Paylaş eskiden metni paylaşım niyetinin içine koyuyordu. Niyet süreçler
arasında ~1 MB'lık bir tampondan geçiyor ve metin orada karakter başına iki
bayt tutuyor: günlük birkaç bin satıra ulaşınca Android paylaşımı reddediyor,
hata da sessizce yutulduğu için düğme hiçbir şey yapmıyormuş gibi
görünüyordu. *Temizle*'den sonra yeniden çalışmasının sebebi günlüğün
küçülmesiydi. Artık günlük dosyaya yazılıyor ve yalnızca dosyanın adresi
paylaşılıyor; paylaşım yine de açılamazsa ekranda uyarı çıkıyor.

### İz satırları: gecikme nereden geliyor, kod nerede kalıyor

Günlükteki `İZ` ile başlayan satırlar okunmak için değil, arıza aramak için.
Zaman damgası milisaniyeli. Satırlar olmadan "bot neden bekledi" sorusu hep
satır aralarındaki boşluklardan tahmin ediliyordu. 20 saniyelik takılmanın
sebebi, her taramada sessizce geri çeviren bir kapı, ancak ekran görüntüsü
ölçülünce bulunabilmişti.

| Satır | Ne söyler |
|---|---|
| `İZ 4812ms ×14 [kart_kapisi/-×12 durgun/-×2] ort=180 max=420(kart_kapisi/-: ocr=160 kutu=20 …)` | Son satırdan bu yana biten taramalar, **nerede bittiklerine** göre sayılmış; en yavaşının aşama aşama süresi. Her olaydan önce ve en geç 5 saniyede bir. |
| `İZ YAVAŞ 1240ms …` | 900 ms'yi aşan tek tarama, aşama aşama |
| `İZ BOŞLUK 3400ms önceki=…` | İki tarama arasında 2,5 saniyeden uzun ara: tarama hiç çalışmamış |
| `İZ TAKILDI 4000ms aşama=ocr (3890ms)` | Süren bir tarama bir aşamada kaldı: **kodun beklediği yer**. Ayrı bir bekçi yazıyor, tarama dönmese de düşer. |
| `İZ YOKLAMA TAKILDI 5000ms aşama=onplan_sorgu` | Taramayı tetikleyen döngü takıldı (ana iş parçacığı meşgul olabilir) |
| `İZ SORU #id karar→okuma=… okuma→kayıt=… kapı=… teyit=… yol=kutu\|metin kutu=4` | Yeni sorunun zaman çizelgesi: önceki karardan ilk okunabildiği ana, oradan kayda; kaç kez kart kapısında ve teyitte geri çevrildiği; şıkların ekrandan ölçülen kutularla mı, metin yoluyla mı bulunduğu |
| `OTOMATİK … · iz[db=12 kare=31 jest=96]` | Dokunuşun kendi içindeki süreler: arşiv sorgusu, karede şık kontrolü, jestin tamamlanması |
| `CEVAP … · zaman[kart=+0 dokunuş=+515 tepki=+640 karar=+1180 …]` | Sorunun kaydından itibaren olaylar |
| `İZ KARAR_TURU #id tur=23 1180ms sonuç=karar\|sessiz\|soru_degisti\|bitti` | Dokunuştan sonraki hızlı renk turunun nasıl bittiği |
| `HATA tarama/iş/yoklama: … @ Dosya.kt:123` | Eskiden yalnızca logcat'e düşen hatalar |

Tarama çıkışları: `burst` (karar turu sürüyor), `onplan` (oyun önde değil),
`durgun` (kare değişmedi, atlandı), `veri_yok` (kare ya da OCR sırası yok),
`red` (ayrıştırılamadı), `guven`, `ayni_soru` (bekleyen sorunun yeniden
okunması), `kart_kapisi` (kart çizilmedi sayıldı), `teyit` (ikinci okuma
bekleniyor), `yeni` / `tekrar` / `yeniden` (kayıt). Eğik çizgiden sonrası
otomatik modun kararı: `yok`, `gecikme`, `kart_bekle`, `basiliyor`,
`tekrar_bekle`, `tekrar_engel`, `is_suruyor`, `dokunus_bitti`, `elle`
(soruyu sen cevapladın).

Olay satırları arasında ayrıca `ELLE #…` (şıkkı bot değil sen seçtin ya da
oyun botunkinden başka bir şıkkı aldı) ve `SORU KAPANDI #…` (bekleyen soru
kararı görülmeden ekrandan gitti) var.

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
│   ├── OptionBoxFinder.kt       şık kutularını ekrandan piksel olarak bulma
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

### Otomatik modda "dokunuş" kanıt sayılmaz

Kuralların arasında bir geri düşüş var: dokunduğun şık karar açılmadan
öylece kaldıysa, doğru bildiğin varsayılıp o şık doğru cevap olarak
kaydediliyor. Elle oynayan biri için makul — insan bildiğini seçer.

Rastgele dokunan bir bot için **dörtte üç ihtimalle yanlış**. Üstelik hata
kendini besliyordu: yanlış cevap arşive girince "bilinen cevabı kullan" onu
her turda yeniden basıyor, oyun her seferinde yanlış diyor, kayıt yine
düzelmiyordu. Bu yüzden geri düşüş artık yalnızca elle dokunulan sorularda
işliyor. Cevapsız kalmak, yanlış cevaptan iyidir.

### "Neden bilinen cevaba basmadı?"

Otomatik mod rastgele seçtiğinde bunun üç ayrı sebebi olabilir ve günlük
artık hangisi olduğunu yazıyor:

| Günlük | Anlamı |
|---|---|
| `bilinen cevap` | Arşivdeki cevap ekranda bulundu, ona basıldı |
| `rastgele (cevabı bilinmiyor)` | Soru yeni ya da cevabı henüz yakalanmamış — normal |
| `rastgele (eşleşmedi)` | **Arıza:** arşivde cevap var ama ekrandaki şıkların hiçbirine benzemiyor |

Sonuncusu ayrıca kendi satırını da yazar:
`UYUŞMAZLIK #123: arşivdeki cevap "Ayı" ekranda bulunamadı → rastgele seçiliyor`

Ya OCR şıkları bozuk okumuştur ya da kayıttaki metin ekrandakinden gerçekten
farklıdır. Bu ayrım olmadan ikisi de sadece "rastgele" görünüyordu ve
"madem cevabı biliyordu, neden başkasına bastı?" sorusunun izi kalmıyordu.

### Hangi gözlem hangisinin üstüne yazar?

Doğru cevabı öğrendiğimiz gözlemin bir de sağlamlık derecesi var:

| Gözlem | Güç | Ne gördük |
|---|---|---|
| `renk (kesin)` | 3 | Kırmızı da göründü: yeşil olan kesinlikle doğru |
| `renk` | 2 | Yalnızca karar yeşili |
| `süre doldu` | 2 | Ekran karardı, ayrışan şık işaretlendi |
| `dokunuş` | 1 | Dokunulan şık öylece kaldı |
| `içe aktarım` | 1 | Yedekten geldi |

Zayıf bir gözlem, daha sağlamıyla yazılmış cevabın üstüne yazamıyor. Eşit
güçtekiler yazabiliyor — bozuk eski kayıtların yeni karşılaşmalarda
kendiliğinden düzelmesi buna bağlı.

Arşivdeki cevaba basıldığı hâlde oyun yanlış diyorsa, kayıt bozuk demektir
ve Teşhis günlüğüne düşer:
`ÇELİŞKİ #507: arşiv B diyordu, doğrusu D`

Bu derece dışa aktarımda da var: JSON'da `cevapKaynagi`, CSV'de
`cevap_kaynagi` sütunu. (Yanındaki `kaynak` alanıyla karıştırma: o, sorunun
ekrandan hangi yolla **okunduğunu** söyler — OCR mı, erişilebilirlik mi.
Cevabın hangi kanıtla yazıldığını yalnızca `cevapKaynagi` söyler.) Zayıf
kanıtla yazılmış cevapları ayıklamak istersen dışa aktarıp bu sütuna göre
süzebilirsin.

### Karar kuralları

* **Kırmızı varsa** karar kesin açılmıştır: yeşil olan doğru cevap, sen
  bilememişsin. Anında kaydedilir.
* **Karar yeşili var, kırmızı yok:** doğru bilmişsin. Kırmızı senin şıkkında
  birkaç kare geç belirebileceği için 300 ms doğrulama payı bırakılır.
* **Sadece turkuaz:** karar bekleniyor, kaydedilmez. 900 ms boyunca yeşile de
  kırmızıya da dönmezse ölçüm dışı bir durum sayılıp doğru kabul edilir.

Kontrol, hızlı yakalama açıkken **100 ms'de bir** (saniyede 10 kare) yapılır.
Eskiden 50 ms'deydi. Karar yeşili ~1 saniye ekranda kalıyor ve onay
süreleri kare sayısıyla değil zamanla ölçülüyor; 10 kare kararı kaçırmadan
bu turun işlemci yükünü yarıya indiriyor.

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

## "Arşivde olan soruya bilmiyorum diyor" — aslında okuyamıyordu

Nadiren, arşivde kayıtlı bir soruda uyarı sesi çalıp bot hiçbir şıkka
basmadan bekliyordu. Günlükteki iz:

```
OKUNAMADI: ekranda soru var ama şıklar çıkarılamıyor ·
  şıklar henüz tamamlanmadı (3/4) ·
  «Perseverance»@%64 «Curiosity»@%72 «Ingenuity»@%81
düğüm:1 ocr:18 kutu:0 · RED: şıklar henüz tamamlanmadı (3/4)
```

Dördüncü şık **"Uluslararası Uzay istasyonu"** idi — ve doğru cevap da
oydu. Sebep: o şık kendi hapında **iki satıra sarıyor.** İki ölçü de
"bütün şıklar aynı yükseklikte" varsayıyordu:

* `QuestionParser.trimOutliers` metin bloklarını yüksekliğe göre eliyordu
  (ortancanın 0,60–1,70 katı). İki satırlık blok ~2 kat yüksek olduğu için
  listeden düşüyor, geriye üç şık kalıyordu — günlükteki "3/4".
* `OptionBoxFinder` hapları eşit **yükseklikte** arıyordu. Dördüncü hap
  yüksek olunca dizi tutarsız sayılıyor ve hiç kutu bulunamıyordu —
  günlükteki "kutu:0".

Yani iki bağımsız yol, aynı yanlış varsayım yüzünden aynı anda
tıkanıyordu. Yakın plan OCR'ın 2x ve 3x denemeleri de boşunaydı: metin
zaten okunuyordu, **süzgeç atıyordu.**

Düzeltme, hapın değişmeyen özelliklerine geçmek oldu:

* **Genişlik** — haplar her zaman aynı genişlikte (ölçülen ekranda 738
  piksel); soru kartı belirgin biçimde daha geniş (900). Kartı eleyen ölçü
  artık bu.
* **Haplar arası boşluk** — iki hap arasındaki mor şerit her zaman aynı
  (57 piksel), hapın yüksekliğinden bağımsız. Aralık ölçümü bilerek
  üstten üste değil, alttan üste yapılıyor.
* Yükseklik yalnızca kaba bir akıl sağlığı sınırı; iki satırlık hap
  içeride kalıyor.
* `trimOutliers` üst sınırı 2,60'a çıktı: iki satırlık şık kalıyor, dört
  şıkkın yapıştığı blok (~4 kat) hâlâ düşüyor.

Ayrıca tarama bandı artık ayarlardaki şık bölgesine bağlı değil. Soru üç
satır olunca şıklar aşağı kayıyor ve dördüncü hap `optionsBottom` (%90)
sınırının altında kalabiliyordu; geometri zaten kendini doğruladığı için
bant cömert tutuluyor.

### Uyarı sesi artık ikisini ayırıyor

"Cevabı bilmiyorum" ile "soruyu okuyamıyorum" aynı sesi çalıyordu; bu
yüzden okuma arızası, arşiv eksiği gibi görünüyordu.

* **Tek ötüş** — soru okundu, cevabı arşivde yok.
* **Çift ötüş** — ekranda soru var ama şıklar okunamıyor. Soru arşivde
  kayıtlı bile olabilir.

Kutu ölçümü kutu bulamadığında sebebini de günlüğe yazıyor artık
("hap rengi satır yok", "5 aday kutu var, eşit aralıklı dörtlü yok · …").
"kutu:0" tek başına, eski "rozetten başka şık yok" mesajı kadar sessizdi.

## Soru ekranda duruyor, bot hiç dokunmuyor — ve günlükte tek satır yok

"Hangisi Orta Çağ felsefesinin temel konularından değildir?" sorusunda bot
çoğu zaman 20-30 saniye bekliyor, sonra birden doğru şıkka basıyordu.
"Felsefe nerede ortaya çıkmıştır?" sorusunda da aynısı (aynı belirtiyi
veren "Aydınlanma döneminin diğer adı nedir?" bu günlükte yok). Günlükte
o aralıkta hiçbir satır yoktu:

```
14:57:35  düğüm:1 ocr:15 kutu:0 · RED: şıklar henüz tamamlanmadı (3/4) · …
                                                  ← 20 saniye boşluk
14:57:55  4 şık %100 · tekrar #1934 · soru 41
14:57:55  OTOMATİK #1934 → D «Antik Yunan» · bilinen cevap · 510 ms
```

Sebep, ekran görüntüsünde ölçülünce ortaya çıktı ve üç katmanlıydı.

**1. Kelime şıklarında şık kutusu hiç bulunamıyordu.** `OptionBoxFinder`
hapları satır satır tarıyor; bir satırın hap sayılması için içinin %82'si
hap renginde olmalı. Yazının geçtiği satırlarda lacivert harfler hap rengi
sayılmıyor ve bu oran %64-80'e iniyor. Böylece **her hap yazının iki
yanında ikiye bölünüyordu**; iki yarım da (63 piksel) kutu alt sınırının
(84) altında kaldığı için kelime şıklarında hiç kutu çıkmıyordu (günlükteki
`kutu:0`). Rakam şıklarında bu olmuyordu, çünkü tek bir rakam satırın ancak
küçük bir kısmını kaplıyor; kutu ölçümü bu yüzden yalnızca onlarda doğrulanmıştı.

Artık hapın ortasından geçen yazı satırı hapı bölmüyor. Ayırt edici işaret
kenarlar: hapın iki ucu beyaz kaldığı için yazı satırının en sol ve en sağ
hap pikseli, üstteki hap satırınınkiyle aynı yerde. Haplar arasındaki mor
şeritte hap pikseli hiç yok, joker düğmeleri de hapa bitişik değil; yani bu
kural iki ayrı şeyi birleştiremiyor. Gerçek ekran görüntüsünde sonuç:
dört hap, 738x150 piksel, eşit aralıklı.

**2. Kutu bulunamayınca "kart çizildi mi" ölçüsü metne bakıyordu.** Eski
metin yolunda şık kutusu OCR metninin sınırları oluyor. Kart kontrolü o
kutunun **baskın rengine** bakıyordu. "Demokratikleşme" iri ve sık yazılmış
bir kelime: kutusundaki 128 örnekten 43'ü beyaz, 42'si lacivert. OCR kutusu
birkaç piksel oynayınca baskın renk lacivert çıkıyor, kart "çizilmedi"
sayılıyor ve soru geri çevriliyordu. Ekran kıpırdamadığı sürece her okuma
aynı sonucu verdiği için takılma kalıcıydı. Ekran görüntüsü alınınca botun
birden basması da büyük ihtimalle bundan: kare değişince OCR kutusu birkaç
piksel oynuyor ve kapı açılıyor.
"Felsefe nerede…" sorusunda aynı rol «Konstantiniyye»nin.

Artık baskın renk koyu çıksa bile örneklerin en az dörtte biri parlaksa
kart çizilmiş sayılıyor. Ölçülen değerler: hapın tamamında %86, en sık
yazılmış şıkkın metin kutusunda %38-52, geçiş karelerinde ~%0.

**3. Bu kapı sessizdi.** Soru her taramada okunduğu için "bekleyen soru yok"
satırı da düşmüyordu; takılma günlükte tamamen görünmezdi. Şimdi:

* Aynı okuma bir saniyeden uzun kapıda kalırsa bir kez yazılıyor:
  `kart oturmadı sayılıyor, dokunulmuyor · «…» · renk (…) · parlak %86 %86 %86 %38`
* Ekran bir saniyedir hiç kıpırdamıyorsa kart, ölçü ne derse desin oturmuş
  sayılıyor. Kapının koruduğu şey solarak gelen karttı; kıpırdamayan bir
  kare solma animasyonunun ortası olamaz. Bu da günlüğe düşüyor:
  `kart ölçüsü tutmadı ama ekran … ms'dir kıpırdamıyor, oturmuş sayıldı`

### Okumadığı soruya "yeniden deneme" diye basmak

Aynı günlüğün başında şu da vardı:

```
14:54:00  OTOMATİK #2595 → C «Aşinalık» · bilinen cevap · 936 ms
14:54:07  OTOMATİK #2595 → C «Aşinalık» · bilinen cevap · 2. deneme · 7415 ms
14:54:12  OTOMATİK #2595 → C «Aşinalık» · bilinen cevap · 3. deneme · 13065 ms
```

Karar yalnızca bir karede görünüp kaçtığı için 5. soru "bekliyor" hâlde
kaldı. Ekrana gelen 6. soru geçiş sırasında okunamadı. Bekleyen soru varken
kıpırdamayan kare hiç yeniden okunmadığından 6. soru bir daha okunmadı.
Bot da "dokunuş yutuldu" sanıp 5. sorunun konumlarına bastı; yani okumadığı
6. ve 7. soruları rastgele cevapladı. İki düzeltme:

* Kıpırdamayan ekran, bekleyen soru varken de 1,5 saniyede bir yeniden
  okunuyor. Yeni soru böylece bulunuyor.
* Yeniden deneme ancak soru dokunuştan sonra yeniden okunduysa yapılıyor.
  Okunamadıysa günlüğe `yeniden denenmiyor: soru dokunuştan sonra okunmadı`
  düşüyor.

## "1. Dönem" şıkları arşive "Dönem" diye yazılıyordu

"Frankfurt Okulu'nun … hangi döneme denk gelmektedir?" sorusunun şıkları
*1. Dönem / 4. Dönem / 2. Dönem / 3. Dönem*. Arşive dört kez **Dönem** diye
yazılmıştı. Şık işaretlerini ("A)", "B.", "1)") soyan kural rakamdan sonraki
noktayı da işaret sayıyordu. Oysa Türkçede sıra sayısı böyle yazılır
(*1. Dönem*, *2. Mahmut*, *3. Selim*). Dört şık birbirinden ayırt
edilemediği için bot bu soruda hep rastgele basıyor, doğru cevap da hiçbir
zaman kaydedilemiyordu.

Artık rakam yalnızca ")" ya da "]" ile kapanıyorsa işaret sayılıyor.

Böyle bozulmuş kayıtlar kendiliğinden düzeliyor. Soru bir dahaki çıkışında
okunduğunda, kayıttaki şıklar ekrandakilerin eski kuraldan geçmiş hâliyle
birebir aynıysa şıklar ekrandan yeniden yazılıyor. Kayıttaki cevap yeni
listede tek bir şıkka denk geliyorsa korunuyor. *Dönem* gibi belirsizse
boşaltılıyor ve bir sonraki renk okuması onu yeniden öğretiyor.

## Otomatik modda sen de basarsan

Otomatik mod açıkken bir soruyu kendin cevaplayabilirsin (cevabı
bilinmeyen soruda "rastgele seç" ya da "kararı bana bırak" fark etmez).
Bu durumda iki şey ters gidiyordu:

* **Bot senin cevabını kendi şıkkı sanıyordu.** Sen A'ya erken bastığında
  bot yine de kendi seçtiği C'ye basıyor, oyun C'yi yok sayıyor, ama
  Hatalar'a "bastığımız C" yazılıyordu.
* **Bot önceki sorunun şıkkına yeni soruda basıyordu.** Sen erken
  cevaplayınca oyun sonraki soruya geçiyor. Bot, önceki soru için seçtiği
  şıkka yeni soruda basıyordu; yeni sorunun hapları aynı yerde durduğu için
  "şıklar ekranda mı" kontrolü bunu göremiyordu.

Artık:

* Bot dokunmadan önce karede bir şıkkın zaten seçilmiş olup olmadığına
  bakıyor (turkuaz, yeşil, kırmızı ya da basılı sarı). Seçilmişse o soru
  senin; bot dokunmuyor. Günlükte `ELLE #…` satırı çıkıyor.
* Bot dokunmadan önce ekranın, soruyu en son okuduğu andaki hâline hâlâ
  benzeyip benzemediğine bakıyor. Ekran değiştiyse soru yeniden okunmadan
  dokunmuyor (`ekran soru okunduğundan beri değişti`).
* Dört şık da sorular arası geçişin koyu morunu aldığında bekleyen soru
  bırakılıyor (`SORU KAPANDI #…`). Eskiden kararı kaçırılan soru bekler
  hâlde kalıyor ve **sonraki sorunun renkleri onun kararı sanılıyordu**:
  günlükte doğrusu 45 olan bir soru için "A yeşil, C kırmızı" okunmuştu.
  Süre dolunca gelen karartma bu ölçüye girmiyor; "süre doldu" kararı
  etkilenmiyor.

## Kasma ve bilinmeyen soruda geç gelen ses

* **Tekrar denetimi önbellekte.** Arşivde parmak izi olmayan her okuma
  arşivin tamamıyla karşılaştırılıyor. Eskiden bunun için her seferinde 4000
  satır veritabanından okunuyor, her satırın metni yeniden sadeleştiriliyor
  ve kelimelerine ayrılıyordu. Bu iş masaüstünde bile soru başına 150-550 ms
  sürüyordu; telefonda birkaç katı. Cevabı bilinmeyen sorudaki uyarı sesi de
  bu yüzden geç geliyordu. Artık satırlar bir kez hesaplanıp bellekte
  tutuluyor. Uzunluğu çok farklı satırlar benzerlik hesabına hiç girmiyor.
  Karar kuralları aynı kaldı.
* **Düzenli ifadeler bir kez derleniyor.** Metin sadeleştirme her çağrıda
  aynı düzenli ifadeyi yeniden derliyordu.
* **Teşhis dökümü diske yazılmıyor.** "Son tarama" dökümü her taramada
  ayarlara yazılıyordu: diske bir yazım, üstüne bütün ayar dinleyicilerinin
  (arayüz, servis) yeniden tetiklenmesi. Artık bellekte.
* **Teyit beklenirken ekran bekletilmiyor.** Yeni bir soru kaydedilmeden
  önce iki kez aynı okunmalı. Ekran kıpırdamıyorsa ikinci okuma 1,5 saniye
  bekliyordu; artık hemen yapılıyor.
* **Yeni kayıtta ses hemen.** Yeni açılan kaydın cevabı olamayacağı için
  uyarı sesi veritabanı sorgusunu beklemiyor.
* **Karar turu saniyede 10 kare** (yukarıya bak).

---

## Bu oturumun hataları

Ana ekranda **Hatalar** bölümü, ondan açılan listede de bu oturumda
bildiremediğimiz sorular var. Arşiv ekranı "doğru cevap neydi" sorusunu
zaten yanıtlıyor; burada yanıtlanan başka bir soru: **biz neye bastık.**
Bot rastgele mi seçti, arşivdeki cevap yanlış mıydı, yoksa süre mi doldu —
ikisi yan yana görülmeden anlaşılmıyor.

Her satırda dokunuşu kimin yaptığı (otomatik / elle / süre doldu), soru
metni, bastığımız şık ve doğrusu var; satıra dokununca sorunun arşivdeki
kaydı açılıyor.

"Bastığımız" şık, **oyunun kabul ettiği** şık: kırmızıya ya da karar öncesi
turkuaza dönen. Eskiden botun niyeti yazılıyordu; sen A'ya erken basıp doğru
bilsen bile bot C'ye bastığı için satıra "bastığımız C" düşüyordu.

Liste **bellekte** duruyor ve uygulama kapanınca gidiyor: bu bir arşiv
değil, "az önce ne oldu" defteri. Kalıcı olması istenen şey zaten arşivin
kendisi; burada olup arşivde olmayan tek bilgi bizim seçtiğimiz şık. En
fazla 300 satır tutuluyor, üstünde bir de Temizle var.

Bir tur içinde aynı soru birkaç kez okunabildiği için listenin başındaki
satır aynı soruya ve aynı doğru cevaba aitse tekrar eklenmiyor.

---

## Soru başına istatistik

Her kayıt üç sayı tutar: kaç kez karşına çıktı, kaçında cevabın gözlendi,
kaçını doğru bildin. Liste ekranında şu şekilde görünür:

> **4 kez çıktı · 3 denemede %67**

Detay ekranında ayrıntısı var, CSV ve JSON çıktılarına da sütun olarak giriyor.
Böylece sürekli yanıldığın soruları `Cevabı eksik` yerine başarı oranına
bakarak ayıklayabilirsin.

## Şıkları sayı olan sorular neden okunamıyordu?

Şıkları "1 / 3 / 4 / 2" olan bir soruda uygulama hiç dokunmuyor, günlüğe
`RED: şık bölgesinde joker/puan rozetinden başka şık yok` yazıp sorunun
süresinin dolmasını bekliyordu. Sebep iki katmanlıydı.

**Birincisi: şık sayısını OCR belirliyordu.** "Ekranda kaç şık var ve
neredeler" sorusunun cevabı ML Kit'in metin bloklarından çıkarılıyordu.
Kelime şıklarında bu iyi çalışıyor. Ama koyu mor zemin üstünde tek başına
duran bir **"1"**, ML Kit'in metin/metin-değil sınıflandırıcısı için zayıf
bir aday: ya hiç döndürülmüyor, ya da dört rakam tek bloğa birleşiyor. İki
durumda da ayrıştırıcının eline dört değil sıfır-iki şık geçiyordu.

**İkincisi: kurtarma mekanizması tam da bu durumda çalışmıyordu.** Şık
şeridini büyütüp yeniden okuyan "yakın plan OCR", yalnızca red sebebi
*"şıklar henüz tamamlanmadı"* olduğunda tetikleniyordu — o mesaj ise ancak
**dört şıktan üçü** okunduğunda üretiliyor. Sıfır, bir veya iki şık
okunduğunda red başka bir mesajla dönüyor ve büyütme hiç denenmiyordu.
Büyütme ölçeğini ve bekleme sürelerini ayarlayan birkaç turluk düzeltme bu
yüzden sonuç vermedi: ayarlanan kod hiç çalışmıyordu.

**Çözüm: geometriyi OCR'dan çıkarmayı bırakmak.** Şıklar koyu mor zemin
üstünde geniş, parlak, eşit aralıklı haplar olarak çiziliyor. Bu dizilim,
içinde ne yazdığından bağımsız olarak ölçülebilir (`OptionBoxFinder.kt`):

1. Şık bandındaki her satırda "hap rengi" piksellerin oranı sayılır. Hapın
   dört hâli de (beyaz, turkuaz, yeşil, kırmızı) zemin morundan belirgin
   şekilde parlak.
2. Geniş **ve içi dolu** bir aralık veren satırlar hap satırıdır. "İçi
   dolu" şartı alttaki joker düğmelerini eliyor: üç altıgen de geniş bir
   aralığa yayılıyor ama aralarında mor boşluk var (doluluk %64'te kalıyor,
   hapta %100).
3. Ardışık hap satırları bir kutu olur; aynı yükseklikte ve eşit aralıklı
   dörtlü şık kutularıdır. Soru kartı da beyaz ve geniştir ama yazısı
   satırları böldüğü için tek parça bir kutu veremiyor, verdiği parça da
   haptan yüksek kalıyor.
4. Her hap **ayrı ayrı** kırpılıp üç kat büyütülerek okunuyor: tek bir
   rakam, beyaz zemin, etrafında başka hiçbir şey yok. Tam ekran OCR'ı o
   kutunun içine tam oturan bir metin zaten verdiyse (kelime şıkları) o
   kullanılıyor, fazladan okuma yapılmıyor.

Ölçüm tutmazsa hiçbir şey bozulmuyor: eski metin tabanlı yol olduğu gibi
yedekte duruyor ve devreye giriyor. Ayarlardan (*Şık kutularını ekrandan
ölç*) kapatılabiliyor.

**Yan kazanç: renk okuması da düzeliyor.** `optionRects` eskiden OCR metninin
sınırlarıydı — bir rakam şıkkında 25x50 piksellik bir harf kutusu. Renk
okuyucu onu örneklediği için günlüklerde bir şıkkın baskın rengi **mor**
çıkabiliyordu (kutu hapın üstünde değildi). Artık kutu hapın tamamı: "kart
oturdu mu", "hangi şık yeşil" ve "nereye dokunayım" ölçümlerinin üçü birden
doğru kutuya bakıyor.

**Teşhis kaydı.** Şıklar okunamadığında o anın ekran görüntüsü ve ham OCR
dökümü uygulamanın klasörüne yazılıyor (`files/teshis/`, en son 20 kare).
Bu arızayı turlarca günlük satırlarından geriye doğru tahmin ederek aramak
zorunda kaldık; artık başarısız kare diskte duruyor ve eşikler ölçülen
veriye göre ayarlanabiliyor. Ayarlardan kapatılabilir.

---

## OCR bir harfi yanlış okursa

Aynı soru iki kez çıktığında biri "…sönen **yıldıza** ne ad verilir?", diğeri
"…sönen **yildza** ne ad verilir?" okunabiliyor. Bunlar %97 benzer, yani
bulanık eşleştirme kuralı (eşik %92) zaten yakalıyor — ama eskiden
karşılaştırma yalnızca **son 300 kayda** bakıyordu. Arşiv birkaç yüz soruyu
geçince eski satır o pencerenin dışında kalıyor ve ikinci bir kayıt
açılıyordu.

Karşılaştırma artık **tüm arşivi** kapsıyor. Bunun ucuz olması için yalnızca
soru metni ve şıklar okunuyor; eşleşme bulunduğunda tam kayıt tek tek
çekiliyor. Benzerlik ölçüsü de uzunluk farkı büyük olan çiftleri hemen
eliyor, yani binlerce satır birkaç milisaniyede taranıyor.

İki metinden hangisinin kalacağına da bakılıyor: arayüz uyarısı içermeyen ve
daha eksiksiz olan kazanıyor. Yani soru bir dahaki çıkışında doğru okunursa
bozuk metin kendiliğinden düzeliyor.

### Kayıt eşiğini yükseltmek bunu çözmez

Ayarlardaki *Kayıt eşiği* metnin ne kadar doğru okunduğunu değil, **düzenin
soru ekranına benzeyip benzemediğini** ölçer: dört şık var mı, eşit aralıklı
mı, soru cümlesi gibi mi. Gerçek soru ekranlarının hemen hepsi %96–100 alır;
"yildza" da "yıldıza" da aynı puanı alır. Eşiği yükseltmek yalnızca iyi
yakalamaları eler.

Doğruluğu artıran şey beklemek: kart oturmadan kaydetmemek (aşağıda).

## Kart oturmadan kaydetme

Soru kartı ekrana solarak geliyor; bu sırada metin yarı saydam ve kayar
hâlde olduğu için OCR harfleri yanlış okuyor. Kayıt artık şık kutuları
çizilene kadar bekliyor — kutular oturduğunda bembeyaz, geçiş kareleri koyu
mor, yani ayırt etmek bedava.

Bu, soru başına yarım saniye kadar geciktiriyor. Sonradan elle temizlenmesi
gereken çift kayda kıyasla ucuz bir takas.

## Cevabı ilk seferde kaçırdıysa ne oluyor?

Hiçbir şey kaybolmuyor: soru bir dahaki çıkışında cevabı yakalanınca **aynı
satır** doldurulur, kayıt artık "cevabı eksik" görünmez. Bunun çalışması için
sorunun yeniden bulunabilmesi gerekiyor ve bulanık eşleştirme eskiden yalnızca
**son 300 kayda** bakıyordu — arşiv birkaç yüz soruyu geçince eski satır
pencerenin dışında kalıyor, OCR bir harfi farklı okuduysa parmak izi de
tutmuyordu. O zaman ikinci bir satır açılıyor, cevap ona yazılıyor, eskisi
sonsuza kadar cevapsız kalıyordu.

Artık kontrol **tüm arşivi** kapsıyor (yukarıya bak). Böylece cevabı kaçmış
bir soru yeniden çıktığında yeni bir satır açılmıyor; var olan satır bulunup
dolduruluyor.

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
