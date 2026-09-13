#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Dışa aktarılmış arşiv JSON'unu temizler.

    python3 tools/arsiv_temizle.py arsiv.json duzeltilmis.json

Neyi düzeltiyor — hepsi gerçek kayıtlarda görülmüş bozulmalar:

  1. Soru metnine yapışan arayüz parçaları. OCR süre balonunu ("4,sn"),
     soru numarasını ("(6s)"), joker rozetlerini ("X2") ve puan balonunu
     ("+5") soruyla aynı blokta okuyor.
  2. Şık sanılan joker/puan rozetleri. "50 50" jokeri şık hizasında
     durduğu için ['Kiminle?', 'Nerede?', '50,', '50'] gibi kayıtlar
     çıkıyor; hatta doğru cevap olarak "200" işaretlenmiş satırlar var.
  3. Aynı sorunun birden çok satırı. Eşleştirme uygulamadaki
     Repo.Probe ile birebir aynı kurallarla yapılıyor, artı uygulamanın
     yakalayamadığı bir durum: yarım okunmuş, tam metnin sonundan ibaret
     satırlar.
  4. Elle doğrulanmış cevap düzeltmeleri (arsiv_duzeltmeleri.py).

Hiçbir sayı "sayı olduğu için" atılmıyor: ['500','5500','50','100'] gerçek
bir sıcaklık sorusunun şıkları. Kural, aynı kayıtta kanıtlanmış bir joker
sızıntısı arıyor.

Çıktı, uygulamanın kendi dışa aktarma biçiminin aynısıdır; Ayarlar >
Yedekleme > İçe aktar ile geri yüklenir. ÖNEMLİ: içe aktarma var olan
cevabın üstüne yazmaz, yalnızca eksikleri tamamlar. Düzeltmelerin işe
yaraması için önce "Tüm arşivi sil" demek gerekiyor.
"""
import json
import sys
import time

from arsiv_kurallari import (clean_options, fix_question, match_index, norm,
                             question_broken, soft)
from arsiv_duzeltmeleri import BOSALT, DUZELT, SIL

# Cevabın ne kadar sağlam bir gözlemden geldiği; uygulamadaki
# Repo.AnswerEvidence ile aynı sıralama.
GUC = {"renk (kesin)": 4, "elle": 4, "renk": 3, "süre doldu": 3,
       "dokunuş": 1, "içe aktarım": 1}

CHROME = ("süre bitti", "sure bitti", "süre doldu", "muhteşem", "tebrikler",
          "biraz daha gayret", "kombo")

ALAN = ["soru", "siklar", "dogruIndeks", "dogruMetin", "cevapKaynagi",
        "kategori", "kaynak", "guven", "kacKezCikti", "cevapladigin",
        "dogruBildigin", "basariYuzde", "tarih", "elleDuzenlendi", "not"]


def kirli(s):
    return any(c in (s or "").lower() for c in CHROME)


def guc(r):
    if r.get("dogruIndeks") is None:
        return -1
    return GUC.get(r.get("cevapKaynagi") or "", 2)


def optkey(opts):
    return "|".join(sorted(norm(o) for o in opts))


def kesin_index(options, dogru):
    """Düzeltme tablosu için şık arama.

    Önce simgeleri koruyan birebir eşleşme: norm() '<' ve '>' işaretlerini
    sildiği için bulanık arama "Satış fiyatı > Maliyet" ile "Satış fiyatı <
    Maliyet" şıklarını aynı sayıyor ve düzeltme hiçbir şeyi değiştirmeden
    geçiyordu.
    """
    hedef = soft(dogru)
    for i, o in enumerate(options):
        if soft(o) == hedef:
            return i
    if any(c in dogru for c in "<>=+-/"):
        return None
    return match_index(options, dogru)


# --- 1. adım: metin ve şık onarımı ----------------------------------------

def onar(rows, rapor):
    out = []
    for r in rows:
        q = fix_question(r["soru"])
        opts, _ = clean_options(r["siklar"])

        # Doğru cevap METNİYLE taşınıyor: oyun şıkları her turda karıştırdığı
        # için sırayı taşımak yanlış şıkka oturuyor.
        idx0 = r.get("dogruIndeks")
        ctext = r.get("dogruMetin")
        if ctext is None and isinstance(idx0, int) and 0 <= idx0 < len(r["siklar"]):
            ctext = r["siklar"][idx0]
        yeni = match_index(opts, ctext) if ctext else None

        why = question_broken(q)
        if why is None and len(opts) < 2:
            why = f"temizlikten sonra {len(opts)} şık kaldı"
        if why:
            rapor.setdefault("silinen", []).append((why, r["soru"]))
            continue

        o = dict(r)
        o["soru"] = q
        o["siklar"] = opts
        o["dogruIndeks"] = yeni
        o["dogruMetin"] = opts[yeni] if yeni is not None else None
        if yeni is None:
            o["cevapKaynagi"] = None
        out.append(o)
    return out


# --- 2. adım: tekrar eden kayıtlar ----------------------------------------

def negation(s):
    low = (s or "").lower()
    sig = 0
    for word, bit in (("değil", 1), ("yanlış", 2), ("olmayan", 4),
                      ("hariç", 8), ("dışında", 16), ("söylenemez", 32)):
        if word in low:
            sig |= bit
    return sig


def ayni_soru(a, b):
    """Repo.Probe.matches'in birebir karşılığı."""
    from arsiv_kurallari import sim
    if negation(a["soru"]) != negation(b["soru"]):
        return False
    ka, kb = norm(a["soru"]), norm(b["soru"])
    s = sim(a["soru"], b["soru"])
    opts_ayni = (len(a["siklar"]) >= 3 and len(a["siklar"]) == len(b["siklar"])
                 and optkey(a["siklar"]) == optkey(b["siklar"]))
    oran = min(len(ka), len(kb)) / max(len(ka), len(kb), 1)
    kapsama = (len(ka) >= 12 and len(kb) >= 12 and (ka in kb or kb in ka)
               and ((opts_ayni and oran >= 0.60) or oran >= 0.85))
    return kapsama or (opts_ayni and s >= 0.80) or s >= 0.92


