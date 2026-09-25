package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Eski temizlik kuralının eksi işaretini sildiği kayıtların onarımı.
 *
 * Gerçek günlük (3.6): «(-6) neye eşittir?» sorusunun şıkları arşive
 * "16 / 4 / 4 / 16" diye yazılmıştı. İki şık ikişer kez geçtiği için doğru
 * cevap hiçbir zaman kaydedilemiyordu; parmak izi işareti görmediği için de
 * doğru okuma hep bu bozuk kayda düşüyordu.
 */
class IsaretOnarimTest {

    private val ekran = listOf("-16", "4", "-4", "16")
    private val bozuk = listOf("4", "16", "16", "4")

    @Test
    fun `eksisi silinmis siklar ekrandakilerle yeniden yazilir`() {
        // Kayıttaki "4" ekranda hem "-4" hem "4" olabilir: cevap boşaltılıyor.
        assertEquals(Repo.Onarim(null), Repo.isaretOnarimi(bozuk, "4", ekran))
        assertEquals(Repo.Onarim(null), Repo.isaretOnarimi(bozuk, null, ekran))
    }

    @Test
    fun `tek karsiligi olan cevap yeni sirasiyla korunur`() {
        val eski = listOf("3", "7", "5", "9")
        val yeni = listOf("9", "-3", "5", "7")
        assertEquals(Repo.Onarim(3), Repo.isaretOnarimi(eski, "7", yeni))
    }

    @Test
    fun `saglam kayda dokunulmaz`() {
        assertNull(Repo.isaretOnarimi(ekran, "-4", ekran.reversed()))
        assertNull(Repo.isaretOnarimi(listOf("1", "2", "3", "4"), "2", listOf("4", "3", "2", "1")))
    }

    @Test
    fun `ekranda da isaretsiz okunmussa onarim yok`() {
        // Bu kez OCR eksiyi görmemiş: elimizde daha iyi bir bilgi yok.
        assertNull(Repo.isaretOnarimi(bozuk, null, bozuk))
    }

    @Test
    fun `baska sebeple farkli siklar onarim sayilmaz`() {
        assertNull(Repo.isaretOnarimi(listOf("4", "16", "16", "5"), null, ekran))
        assertNull(Repo.isaretOnarimi(listOf("4", "16", "16"), null, ekran))
    }

    @Test
    fun `ekrandaki siklar ayirt edilemiyorsa onarim yok`() {
        assertNull(Repo.isaretOnarimi(bozuk, null, listOf("-4", "-4", "16", "16")))
    }
}
