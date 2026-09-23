package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.util.TurkishText

/**
 * Arşivdeki bir kaydın, tekrar denetimi için önceden hesaplanmış hâli.
 *
 * Neden var: yeni (arşivde parmak izi olmayan) her okuma, arşivin
 * **tamamıyla** karşılaştırılıyor. Eskiden her karşılaştırmada her satırın
 * metni yeniden sadeleştiriliyor, kelimelerine ayrılıyor, şıkları yeniden
 * sıralanıyordu. 4000 satırlık arşivde bu, masaüstünde bile soru başına
 * 150-550 ms; telefonda birkaç katı. Cevabı bilinmeyen soruda uyarı sesinin
 * geç gelmesinin ve taramanın ağırlaşmasının bir parçası buydu. Bu hesaplar
 * satır başına bir kez yapılıp saklanıyor (bkz. `Repo`'daki önbellek).
 */
internal class TekrarAdayi(val id: Long, question: String, options: List<String>) {
    val key: String = TurkishText.normalizeKey(question)
    val negation: Int = TurkishText.negationSignature(question)
    val optionKeys: List<String> = options.map { TurkishText.normalizeKey(it) }
    val sortedOptionKey: String = optionKeys.sorted().joinToString("|")
    val words: List<String> by lazy { TurkishText.words(question) }

    constructor(row: DedupRow) : this(row.id, row.questionText, row.options)
}

/**
 * "Bu soru zaten arşivde mi?" kararını veren kurallar.
 *
 * Tek yerde duruyor çünkü iki ayrı yol aynı kararı vermek zorunda:
 * ekrandan yakalama ve yedekten içe aktarma. Kurallar eskiden `Repo`'nun
 * içindeki `Probe`'daydı; sonuç birebir aynı, yalnızca sıralama ucuzdan
 * pahalıya: sonucu değiştiremeyecek bir hesap hiç yapılmıyor.
 */
internal class TekrarSorgusu(question: String, options: List<String>) {
    private val aday = TekrarAdayi(0L, question, options)
    private val optionCount = options.size

    fun matches(old: TekrarAdayi): Boolean {
        // Şıklar birebir aynı olmak zorunda değil: OCR bir şıkkın
        // sonundaki harfi düşürünce ("Fransa" / "Frans") birebir eşitlik
        // tutmuyor ve kırpılmış okuma yakalanamıyordu. Sayı şıklarında
        // yine birebir eşitlik aranıyor.
        //
        // Sorunun sonu okunamamış olabilir: OCR son kelimeyi düşürdüğünde
        // "…kullanım amaçlarından biri" ile "…kullanım amaçlarından biri
        // değildir?" iki ayrı kayıt oluyordu. Üstelik düşen kelime tam da
        // olumsuzluk kelimesi olduğu için aşağıdaki olumsuzluk kontrolü
        // ikisini birleştirmeyi kesin olarak reddediyordu.
        //
        // Bu yüzden kırpılmış okuma kontrolü olumsuzluk kontrolünden ÖNCE
        // geliyor. Ölçüt dar tutuldu: dört şık birebir aynı olacak ve kısa
        // metin uzun metnin başlangıcıyla örtüşecek. "Hangisi X'tir?" ile
        // "Hangisi X değildir?" birbirinin başlangıcı olmadığı için bu
        // kapıdan geçemez.
        if (TurkishText.optionKeysNearlyMatch(old.optionKeys, aday.optionKeys) &&
            truncatedHead(old.words, aday.words)
        ) return true

        // Olumsuzluk farkı varsa hiçbir benzerlik ölçüsü bunları
        // birleştiremez — zıt anlamlı iki ayrı sorudur.
        if (old.negation != aday.negation) return false

        val oldKey = old.key
        val key = aday.key
        // Eski kuraldaki uç durum korunuyor: harf/rakam içermeyen iki metnin
        // benzerliği 1 sayılıyordu.
        if (oldKey.isEmpty() && key.isEmpty()) return true
        val lengthRatio = minOf(oldKey.length, key.length).toFloat() /
            maxOf(oldKey.length, key.length).coerceAtLeast(1)
        // Aşağıdaki üç kuralın hiçbiri %60'ın altındaki uzunluk oranında
        // tutamaz (benzerlik orada zaten 0 dönüyor); Levenshtein'a girmeden
        // eleniyor. Arşivin büyük kısmı burada düşüyor.
        if (lengthRatio < 0.60f) return false

        val optionsMatch = optionCount >= 3 && old.optionKeys.size == optionCount &&
            old.sortedOptionKey == aday.sortedOptionKey

        // Yarım yakalanmış okuma ("…kaç" ile "…kaç adettir?"). Bir sorunun
        // metninin başka bir soruda geçmesi onu aynı soru yapmaz; bu yüzden
        // hem uzunluklar birbirine çok yakın olmalı hem de ya şıklar birebir
        // aynı olmalı ya da fark çok küçük olmalı.
        val contained = oldKey.length >= 12 && key.length >= 12 &&
            (key.contains(oldKey) || oldKey.contains(key)) &&
            (optionsMatch && lengthRatio >= 0.60f || lengthRatio >= 0.85f)
        if (contained) return true

        // Benzerlik, uzunluk oranını aşamaz: %80 için oran en az %80, %92
        // için en az %92 olmalı. Tutamayacaksa hesaplanmıyor.
        val gerekli = if (optionsMatch) 0.80f else 0.92f
        if (lengthRatio < gerekli) return false
        val sim = TurkishText.similarityOfKeys(oldKey, key)

        // Dört şıkkın tamamı birebir aynıysa neredeyse kesinlikle aynı
        // sorudur. Metnin başına "17. Süre Bitti" gibi bir fazlalık
        // yapışıp üstüne bir de OCR harf hatası olunca ne kapsama ne
        // benzerlik tutuyordu; şıklar bu ikisini de kurtarıyor.
        // Dört şık birebir aynı olsa bile metinler birbirinden çok
        // farklıysa ayrı sorulardır ("Hangisi X'tir?" / "Hangisi X
        // değildir?" aynı şıkları paylaşabiliyor). Bu yüzden eşik yüksek.
        val sameOptions = optionsMatch && sim >= 0.80f

        return sameOptions || sim >= 0.92f
    }