def gruplar(rows):
    """Levenshtein pahalı; önce 4-gram örtüşmesiyle aday eliyoruz."""
    from collections import defaultdict
    keys = [norm(r["soru"]) for r in rows]
    grams = [{k[x:x + 4] for x in range(max(1, len(k) - 3))} for k in keys]
    index = defaultdict(list)
    for i, gs in enumerate(grams):
        for g in gs:
            index[g].append(i)

    out, used = [], [False] * len(rows)
    for i, a in enumerate(rows):
        if used[i]:
            continue
        hits = defaultdict(int)
        for g in grams[i]:
            for j in index[g]:
                if j > i:
                    hits[j] += 1
        grp = [i]
        used[i] = True
        for j, c in hits.items():
            if used[j] or c / max(len(grams[i]), len(grams[j]), 1) < 0.45:
                continue
            if ayni_soru(a, rows[j]):
                grp.append(j)
                used[j] = True
        if len(grp) > 1:
            out.append(sorted(grp))
    return out


def birlestir(rs):
    """Temiz metin > çok şık > uzun metin olan satır temsilci olur."""
    sirali = sorted(rs, key=lambda r: (kirli(r["soru"]), -len(r["siklar"]),
                                       -len(r["soru"])))
    base = dict(sirali[0])
    opts = list(base["siklar"])
    for r in sirali[1:]:
        for o in r["siklar"]:
            if len(opts) >= 4:
                break
            if match_index(opts, o) is None:
                opts.append(o)
    base["siklar"] = opts

    best = max(rs, key=guc)
    idx = match_index(opts, best.get("dogruMetin")) if guc(best) >= 0 else None
    base["dogruIndeks"] = idx
    base["dogruMetin"] = opts[idx] if idx is not None else None
    base["cevapKaynagi"] = best.get("cevapKaynagi") if idx is not None else None

    base["kacKezCikti"] = max(r.get("kacKezCikti", 1) or 1 for r in rs)
    base["cevapladigin"] = max(r.get("cevapladigin", 0) or 0 for r in rs)
    base["dogruBildigin"] = min(max(r.get("dogruBildigin", 0) or 0 for r in rs),
                                base["cevapladigin"])
    base["tarih"] = min(r.get("tarih", 0) or 0 for r in rs)
    base["guven"] = max(r.get("guven", 1.0) or 0 for r in rs)
    base["elleDuzenlendi"] = any(r.get("elleDuzenlendi") for r in rs)
    base["kategori"] = next((r.get("kategori") for r in rs if r.get("kategori")), None)
    return base


