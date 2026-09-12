import React, { useState } from 'react';
import { BotSettings, Question, BotLog } from './types';
import { TrtSimulator } from './components/TrtSimulator';
import { QuestionsList } from './components/QuestionsList';
import { SettingsTab } from './components/SettingsTab';
import { KotlinCodesTab } from './components/KotlinCodesTab';
import { BotLogs } from './components/BotLogs';
import {
  Gamepad2,
  Database,
  Settings,
  Code2,
  Play,
  Touchpad,
  Clock,
  Sparkles,
  Zap,
  CheckCircle2,
  Download
} from 'lucide-react';

const INITIAL_QUESTIONS: Question[] = [
  {
    id: 'q1',
    questionText: '"Bir dil konuşabilen birinin, diğer insanların var olduğundan şüphe etmesi oldukça anlamsızdır." görüşü kime aittir?',
    options: ['Pascal', 'Wittgenstein', 'Berkeley', 'Kierkegaard'],
    correctAnswer: 'Wittgenstein',
    category: 'Felsefe',
    known: true,
    timesSeen: 2
  },
  {
    id: 'q2',
    questionText: 'Hangi görüşe göre evrendeki her şey birbirine zıt ve tamamlayıcı iki kuvvet tarafından yönetilir?',
    options: ['Feng Shui', 'Ying Yang', 'Zen', 'Dharma'],
    correctAnswer: 'Ying Yang',
    category: 'Felsefe / Uzak Doğu',
    known: true,
    timesSeen: 1
  },
  {
    id: 'q3',
    questionText: 'Türkiye\'nin yüz ölçümü bakımından en büyük gölü hangisidir?',
    options: ['Tuz Gölü', 'Van Gölü', 'Beyşehir Gölü', 'Eğirdir Gölü'],
    correctAnswer: 'Van Gölü',
    category: 'Coğrafya',
    known: false,
    timesSeen: 0
  },
  {
    id: 'q4',
    questionText: 'Güneş Sistemi\'nde güneşe en yakın gezegen hangisidir?',
    options: ['Venüs', 'Merkür', 'Mars', 'Dünya'],
    correctAnswer: 'Merkür',
    category: 'Bilim',
    known: false,
    timesSeen: 0
  }
];