    companion object {
        /**
         * Biri diğerinin, sonundan bir iki kelime düşmüş hâli mi?
         *
         * Karşılaştırma KELİME bazında. Karakter dizisi üzerinden bakmak
         * tehlikeliydi: "…ölçütlerindendir?" ile "…ölçütlerinden değildir?"
         * harf harf neredeyse aynı görünüyor ve kural bu iki ayrı soruyu
         * birleştiriyordu — birim test bunu yakaladı. Kelimelere bölününce
         * son kelimelerin farkı ("olcutlerindendir" ≠ "olcutlerinden")
         * ortaya çıkıyor.
         *
         * Kelimeler birebir değil benzerlikle karşılaştırılıyor, çünkü OCR
         * aynı karede ortadaki bir harfi de kaçırabiliyor ("uydularin" /
         * "uydulariin").
         */
        internal fun truncatedHead(wa: List<String>, wb: List<String>): Boolean {
            val kisa = if (wa.size <= wb.size) wa else wb
            val uzun = if (wa.size <= wb.size) wb else wa
            val fazla = uzun.size - kisa.size
            if (fazla !in 1..MAX_MISSING_WORDS) return false
            if (kisa.size < MIN_HEAD_WORDS) return false
            return kisa.indices.all { i ->
                kisa[i] == uzun[i] ||
                    TurkishText.similarityOfKeys(kisa[i], uzun[i]) >= WORD_MIN_SIMILARITY
            }
        }

        /**
         * Kırpılmış okumada en fazla bu kadar kelime düşmüş olabilir.
         *
         * Temizlenmiş arşivdeki 732 sorunun tüm çiftleri tarandı: bu kural
         * 1..5 aralığının tamamında yalnızca tek bir çifti birleştiriyor ve
         * o çift gerçekten aynı sorunun kırpılmış hâli ("Aşağıdaki ülkelerden
         * hangisi Uluslararası Uzay İstasyonu" / "…İstasyonu misyonu
         * içerisinde değildir?" — üç kelime düşmüş). Yani gözlenen tek gerçek
         * vaka iki kelimeyle yakalanamıyordu; üç, yanlış birleşme üretmeden
         * onu da kapsıyor.
         */
        private const val MAX_MISSING_WORDS = 3
        /** Kısa metin en az bu kadar kelime taşımalı. */
        private const val MIN_HEAD_WORDS = 4
        /** Aynı sıradaki kelimeler bu kadar benzemeli. */
        private const val WORD_MIN_SIMILARITY = 0.85f
    }
}