def yarim_olanlari_at(rows):
    """Tam metnin sonundan ibaret, yarım okunmuş satırlar.

    Uygulamanın kendi eşleştirmesi bunları birleştiremiyor (uzunluk oranı
    çok düşük), o yüzden burada eliyoruz. Ölçüt sıkı: biri diğerinin gerçek
    alt dizisi olacak ve dört şık birebir tutacak.
    """
    keys = [norm(r["soru"]) for r in rows]
    oks = [optkey(r["siklar"]) for r in rows]
    at = set()
    for i in range(len(rows)):
        if i in at or len(rows[i]["siklar"]) < 3:
            continue
        for j in range(len(rows)):
            if i == j or j in at:
                continue
            if oks[i] == oks[j] and len(keys[i]) < len(keys[j]) \
                    and keys[i] and keys[i] in keys[j]:
                at.add(i)
                break
    return [r for i, r in enumerate(rows) if i not in at], len(at)


# --- 3. adım: elle düzeltmeler --------------------------------------------

def duzelt(rows, rapor):
    for anahtar, dogru in DUZELT:
        hit = next((r for r in rows if anahtar in norm(r["soru"])), None)
        if hit is None:
            rapor.setdefault("uygulanamadi", []).append((anahtar, "soru yok"))
            continue
        idx = kesin_index(hit["siklar"], dogru)
        if idx is None:
            rapor.setdefault("uygulanamadi", []).append(
                (anahtar, f"şık yok: {dogru!r}"))
            continue
        rapor.setdefault("duzeltilen", []).append(
            (hit["soru"], hit.get("dogruMetin"), hit["siklar"][idx]))
        hit["dogruIndeks"] = idx
        hit["dogruMetin"] = hit["siklar"][idx]
        hit["cevapKaynagi"] = "elle"
        # Uygulama elle düzeltilmiş cevabın üstüne yazmaz.
        hit["elleDuzenlendi"] = True

    for anahtar in BOSALT:
        hit = next((r for r in rows if anahtar in norm(r["soru"])), None)
        if hit is None:
            continue
        hit["dogruIndeks"] = hit["dogruMetin"] = hit["cevapKaynagi"] = None
        hit["elleDuzenlendi"] = False
        rapor.setdefault("bosaltilan", []).append(hit["soru"])
    return rows


def main(girdi, cikti):
    ham = json.load(open(girdi, encoding="utf-8"))
    rows = ham["sorular"] if isinstance(ham, dict) else ham
    basta = len(rows)
    rapor = {}

    rows = onar(rows, rapor)
    grp = gruplar(rows)
    icinde = {i for g in grp for i in g}
    rows = [birlestir([rows[i] for i in g]) for g in grp] + \
           [r for i, r in enumerate(rows) if i not in icinde]
    rows.sort(key=lambda r: r.get("tarih", 0) or 0)

    rows, yarim = yarim_olanlari_at(rows)
    rows = [r for r in rows if not any(norm(s) in norm(r["soru"]) for s in SIL)]
    rows = duzelt(rows, rapor)

    out = []
    for r in rows:
        o = {k: r.get(k) for k in ALAN}
        ce = o.get("cevapladigin") or 0
        o["basariYuzde"] = ((o.get("dogruBildigin") or 0) * 100 // ce) if ce else None
        out.append(o)

    json.dump({"olusturma": int(time.time() * 1000), "adet": len(out),
               "sorular": out},
              open(cikti, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    cevapli = sum(1 for r in out if r["dogruIndeks"] is not None)
    print(f"{basta} -> {len(out)} soru")
    print(f"  bozuk kayıt silindi   : {len(rapor.get('silinen', []))}")
    print(f"  tekrar birleştirildi  : {sum(len(g) - 1 for g in grp)}")
    print(f"  yarım kayıt atıldı    : {yarim}")
    print(f"  cevabı elle düzeltildi: {len(rapor.get('duzeltilen', []))}")
    print(f"  cevabı bilinen        : {cevapli} · eksik {len(out) - cevapli}")
    for a, n in rapor.get("uygulanamadi", []):
        print(f"  !! düzeltme uygulanamadı: {a} ({n})")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