export default function App() {
  const [activeTab, setActiveTab] = useState<'sim' | 'archive' | 'settings' | 'code'>('sim');

  const [settings, setSettings] = useState<BotSettings>({
    mode: 'auto',
    clickDelayMs: 1200,
    autoRestartGame: true,
    autoSolveKnown: true,
    randomGuessUnknown: true
  });

  const [questions, setQuestions] = useState<Question[]>(INITIAL_QUESTIONS);

  const [logs, setLogs] = useState<BotLog[]>([
    {
      id: 'init-1',
      timestamp: new Date().toLocaleTimeString(),
      type: 'info',
      message: 'Soru Arşivi & Bot Yöneticisi başlatıldı. Erişilebilirlik servisi hazır.'
    },
    {
      id: 'init-2',
      timestamp: new Date().toLocaleTimeString(),
      type: 'info',
      message: 'TRT Bil Bakalım paketi izleme listesinde. Otomatik bot modu aktif (1200ms gecikme).'
    }
  ]);

  const addLog = (log: Omit<BotLog, 'id' | 'timestamp'>) => {
    const newLog: BotLog = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString(),
      ...log
    };
    setLogs(prev => [newLog, ...prev.slice(0, 49)]);
  };

  const handleLearnQuestion = (questionId: string, correctAnswer: string) => {
    setQuestions(prev =>
      prev.map(q => {
        if (q.id === questionId) {
          return {
            ...q,
            correctAnswer,
            known: true,
            timesSeen: q.timesSeen + 1
          };
        }
        return q;
      })
    );
  };

  const updateSettings = (newSettings: Partial<BotSettings>) => {
    setSettings(prev => {
      const updated = { ...prev, ...newSettings };
      if (newSettings.mode) {
        addLog({
          type: 'info',
          message: `Çalışma modu değiştirildi: ${newSettings.mode === 'auto' ? '⚡ Otomatik Bot' : '🖐️ Manuel Mod'}`
        });
      }
      if (newSettings.clickDelayMs) {
        addLog({
          type: 'info',
          message: `Tıklama gecikmesi güncellendi: ${newSettings.clickDelayMs} ms`
        });
      }
      return updated;
    });
  };

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 font-sans flex flex-col justify-between">
      {/* Top Navbar */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-50">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-purple-700 to-indigo-600 flex items-center justify-center text-white shadow-md shadow-purple-500/20">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-extrabold text-slate-800 tracking-tight">
                  Soru Arşivi &amp; Bot
                </h1>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-purple-100 text-purple-800">
                  TRT Bil Bakalım
                </span>
              </div>
              <p className="text-[11px] text-slate-500">
                Otomatik Soru Kaydedici &amp; Oynama Botu
              </p>
            </div>
          </div>

          {/* Quick Mode Toggle in Header */}
          <div className="flex items-center gap-2">
            <div className="hidden sm:flex items-center bg-slate-100 p-1 rounded-2xl border border-slate-200">
              <button
                id="header-mode-manual"
                onClick={() => updateSettings({ mode: 'manual' })}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                  settings.mode === 'manual'
                    ? 'bg-white text-slate-800 shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                <Touchpad className="w-3.5 h-3.5" />
                <span>Manuel</span>
              </button>

              <button
                id="header-mode-auto"
                onClick={() => updateSettings({ mode: 'auto' })}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                  settings.mode === 'auto'
                    ? 'bg-emerald-600 text-white shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                <Play className="w-3.5 h-3.5" />
                <span>Otomatik Bot</span>
              </button>
            </div>

            <div className="hidden md:flex items-center gap-1 text-xs font-mono text-slate-500 bg-slate-50 px-2.5 py-1.5 rounded-xl border border-slate-200">
              <Clock className="w-3.5 h-3.5 text-purple-600" />
              <span>{settings.clickDelayMs}ms</span>
            </div>

            <a
              id="header-download-zip"
              href="/SoruArsivi_Bot_Guncel.zip"
              download="SoruArsivi_Bot_Guncel.zip"
              className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-sm transition-all hover:scale-105 active:scale-95 cursor-pointer"
              title="Güncel Android Studio Projesini İndir"
            >
              <Download className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Projeyi İndir (.ZIP)</span>
              <span className="sm:hidden">ZIP</span>
            </a>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="max-w-6xl mx-auto px-4 sm:px-6 flex gap-1 border-t border-slate-100 overflow-x-auto scrollbar-none">
          <button
            id="tab-sim"
            onClick={() => setActiveTab('sim')}
            className={`py-3 px-4 text-xs font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'sim'
                ? 'border-purple-600 text-purple-700 bg-purple-50/50'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Gamepad2 className="w-4 h-4" />
            <span>Canlı Simülatör &amp; Bot</span>
          </button>

          <button
            id="tab-archive"
            onClick={() => setActiveTab('archive')}
            className={`py-3 px-4 text-xs font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'archive'
                ? 'border-purple-600 text-purple-700 bg-purple-50/50'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Database className="w-4 h-4" />
            <span>Soru Arşivi ({questions.length})</span>
          </button>

          <button
            id="tab-settings"
            onClick={() => setActiveTab('settings')}
            className={`py-3 px-4 text-xs font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'settings'
                ? 'border-purple-600 text-purple-700 bg-purple-50/50'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Settings className="w-4 h-4" />
            <span>Bot Ayarları</span>
          </button>

          <button
            id="tab-code"
            onClick={() => setActiveTab('code')}
            className={`py-3 px-4 text-xs font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'code'
                ? 'border-purple-600 text-purple-700 bg-purple-50/50'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Code2 className="w-4 h-4 text-emerald-600" />
            <span className="text-emerald-700">Android Kaynak Kodları (Kotlin)</span>
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-8 flex-1 w-full space-y-8">
        {activeTab === 'sim' && (
          <TrtSimulator
            settings={settings}
            questions={questions}
            onLearnQuestion={handleLearnQuestion}
            onAddLog={addLog}
          />
        )}

        {activeTab === 'archive' && (
          <QuestionsList questions={questions} />
        )}

        {activeTab === 'settings' && (
          <SettingsTab
            settings={settings}
            onUpdateSettings={updateSettings}
          />
        )}

        {activeTab === 'code' && (
          <KotlinCodesTab />
        )}

        {/* Real-time Logs Console Component at bottom */}
        <BotLogs logs={logs} onClearLogs={() => setLogs([])} />
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-slate-200 py-4 text-center text-xs text-slate-500">
        <p>
          Soru Arşivi &amp; Otomatik Oynama Botu &bull; TRT Bil Bakalım için Erişilebilirlik &amp; Room SQLite Tabanlı Çözüm
        </p>
      </footer>
    </div>
  );
}
