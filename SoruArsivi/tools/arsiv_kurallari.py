# -*- coding: utf-8 -*-
"""Soru Arşivi yedeğini temizler.

Her kural, hangi gerçek bozulmadan doğduğu ile birlikte yazıldı. Kural
yazarken tek tehlike fazla agresif olmak: "100" tek başına bir joker
rozeti olabildiği gibi bir sayı sorusunun gerçek şıkkı da olabiliyor.
Bu yüzden hiçbir sayı "sayı olduğu için" atılmıyor; ancak aynı kayıtta
kanıtlanmış bir joker sızıntısı varsa ya da kelime şıkları sayıları
açıkça bastırıyorsa atılıyor.
"""
import re

TR = str.maketrans("ıİğĞüÜşŞöÖçÇâîûáéèëíìóòúùä", "iigguussooccaiuaeeeiioouua")

def norm(s):
    """Karşılaştırma anahtarı: harf ve rakam dışı her şey silinir.
    Uygulamadaki TurkishText.normalizeKey ile aynı sonucu vermeli."""
    return re.sub(r"[^a-z0-9]", "", (s or "").translate(TR).lower())

def soft(s):
    """Şık karşılaştırması: yalnızca boşluk/büyük-küçük normalleşir.
    Simgeler korunur, yoksa '<' '>' '=' şıkları aynı sanılıyor."""
    return re.sub(r"\s+", " ", (s or "").translate(TR).lower()).strip()

def lev(a, b):
    if a == b: return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(cur[-1] + 1, prev[j] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]

def sim(a, b):
    """Uygulamadaki TurkishText.similarity'nin birebir karşılığı."""
    x, y = norm(a), norm(b)
    if not x and not y: return 1.0
    if not x or not y: return 0.0
    if x == y: return 1.0
    m = max(len(x), len(y))
    if min(len(x), len(y)) / m < 0.6: return 0.0
    return 1 - lev(x, y) / m

NEG = [("değil", 1), ("yanlış", 2), ("olmayan", 4), ("hariç", 8),
       ("dışında", 16), ("söylenemez", 32)]

def negation(s):
    low = (s or "").lower()
    sig = 0
    for word, bit in NEG:
        if word in low: sig |= bit
    return sig

def match_index(options, text):
    """TurkishText.matchIndex: önce birebir, sonra >=0.85 benzerlik."""
    if not text or not options: return None
    key = norm(text)
    if not key: return None
    for i, o in enumerate(options):
        if norm(o) == key: return i
    best, best_sim = -1, 0.0
    for i, o in enumerate(options):
        s = sim(o, text)
        if s > best_sim: best, best_sim = i, s
    return best if best >= 0 and best_sim >= 0.85 else None

# --- soru metnine yapışan arayüz parçaları ---------------------------------
# Süre balonu ("4,sn"), soru numarası ("(6s)"), puan balonu ("+5") ve
# kombo banner'ı OCR tarafından soruyla aynı blokta döndürülüyor.
PREFIX = re.compile(
    r"^\s*(?:ar\s*)?\d{0,3}\s*[,._]?\s*sn\b[\s.,)]*(?:\d{1,3}\b[\s.,)]*)?"  # "4,sn", "Arsn 33"
    r"|^\s*\(\s*\d{1,3}\s*[sa]?\s*\)?\s+"                 # "(6s)", "(73 " (kapanmamış)
    r"|^\s*\d{1,3}\s*sn\s*\(\s*\d+\s*"                    # "4 sn (73"
    r"|^\s*(?:kombo|combo|muhteşem|muhtesem|tebrikler|süper|super|seri"
    r"|çok\s*yaklaştın|cok\s*yaklastin|biraz\s*daha\s*gayret"
    r"|süre\s*bitti|sure\s*bitti|süre\s*doldu|sure\s*doldu)\s*[!.,]*\s*",
    re.I)
# Sondaki puan balonu. Lookbehind şart: "Sonuç kaçtır: 2 + 2" gibi bir
# soruda "+ 2" gerçekten sorunun parçası.
SUFFIX = re.compile(r"(?<=[^0-9\s])[\s(]*[+\-±]\s*\d{1,4}\s*[)!.,:;]*\s*$")

