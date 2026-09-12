import React, { useState } from 'react';
import { BotSettings } from '../types';
import { Play, Touchpad, Clock, RotateCcw, Check, Sparkles, Shield, AlertTriangle } from 'lucide-react';

interface SettingsTabProps {
  settings: BotSettings;
  onUpdateSettings: (newSettings: Partial<BotSettings>) => void;
}

export const SettingsTab: React.FC<SettingsTabProps> = ({
  settings,
  onUpdateSettings
}) => {
  const [delayInput, setDelayInput] = useState(settings.clickDelayMs.toString());
  const [saveSuccess, setSaveSuccess] = useState(false);

  const handleDelayChange = (val: string) => {
    setDelayInput(val);
    const num = parseInt(val, 10);
    if (!isNaN(num) && num >= 200 && num <= 10000) {
      onUpdateSettings({ clickDelayMs: num });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 1500);
    }
  };

  const setPreset = (ms: number) => {
    setDelayInput(ms.toString());
    onUpdateSettings({ clickDelayMs: ms });
    setSaveSuccess(true);
    setTimeout(() => setSaveSuccess(false), 1500);
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-xl font-bold text-slate-800">Bot ve Oyun Ayarları</h2>
        <p className="text-sm text-slate-500">
          Otomatik/Manuel mod tercihleri, tıklama bekleme süresi ve oyun sonu davranışlarını özelleştirin.
        </p>
      </div>

      {/* 1. ÇALIŞMA MODU (Manuel vs Otomatik) */}
      <div className="bg-white rounded-3xl p-6 border border-slate-200 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">
              1. Çalışma Modu
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Uygulamanın ekrana müdahale edip etmeyeceğini belirler.
            </p>
          </div>
          <span className={`text-xs font-bold px-3 py-1 rounded-full ${
            settings.mode === 'auto' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700'
          }`}>
            {settings.mode === 'auto' ? 'Otomatik Aktif' : 'Manuel Aktif'}
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
          {/* Manuel Mode Card */}
          <div
            id="card-mode-manual"
            onClick={() => onUpdateSettings({ mode: 'manual' })}
            className={`p-5 rounded-2xl border-2 cursor-pointer transition-all ${
              settings.mode === 'manual'
                ? 'border-purple-600 bg-purple-50/50 shadow-md ring-1 ring-purple-600/30'
                : 'border-slate-200 hover:border-slate-300 bg-white'
            }`}
          >
            <div className="flex items-center justify-between mb-3">
              <div className="w-10 h-10 rounded-xl bg-purple-100 text-purple-700 flex items-center justify-center font-bold">
                <Touchpad className="w-5 h-5" />
              </div>
              {settings.mode === 'manual' && (
                <span className="w-5 h-5 rounded-full bg-purple-600 text-white flex items-center justify-center text-xs">
                  ✓
                </span>
              )}
            </div>
            <h4 className="font-bold text-slate-800 text-base">Manuel Mod</h4>
            <p className="text-xs text-slate-600 mt-1 leading-relaxed">
              Bot ekrana <strong>hiçbir tıklama yapmaz</strong>. Siz oyunu kendiniz oynarken soruları, şıkları ve yeşil yanan doğru cevapları arka planda otomatik veritabanına kaydeder.
            </p>
          </div>

          {/* Otomatik Mode Card */}
          <div
            id="card-mode-auto"
            onClick={() => onUpdateSettings({ mode: 'auto' })}
            className={`p-5 rounded-2xl border-2 cursor-pointer transition-all ${
              settings.mode === 'auto'
                ? 'border-emerald-600 bg-emerald-50/50 shadow-md ring-1 ring-emerald-600/30'
                : 'border-slate-200 hover:border-slate-300 bg-white'
            }`}
          >
            <div className="flex items-center justify-between mb-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold">
                <Play className="w-5 h-5" />
              </div>
              {settings.mode === 'auto' && (
                <span className="w-5 h-5 rounded-full bg-emerald-600 text-white flex items-center justify-center text-xs">
                  ✓
                </span>
              )}
            </div>
            <h4 className="font-bold text-slate-800 text-base">Otomatik Bot Modu</h4>
            <p className="text-xs text-slate-600 mt-1 leading-relaxed">
              Daha önce arşivlenen sorularda <strong>doğru cevabı otomatik tıklar</strong>. Yeni sorularda rastgele bir şık seçip yeşil/kırmızı renkten doğru cevabı öğrenir.
            </p>
          </div>
        </div>
      </div>

      {/* 2. TIKLAMA GECİKMESİ (EL İLE AYARLANABİLİR) */}
      <div className="bg-white rounded-3xl p-6 border border-slate-200 shadow-sm space-y-4">
        <div>
          <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">
            2. Tıklama Gecikmesi (Gecikme Süresi)
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Soru ekranda belirdikten sonra botun şıkkı tıklamadan önce bekleyeceği süreyi elinizle girin.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-center gap-3 pt-2">
          <div className="relative flex-1 w-full">
            <input
              id="input-delay-ms"
              type="number"
              min={200}
              max={10000}
              step={100}
              value={delayInput}
              onChange={e => handleDelayChange(e.target.value)}
              className="w-full pl-4 pr-14 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-slate-800 font-bold text-base focus:ring-2 focus:ring-purple-500 focus:outline-none"
              placeholder="1200"
            />
            <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-bold text-slate-400">
              ms
            </span>
          </div>

          {saveSuccess && (
            <span className="text-xs text-emerald-600 font-bold flex items-center gap-1">
              <Check className="w-4 h-4" /> Kaydedildi
            </span>
          )}
        </div>

        {/* Hazır Hızlı Seçim Butonları */}
        <div className="pt-2">
          <span className="text-xs font-semibold text-slate-500 mb-2 block">
            Hızlı Hazır Şablonlar:
          </span>
          <div className="grid grid-cols-3 gap-2">
            {[
              { ms: 500, label: '⚡ Hızlı', desc: '0.5 sn' },
              { ms: 1200, label: '⚖️ Normal', desc: '1.2 sn (Önerilen)' },
              { ms: 2500, label: '🧘 İnsansı / Güvenli', desc: '2.5 sn' },
            ].map(preset => (
              <button
                key={preset.ms}
                onClick={() => setPreset(preset.ms)}
                className={`p-2.5 rounded-xl border text-center transition-all cursor-pointer ${
                  settings.clickDelayMs === preset.ms
                    ? 'border-purple-600 bg-purple-50 text-purple-900 font-bold shadow-sm'
                    : 'border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-700'
                }`}
              >
                <div className="text-xs font-bold">{preset.label}</div>
                <div className="text-[11px] text-slate-500 mt-0.5">{preset.desc}</div>
              </button>
            ))}
          </div>
        </div>

        <div className="p-3 bg-slate-50 rounded-xl text-xs text-slate-500 flex items-start gap-2">
          <Clock className="w-4 h-4 text-purple-600 shrink-0 mt-0.5" />
          <span>
            Not: 800 - 1500 ms arası gecikme, oyun sunucusunun ve animasyonlarının soru kartını tam çizmesi ve anti-otomasyon korumalarını aşması için idealdir.
          </span>
        </div>
      </div>

      {/* 3. OYUN SONU VE 'YENİ OYUN' TEKRAR DÖNGÜSÜ */}
      <div className="bg-white rounded-3xl p-6 border border-slate-200 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">
              3. Oyun Sonu ve Otomatik Tekrar Döngüsü
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              7 soru bitip "108-84 Tebrikler kazandınız" ekranı geldiğinde sıradaki oyunu otomatik başlatın.
            </p>
          </div>
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              id="switch-auto-restart"
              type="checkbox"
              checked={settings.autoRestartGame}
              onChange={e => onUpdateSettings({ autoRestartGame: e.target.checked })}
              className="sr-only peer"
            />
            <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
          </label>
        </div>

        <div className="p-4 rounded-2xl bg-emerald-50/60 border border-emerald-200/80 text-xs text-emerald-950 space-y-2">
          <div className="flex items-center gap-2 font-bold text-emerald-800">
            <RotateCcw className="w-4 h-4" />
            <span>Kesintisiz Soru Arşivleme Döngüsü</span>
          </div>
          <p className="leading-relaxed">
            Bu özellik aktifken, maç sonu ekranındaki <strong>"Yeni Oyun"</strong> butonu erişilebilirlik servisi tarafından otomatik tespit edilir ve belirlediğiniz gecikme süresiyle tıklanır. Böylece siz oyuna hiç dokunmasanız bile yüzlerce soru arşive eklenmeye devam eder.
          </p>
        </div>
      </div>

      {/* 4. HAZIR PROJEYİ İNDİR (ZIP) */}
      <div className="bg-gradient-to-br from-slate-900 to-indigo-950 text-white rounded-3xl p-6 border border-slate-800 shadow-xl space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
              TAM PROJE PAKETİ
            </span>
            <h3 className="text-base font-bold text-white mt-1">
              SoruArsivi.zip (Güncel Android Studio Projesi)
            </h3>
            <p className="text-xs text-slate-300 mt-0.5">
              Tüm kodlar, ayarlar ve erişilebilirlik servisi yapılandırılmış hazır zip arşivi.
            </p>
          </div>

          <a
            href="/SoruArsivi_Bot_Guncel.zip"
            download="SoruArsivi_Bot_Guncel.zip"
            className="px-5 py-3 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold rounded-2xl text-xs flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20 transition-all hover:scale-105 active:scale-95 shrink-0"
          >
            <span>SoruArsivi.zip İndir</span>
          </a>
        </div>
      </div>
    </div>
  );
};
