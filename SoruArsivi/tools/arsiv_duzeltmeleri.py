# -*- coding: utf-8 -*-
"""Elle doğrulanmış cevap düzeltmeleri.

Buraya yalnızca **kesin** olanlar giriyor. Ölçüt şu: sorunun apaçık doğru
bir şıkkı var ve kayıt başka bir şıkkı gösteriyor ("Meteor nedir? -> Maden",
"Japonya Uzay Araştırma Ajansı'nın kısaltması -> ESA" gibi). Ansiklopedik
tartışma götüren yerlere (Gazzâlî kaç sınıfa ayırdı, Uranüs mavi mi yeşil
mi) dokunulmuyor: orada oyunun kendi cevap anahtarı bizimkinden önemli ve
zaten renk okuması onu bir sonraki karşılaşmada tazeler.

Anahtar, sorunun normalize edilmiş metninde geçen ayırt edici bir parça.
"""

# (anahtar parça, doğru şık metni)
DUZELT = [
    # --- Felsefe / Matematik ---
    ("ethossozcugundenturemis",            "Ahlakın ilkelerini"),
    ("metafiziknedir",                     "Fizik ötesi"),
    ("felsefeninentemelgorevi",            "Sorgulamak"),
    ("kliseolarakkedilerhangisiyle",       "Yün yumağı"),
    ("hangisikarlibirsatisi",              "Satış fiyatı > Maliyet fiyatı"),
    ("kesircizgisininaltindakalan",        "Payda"),
    ("goruslerinbutunune",                 "Kuram"),
    ("takimcalismasiproblemcozme",         "21. yy"),
    ("alpharabius",                        "Farabi"),
    ("kinikokulununkurucusu",              "Antisthenes"),
    ("guzellikdenenkavraminbilimi",        "Estetik"),
    # Boş kalmış ama cevabı tartışmasız olanlar
    ("filmlerdepolislerdencokca",          "Kanun namına"),
    ("postmodernizmilebirlikteanilmaz",    "Aristoteles"),
    ("induktifakilyurutmenedir",           "Tümevarım"),
    ("nedensorusunakimlercevap",           "Filozoflar"),
    ("gunesulkesikitabinda",               "Tomasso Campanella"),
    ("islamiliteraturdeeflatun",           "Platon"),

    # --- Tekrar gruplarındaki çelişkiler ---
    # Aynı soru iki kez kaydedilmiş ve iki farklı cevap taşıyor. Kanıt gücü
    # burada karar veremiyor: aşağıdakilerin ikisinde "renk (kesin)" bile
    # yanlış şıkkı işaretlemiş (ekran görüntüsü cevap animasyonunun ortasına
    # denk gelmiş olmalı).
    ("haberlesmeuydularin",                "Türksat"),
    ("uluslararasiuzayistasyonunun",       "ISS"),
    ("kozmikradyasyonlardankorur",         "Atmosfer ve manyetik alan"),
    ("incebirseritgibi",                   "Hilâl"),
    ("2006yilininagustos",                 "Plüton"),
    ("izledigiyolanedenir",                "Yörünge"),
    ("kutupyildizin", "Kuzey Yildızı"),

    # --- Bilim ve Uzay ---
    ("tekrarkullanilabilirsekildeuretilen", "Uzay mekiği"),
    ("ulusalhavaci",            "NASA"),
    ("icindebulundugumuzgalaksinin",       "Samanyolu"),
    ("ikiucundabulunannoktalara",          "Kutup"),
    ("gorulendegerleritespitetme",         "Rasat"),
    ("ongorulemeyengunesaktivitesi",       "Uydu çökmeleri"),
    ("ciplakgozlegorulebildigine",         "Çin Seddi"),
    ("astronomibirimindenfazladir",        "Satürn"),
    ("hangisiuzaybilimlerindenbiridegildir", "Jeoloji"),
    ("apollo11gorevinindevasa",            "Saturn V"),
    ("etrafindakidonusunukacgunde",   "365"),
    ("halleyneturbirgokcismi",             "Kuyruklu yıldız"),
    ("eskiadiutarit",                      "Merkür"),
    ("plutonunyanindangecerek",            "New Horizons"),
    ("sarmalnebula",                       "Edwin Hubble"),
    ("bilinen27uydusuna",                  "Uranüs"),
    ("gecelerienparlakyildiz",             "Sirius"),
    ("yuzeylerikatilasmis",                "Venüs"),
    ("enickatmanihangisidir",                 "Çekirdek"),
    ("enickatmanihangi",                 "Çekirdek"),
    ("dunyahangiikigezegenarasinda",       "Venüs-Mars"),
    ("zehirligazlardanolusangezegen",      "Venüs"),
    ("gunesievreninmerkeziolarak",         "Kopernik modeli"),
    ("ulkemizdeenuzungunduz",              "21 Haziran"),
    ("metcezirin",              "Ay"),
    ("japonyauzayarastirmaajansininkisaltmasi", "JAXA"),
    ("meteornedir",                        "Gök taşı"),
    ("neacverilig",                        "Yapay uydu"),
    ("zamandiliminekarsil",   "24 saat"),
    ("hangigezegeninetrafindahalkayoktur", "Merkür"),
    ("titanyumlualasimlarla",              "Vanadyum"),
    ("turkiyeninuzayprojelerindenbiridegildir", "ARES"),
    ("yilboyuncageceve",                   "Ekvator çizgisi"),
    ("buyukkutleliyildizlar",              "Süper dev"),
    ("ayinyercekimidunyanin",              "1/6"),
    ("gunesinyorungesindekikucukkaya",     "Asteroid"),
    ("zuhrecoban",                  "Venüs"),
    ("suruhalindehizla",           "Starlink uyduları"),
    ("nasaninayadonusprogrami",            "Artemis"),
    ("marsinkutlesidunyanin",              "Onda biri"),
    ("entegreedilenlaboratuvarmodulu",     "Kibo"),
    ("donmesineticesinde", "Gün"),
    ("phoenixuzayaraci",                   "Mars"),
    ("richardbranson",                     "Virgin Galactic"),
    ("astronotgiysilerinindiskatmani",     "Beyaz"),
    ("kacmasinaizinvermeyecek",            "Kara delik"),
    ("yildizlarinyapisindaenfazla",        "Hidrojen"),
    ("avrupaguneygozlemevi",               "ESO"),
    ("ogezegeninkendineaitbiryilindan",    "Venüs"),
    ("dunyagunessistemindekikacinci",      "3"),
    ("cinulusaluzayidaresi",               "Tianwen"),
    ("1990yilindauzaymekigi", "Hubble"),
    ("anatolysolovyev",                    "82 saat"),
    ("hidrojenihangisinedonusturerek",     "Helyum"),
    ("bulunanyildizsayisiortalama", "200 - 400 milyar"),
    ("ikincidefameydanagelendolunaya",     "Mavi ay"),
    ("terrestrialgezegendegildir",         "Jüpiter"),
]

# Cevabı çöp çıkmış, doğrusu da şıklar arasında olmayanlar: boşaltılıyor ki
# otomatik mod bir dahaki karşılaşmada gerçeğini öğrensin.
BOSALT = ["descarteshangiulkededogmustur"]

# Ekranın yarısı okunmuş, şıkları parça parça bölünmüş kayıt.
SIL = ["bilimin mantiksaligini"]