def fix_question(q):
    t = (q or "").strip()
    # Baştaki joker/puan/süre rozetleri arka arkaya gelebiliyor:
    # "X2 (57) +4 Sn Hangi filozofu..." — hepsini tek tek soyuyoruz.
    t = re.sub(r"^(?:\s*(?:[xX]\s*2|2\s*[xX]|[+±]\s*\d{1,3}|\(\s*\d{1,3}\s*\)"
               r"|\d{1,3}\s*[,._]?\s*[sS][nN]\b|50\s*[:,]?\s*50))+\s*", "", t)
    # Sondaki "TO", "5 +5" gibi artıklar.
    t = re.sub(r"[\s(]*(?:\d{1,3}\s*)?[+±]\s*\d{1,4}\s*[)!.,:;]*\s*$", "", t) \
        if re.search(r"[?!.][^?!.]{0,8}$", t) else t
    t = re.sub(r"(?<=[?!.])\s*[A-Z]{1,2}\s*$", "", t)
    # Baştaki tek başına duran 1-2 harflik büyük harf artığı ("LO Dünya'nın").
    # Gerçek sorular kısaltmayla başlamıyor; "M.Ö." gibi olanlarda nokta var.
    t = re.sub(r"^[A-Z]{1,2}\s+(?=[A-ZÇĞİÖŞÜ])", "", t)
    for _ in range(6):
        before = t
        t = PREFIX.sub("", t).strip()
        t = SUFFIX.sub("", t).strip()
        t = re.sub(r"^\d{1,3}\s*[.)]\s+", "", t).strip()   # "17. " soru numarası
        # "…hangisidir? s+10" -> "…hangisidir? s" -> "…hangisidir?"
        t = re.sub(r"(?<=[?.!])\s*[A-Za-zçğıöşü]\s*$", "", t)
        t = t.strip(" \t·•*_-—–\"'")
        if t == before: break
    return re.sub(r"\s{2,}", " ", t).strip()

# --- şık temizliği ---------------------------------------------------------
# Ekranda joker düğmeleri (50:50, çift cevap, soru değiştir) ve puan
# balonları ("+5") şıklarla aynı hizada duruyor; OCR onları şık sanıyor.
ARTIFACT = {"50,", ",50", "50 50", "50, 50", "50,50", "x2", "2x", "+5", "+10",
            "+ 5", "+ 10", "5+5", "5 +5", "+5 5", "1 dönem", "1 donem"}

BARE = re.compile(r"^[+\-]?\d{1,4}(?:[.,]\d{1,3})?[.,]?$")

def bare_number(o):
    return BARE.fullmatch((o or "").strip()) is not None

def clean_options(raw):
    """Şıkları temizler; (temiz_liste, atılanlar) döner.

    Sıra üç adım:
      1. Kesin arayüz parçaları ("50,", "x2", "+5") atılır.
      2. Birebir tekrar eden şıklar teke iner — OCR aynı rozeti iki kez
         okumuş demektir ("200", "200").
      3. Bu ikisinden biri iş gördüyse kayıtta joker sızıntısı kanıtlanmış
         olur; geriye kalan çıplak sayılar da atılır. Kanıt yoksa sayılar
         ancak kelime şıkları onları sayıca bastırıyorsa atılır — böylece
         ['19','124','Hiçbiri','7'] gibi gerçek bir sayı sorusu kurtulur.
    """
    opts = [re.sub(r"\s{2,}", " ", (o or "").strip()) for o in raw]
    opts = [o for o in opts if o]
    dropped = []

    kept = []
    for o in opts:
        if soft(o) in ARTIFACT:
            dropped.append(o)
        else:
            kept.append(o)
    contaminated = bool(dropped)

    seen, uniq = set(), []
    for o in kept:
        k = soft(o)
        if k in seen:
            dropped.append(o)
            contaminated = True
        else:
            seen.add(k)
            uniq.append(o)

    nums = [o for o in uniq if bare_number(o)]
    words = [o for o in uniq if not bare_number(o)]
    if nums and words and (contaminated or len(words) > len(nums)):
        dropped.extend(nums)
        uniq = words

    # Tek karakterlik ve harf/rakam içermeyen artıklar.
    final = []
    for o in uniq:
        if len(o) == 1 and not o.isalnum():
            dropped.append(o)
        else:
            final.append(o)
    return final, dropped

# --- soru metni yaşayabilir mi ---------------------------------------------
UI_TEXT = re.compile(r"oyundan ayrılmak|oyundan ayrilmak|emin misin|reklam izle"
                     r"|bilme oranı|bilme orani", re.I)

def question_broken(q):
    if len(q) < 12:
        return "soru metni çok kısa"
    if not re.search(r"[A-Za-zÇĞİÖŞÜçğıöşü]{4}", q):
        return "soru metninde sözcük yok"
    if UI_TEXT.search(q):
        return "oyun arayüz metni, soru değil"
    return None
