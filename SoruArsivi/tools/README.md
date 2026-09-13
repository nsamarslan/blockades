# Arşiv bakım araçları

## `arsiv_temizle.py`

Ayarlar > Yedekleme > **Dışa aktar (JSON)** ile aldığın dosyayı temizler:

```bash
cd SoruArsivi/tools
python3 arsiv_temizle.py ~/İndirilenler/soru_arsivi_20260913.json duzeltilmis.json
```

Bağımlılığı yok, saf Python 3.

### Ne yapıyor

| Adım | Ne düzeltiliyor |
| --- | --- |
| Metin onarımı | Soruya yapışan süre balonu (`4,sn`), soru numarası (`(6s)`), joker rozeti (`X2`) ve puan balonu (`+5`) |
| Şık temizliği | Şık sanılmış joker/puan rozetleri: `['Kiminle?', 'Nerede?', '50,', '50']` |
| Tekrar birleştirme | Aynı sorunun birden çok satırı; sayaçlar toplanmaz, en büyüğü alınır |
| Yarım kayıt | Tam metnin yalnızca sonundan ibaret satırlar |
| Cevap düzeltmesi | `arsiv_duzeltmeleri.py` içindeki elle doğrulanmış liste |

Eşleştirme kuralları uygulamadaki `Repo.Probe` ile birebir aynı; olumsuzluk
imzası tutmayan sorular ("hangisidir" / "hangisi değildir") asla
birleştirilmiyor.

### Hiçbir sayı "sayı olduğu için" atılmaz

`['500', '5500', '50', '100']` gerçek bir sıcaklık sorusunun şıkları. Kural,
aynı kayıtta **kanıtlanmış** bir joker sızıntısı arıyor: `50,` / `x2` gibi
tartışmasız bir rozet ya da iki kez okunmuş aynı şık. Ancak o zaman geri
kalan çıplak sayılar da atılıyor.

### Cevap düzeltmelerinde ölçüt

`arsiv_duzeltmeleri.py`'ye yalnızca **kesin** olanlar giriyor: sorunun apaçık
doğru bir şıkkı var ve kayıt başkasını gösteriyor ("Meteor nedir? → Maden",
"Japonya Uzay Araştırma Ajansı'nın kısaltması → ESA"). Ansiklopedik tartışma
götüren yerlere dokunulmuyor — orada oyunun kendi cevap anahtarı bizimkinden
önemli ve renk okuması onu bir sonraki karşılaşmada zaten tazeliyor.

Düzeltilen kayıtlar `elleDuzenlendi: true` ile işaretleniyor; uygulama
bunların üstüne bir daha yazmaz.

## İçe aktarmadan önce: arşivi sil

`Importers.merge` var olan cevabın **üstüne yazmaz**, yalnızca eksikleri
tamamlar; hiçbir satırı da silmez. Yani temizlenmiş dosyayı dolu bir arşivin
üstüne aktarırsan ne düzeltmeler uygulanır ne de çöp kayıtlar gider.

Doğru sıra: **Ayarlar > Tüm arşivi sil**, sonra **İçe aktar**.
