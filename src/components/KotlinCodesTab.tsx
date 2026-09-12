import React, { useState } from 'react';
import { kotlinFiles } from '../data/kotlinCode';
import { Copy, Check, FileCode, ExternalLink, Terminal, Download } from 'lucide-react';

export const KotlinCodesTab: React.FC = () => {
  const [selectedFileIdx, setSelectedFileIdx] = useState(0);
  const [copied, setCopied] = useState(false);

  const currentFile = kotlinFiles[selectedFileIdx];

  const handleCopy = () => {
    navigator.clipboard.writeText(currentFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Android Kaynak Kodları &amp; Hazır Proje (ZIP)</h2>
          <p className="text-sm text-slate-500">
            Otomatik oynama, gecikme ayarı ve "Yeni Oyun" döngüsü ile güncellenmiş komple Android Studio projesi.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <a
            href="/SoruArsivi_Bot_Guncel.zip"
            download="SoruArsivi_Bot_Guncel.zip"
            className="px-4 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-md transition-all hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
          >
            <Download className="w-4 h-4 text-emerald-200" />
            <span>Hazır Projeyi İndir (.ZIP)</span>
          </a>

          <button
            onClick={handleCopy}
            className="px-4 py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-sm transition-colors cursor-pointer"
          >
            {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Kopyalandı!' : 'Dosyayı Kopyala'}</span>
          </button>
        </div>
      </div>

      {/* Direct ZIP Download Banner */}
      <div className="bg-gradient-to-r from-emerald-900 via-slate-900 to-purple-950 text-white p-5 rounded-3xl border border-emerald-500/30 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
              TAM SÜRÜM v2.0
            </span>
            <span className="text-xs text-slate-300 font-mono">SoruArsivi_Bot_Guncel.zip</span>
          </div>
          <h3 className="text-base font-bold text-white">
            Tüm Değişiklikler Yapılmış Eksiksiz Android Studio Projesi
          </h3>
          <p className="text-xs text-slate-300 max-w-2xl leading-relaxed">
            Manuel &amp; Otomatik mod, gecikme süresi (ms) ayarları, çift katmanlı tıklama motoru ve oyun sonundaki "Yeni Oyun" döngüsü projeye entegre edilmiştir. İndirip Android Studio'da doğrudan açabilir ve derleyebilirsiniz.
          </p>
        </div>

        <a
          href="/SoruArsivi_Bot_Guncel.zip"
          download="SoruArsivi_Bot_Guncel.zip"
          className="shrink-0 px-5 py-3 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold rounded-2xl text-xs flex items-center gap-2.5 shadow-lg shadow-emerald-500/20 transition-all hover:scale-105 active:scale-95"
        >
          <Download className="w-4 h-4 text-slate-950" />
          <span>SoruArsivi.zip İndir</span>
        </a>
      </div>

      {/* File Navigation Pills */}
      <div className="flex flex-wrap gap-2">
        {kotlinFiles.map((file, idx) => (
          <button
            key={file.fileName}
            onClick={() => {
              setSelectedFileIdx(idx);
              setCopied(false);
            }}
            className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all cursor-pointer ${
              selectedFileIdx === idx
                ? 'bg-slate-900 text-white shadow-md'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <FileCode className="w-3.5 h-3.5 text-purple-400" />
            <span>{file.fileName}</span>
          </button>
        ))}
      </div>

      {/* File Info Card */}
      <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
        <div>
          <span className="font-semibold text-slate-500">Dosya Yolu: </span>
          <code className="font-mono text-purple-700 bg-purple-50 px-2 py-0.5 rounded">
            {currentFile.path}
          </code>
        </div>
        <p className="text-slate-600 italic">
          {currentFile.description}
        </p>
      </div>

      {/* Code Viewer */}
      <div className="relative rounded-3xl bg-slate-950 border border-slate-800 shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 bg-slate-900/80 border-b border-slate-800 text-xs text-slate-400">
          <div className="flex items-center gap-2">
            <span className="w-3 h-3 rounded-full bg-red-500/80" />
            <span className="w-3 h-3 rounded-full bg-yellow-500/80" />
            <span className="w-3 h-3 rounded-full bg-green-500/80" />
            <span className="ml-2 font-mono text-slate-300 font-semibold">{currentFile.fileName}</span>
          </div>
          <button
            onClick={handleCopy}
            className="hover:text-white transition-colors flex items-center gap-1.5"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copied ? 'Kopyalandı' : 'Kopyala'}</span>
          </button>
        </div>

        <pre className="p-5 font-mono text-xs leading-relaxed text-slate-200 overflow-x-auto max-h-[560px] scrollbar-thin scrollbar-thumb-slate-700">
          <code>{currentFile.code}</code>
        </pre>
      </div>

      {/* Quick Setup Instructions */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-purple-50/70 rounded-3xl p-5 border border-purple-200 text-xs text-purple-950 space-y-2">
          <h4 className="font-bold text-sm text-purple-900 flex items-center gap-2">
            <Terminal className="w-4 h-4" />
            Android Studio'da Açma ve Derleme
          </h4>
          <ol className="list-decimal list-inside space-y-1.5 text-slate-700 leading-relaxed">
            <li>İndirdiğiniz <strong>SoruArsivi_Bot_Guncel.zip</strong> arşivini bir klasöre çıkartın.</li>
            <li>Android Studio &gt; <strong>Open</strong> seçeneği ile bu klasörü açın.</li>
            <li>Gradle senkronizasyonu bitince <strong>Build &gt; Build APK(s)</strong> diyerek telefonunuza yükleyebilirsiniz.</li>
            <li>Telefonunuzun <strong>Ayarlar &gt; Erişilebilirlik</strong> menüsünden servisi aktif edin.</li>
          </ol>
        </div>

        <div className="bg-emerald-50/80 rounded-3xl p-5 border border-emerald-200 text-xs text-emerald-950 space-y-2">
          <h4 className="font-bold text-sm text-emerald-900 flex items-center gap-2">
            <ExternalLink className="w-4 h-4" />
            GitHub Actions ile Otomatik APK Alma (Neden Çalışmadı?)
          </h4>
          <div className="text-slate-700 space-y-1.5 leading-relaxed">
            <p>
              GitHub Actions'ın tetiklenmesi için repoda <strong>.github/workflows/build-apk.yml</strong> dosyasının ve <strong>gradlew</strong> çalıştırıcısının bulunması zorunludur.
            </p>
            <p className="font-semibold text-emerald-950">
              Şimdi güncel ZIP içerisine bu dosyalar eklendi:
            </p>
            <ol className="list-decimal list-inside space-y-1 text-slate-700">
              <li>ZIP'ten çıkan tüm içeriği (özellikle <strong>.github</strong> klasörünü) GitHub reponuza pushlayın.</li>
              <li>GitHub sayfanızda <strong>Actions</strong> sekmesine gelin.</li>
              <li><strong>TRT Bil Bakalim Bot - APK Derle</strong> iş akışının başladığını göreceksiniz.</li>
              <li>Bittiğinde <strong>Artifacts</strong> altından hazır <strong>SoruArsivi-Bot-Debug-APK</strong> dosyasını tek tıkla indirin!</li>
            </ol>
          </div>
        </div>
      </div>
    </div>
  );
};
