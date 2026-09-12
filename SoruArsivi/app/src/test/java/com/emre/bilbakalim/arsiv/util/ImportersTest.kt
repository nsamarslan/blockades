package com.emre.bilbakalim.arsiv.util

import com.emre.bilbakalim.arsiv.data.QuestionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yedekten geri yükleme testleri.
 *
 * Buradaki asıl risk veri kaybı: içe aktarma var olan bir kaydın bildiği
 * cevabı ezerse ya da sayaçları şişirirse arşiv sessizce bozulur. Testler
 * bu iki kuralı çiviliyor.
 */
class ImportersTest {

    private val yedek = """
        {
          "olusturma": 1757000000000,
          "adet": 2,
          "sorular": [
            {
              "soru": "Türkiye'nin başkenti neresidir?",
              "siklar": ["İstanbul", "Ankara", "İzmir", "Bursa"],
              "dogruIndeks": 1,
              "dogruMetin": "Ankara",
              "kategori": "Genel Kültür",
              "kaynak": "OCR",
              "guven": 0.93,
              "kacKezCikti": 4,
              "cevapladigin": 3,
              "dogruBildigin": 2,
              "tarih": 1756900000000,
              "elleDuzenlendi": false,
              "not": null
            },
            {
              "soru": "Bir koşuda 1. atletin 2. atletle mesafesi kaç metredir?",
              "siklar": ["25", "55", "5", "15"],
              "dogruIndeks": null,
              "dogruMetin": null,
              "kategori": null,
              "kaynak": "OCR",
              "guven": 0.81,
              "kacKezCikti": 1,
              "cevapladigin": 0,
              "dogruBildigin": 0,
              "tarih": 1756950000000
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `uygulamanin kendi ciktisini okur`() {
        val rows = Importers.parse(yedek)
        assertEquals(2, rows.size)
        assertEquals("Ankara", rows[0].correctText)
        assertEquals(listOf("İstanbul", "Ankara", "İzmir", "Bursa"), rows[0].options)
        assertEquals("Genel Kültür", rows[0].category)
        assertEquals(4, rows[0].seenCount)
        // Cevabı bilinmeyen satır da gelmeli — sonradan tamamlanabilsin.
        assertNull(rows[1].correctText)
        assertNull(rows[1].category)
        // Sayı şıklar korunuyor.
        assertEquals(listOf("25", "55", "5", "15"), rows[1].options)
    }

    @Test
    fun `duz dizi de kabul edilir`() {
        val rows = Importers.parse("""[{"soru":"Kaç kıta vardır?","siklar":["5","6","7","8"],"dogruMetin":"7"}]""")
        assertEquals(1, rows.size)
        assertEquals("7", rows[0].correctText)
    }

    @Test
    fun `bozuk dosya anlasilir hata verir`() {
        listOf("", "merhaba", "{\"baska\":1}").forEach { text ->
            val hata = runCatching { Importers.parse(text) }.exceptionOrNull()
            assertTrue("\"$text\" için hata bekleniyordu", hata is IllegalArgumentException)
        }
    }

    @Test
    fun `eski yedekte yalnizca sira varsa metne cevrilir`() {
        val rows = Importers.parse("""[{"soru":"Kaç kıta vardır?","siklar":["5","6","7","8"],"dogruIndeks":2}]""")
        assertEquals("7", rows[0].correctText)
    }

    // --- birleştirme --------------------------------------------------------

    private fun kayit(
        correctIndex: Int? = null,
        options: List<String> = listOf("İstanbul", "Ankara", "İzmir", "Bursa"),
        seen: Int = 1,
        answered: Int = 0,
        correct: Int = 0,
        category: String? = null
    ) = QuestionEntity(
        id = 7,
        questionText = "Türkiye'nin başkenti neresidir?",
        optionA = options.getOrNull(0),
        optionB = options.getOrNull(1),
        optionC = options.getOrNull(2),
        optionD = options.getOrNull(3),
        correctIndex = correctIndex,
        category = category,
        fingerprint = "eski",
        capturedAt = 1756800000000,
        seenCount = seen,
        answeredCount = answered,
        correctCount = correct
    )

    @Test
    fun `cevabi eksik kayit yedekten tamamlanir`() {
        val gelen = Importers.parse(yedek)[0]
        val sonuc = Importers.merge(kayit(correctIndex = null), gelen)
        assertEquals(1, sonuc.correctIndex)
        assertEquals("Ankara", sonuc.correctText)
        assertEquals("Genel Kültür", sonuc.category)
    }

    @Test
    fun `siklar karisik sirada olsa bile dogru cevap dogru yere oturur`() {
        // Cihazdaki kayıtta sıra farklı: Ankara 3. sırada.
        val yerel = kayit(
            correctIndex = null,
            options = listOf("Bursa", "İzmir", "Ankara", "İstanbul")
        )
        val sonuc = Importers.merge(yerel, Importers.parse(yedek)[0])
        assertEquals(2, sonuc.correctIndex)
        assertEquals("Ankara", sonuc.correctText)
    }

    @Test
    fun `cihazdaki cevap ezilmez`() {
        // Yedekte "Ankara" doğru diyor ama cihazdaki kayıt "İzmir" demiş
        // (kullanıcı elle düzeltmiş olabilir). Cihazdaki kazanır.
        val sonuc = Importers.merge(kayit(correctIndex = 2), Importers.parse(yedek)[0])
        assertEquals(2, sonuc.correctIndex)
        assertEquals("İzmir", sonuc.correctText)
    }

    @Test
    fun `sayaclarda buyuk olan alinir ve iki kez almak sismez`() {
        val gelen = Importers.parse(yedek)[0]   // 4 / 3 / 2
        val yerel = kayit(seen = 2, answered = 5, correct = 1)
        val birinci = Importers.merge(yerel, gelen)
        assertEquals(4, birinci.seenCount)
        assertEquals(5, birinci.answeredCount)
        assertEquals(2, birinci.correctCount)

        // Aynı dosyayı bir kez daha almak hiçbir şeyi değiştirmemeli.
        val ikinci = Importers.merge(birinci, gelen)
        assertEquals(birinci.seenCount, ikinci.seenCount)
        assertEquals(birinci.answeredCount, ikinci.answeredCount)
        assertEquals(birinci.correctCount, ikinci.correctCount)
    }

    @Test
    fun `dogru bildigin sayisi cevapladigini gecemez`() {
        val gelen = Importers.parse(yedek)[0]
        val sonuc = Importers.merge(kayit(answered = 0, correct = 0), gelen)
        assertTrue(sonuc.correctCount <= sonuc.answeredCount)
    }

    @Test
    fun `eksik siklar tamamlanir ama var olanlar yerinden oynamaz`() {
        val yerel = kayit(correctIndex = 0, options = listOf("İstanbul", "Ankara"))
        val sonuc = Importers.merge(yerel, Importers.parse(yedek)[0])
        assertEquals("İstanbul", sonuc.optionA)
        assertEquals("Ankara", sonuc.optionB)
        assertEquals(listOf("İstanbul", "Ankara", "İzmir", "Bursa"), sonuc.options)
        // Doğru cevap hâlâ İstanbul'u gösteriyor olmalı.
        assertEquals("İstanbul", sonuc.correctText)
    }

    @Test
    fun `ilk gorulme tarihi korunur`() {
        val sonuc = Importers.merge(kayit(), Importers.parse(yedek)[0])
        assertEquals(1756800000000, sonuc.capturedAt)
    }

    @Test
    fun `arsivde olmayan satir yeni kayda cevrilir`() {
        val entity = Importers.toEntity(Importers.parse(yedek)[0])
        assertEquals("Ankara", entity.correctText)
        assertEquals(Importers.ANSWER_SOURCE, entity.answerSource)
        assertTrue(entity.fingerprint.isNotBlank())

        // Cevabı bilinmeyen satırda kaynak da boş kalmalı.
        val cevapsiz = Importers.toEntity(Importers.parse(yedek)[1])
        assertNull(cevapsiz.correctIndex)
        assertNull(cevapsiz.answerSource)
    }

    @Test
    fun `degisiklik yoksa ayni nesne dondurulur`() {
        // Repo bu eşitliğe bakıp "değişmedi" sayıyor; bozulmamalı.
        val tam = kayit(correctIndex = 1, seen = 9, answered = 9, correct = 9, category = "Genel Kültür")
            .copy(fingerprint = TurkishText.fingerprint(kayit().questionText, kayit().options))
        val sonuc = Importers.merge(tam, Importers.parse(yedek)[0])
        assertEquals(tam, sonuc)
        assertSame(tam.questionText, sonuc.questionText)
    }
}
