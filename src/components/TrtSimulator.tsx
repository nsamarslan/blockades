import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  RotateCcw,
  Home,
  Play,
  CheckCircle2,
  XCircle,
  Clock,
  Sparkles,
  Zap,
  HelpCircle,
  Award,
  ChevronRight,
  ShieldCheck,
  Star,
  Coins,
  Heart
} from 'lucide-react';
import { Question, BotSettings, BotLog } from '../types';

interface TrtSimulatorProps {
  settings: BotSettings;
  questions: Question[];
  onLearnQuestion: (questionId: string, correctAnswer: string) => void;
  onAddLog: (log: Omit<BotLog, 'id' | 'timestamp'>) => void;
}

const SAMPLE_MATCH_QUESTIONS: Question[] = [
  {
    id: 'q1',
    questionText: '"Bir dil konuşabilen birinin, diğer insanların var olduğundan şüphe etmesi oldukça anlamsızdır." görüşü kime aittir?',
    options: ['Pascal', 'Wittgenstein', 'Berkeley', 'Kierkegaard'],
    correctAnswer: 'Wittgenstein',
    category: 'Felsefe',
    known: true,
    timesSeen: 1
  },
  {
    id: 'q2',
    questionText: 'Hangi görüşe göre evrendeki her şey birbirine zıt ve tamamlayıcı iki kuvvet tarafından yönetilir?',
    options: ['Feng Shui', 'Ying Yang', 'Zen', 'Dharma'],
    correctAnswer: 'Ying Yang',
    category: 'Felsefe / Uzak Doğu',
    known: false,
    timesSeen: 0
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

export const TrtSimulator: React.FC<TrtSimulatorProps> = ({
  settings,
  questions,
  onLearnQuestion,
  onAddLog
}) => {
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [selectedOption, setSelectedOption] = useState<string | null>(null);
  const [isGameOver, setIsGameOver] = useState(false);
  const [isAutoWaiting, setIsAutoWaiting] = useState(false);
  const [matchScore, setMatchScore] = useState({ user: 108, opponent: 84 });
  const [gameCycleCount, setGameCycleCount] = useState(1);

  const currentQ = SAMPLE_MATCH_QUESTIONS[currentQuestionIndex];
  // Check if current question is known in our database
  const dbMatch = questions.find(q => q.questionText === currentQ?.questionText);
  const isCurrentlyKnownInDB = Boolean(dbMatch?.correctAnswer);

  // Auto-play bot engine
  useEffect(() => {
    if (settings.mode !== 'auto') return;

    if (isGameOver) {
      if (settings.autoRestartGame) {
        onAddLog({
          type: 'info',
          message: `Oyun bitti ("Tebrikler kazandınız" 108-84). Bot ${settings.clickDelayMs}ms sonra "Yeni Oyun" butonuna basacak...`
        });
        setIsAutoWaiting(true);

        const restartTimer = setTimeout(() => {
          onAddLog({
            type: 'click',
            message: `⚡ [BOT TIKLADI]: "Yeni Oyun" butonuna başarıyla basıldı! Yeni maç başlatılıyor...`
          });
          setIsGameOver(false);
          setCurrentQuestionIndex(0);
          setSelectedOption(null);
          setGameCycleCount(prev => prev + 1);
          setIsAutoWaiting(false);
        }, settings.clickDelayMs);

        return () => clearTimeout(restartTimer);
      }
      return;
    }

    if (!selectedOption && currentQ) {
      setIsAutoWaiting(true);

      const answerTimer = setTimeout(() => {
        let optionToClick: string;
        const knownAnswer = dbMatch?.correctAnswer;

        if (knownAnswer && currentQ.options.includes(knownAnswer)) {
          optionToClick = knownAnswer;
          onAddLog({
            type: 'success',
            message: `🎯 [BİLİNEN SORU]: "${currentQ.questionText.slice(0, 32)}..." -> Veritabanındaki doğru şık seçildi: "${optionToClick}"`
          });
        } else {
          // Rastgele bir şık seç
          const randomIdx = Math.floor(Math.random() * currentQ.options.length);
          optionToClick = currentQ.options[randomIdx];
          onAddLog({
            type: 'warning',
            message: `🎲 [YENİ SORU]: Arşivde doğru cevap henüz yok. Rastgele tıklandı: "${optionToClick}"`
          });
        }

        handleOptionClick(optionToClick, true);
        setIsAutoWaiting(false);
      }, settings.clickDelayMs);

      return () => clearTimeout(answerTimer);
    }
  }, [currentQuestionIndex, isGameOver, selectedOption, settings.mode, settings.clickDelayMs, settings.autoRestartGame]);

  const handleOptionClick = (option: string, isBot = false) => {
    if (selectedOption || isGameOver) return;
    setSelectedOption(option);

    const isCorrect = option === currentQ.correctAnswer;

    if (isCorrect) {
      onAddLog({
        type: 'success',
        message: `✅ Şık yeşil yandı! Doğru cevap: "${option}"`
      });
      if (!isCurrentlyKnownInDB && currentQ.correctAnswer) {
        onLearnQuestion(currentQ.id, currentQ.correctAnswer);
        onAddLog({
          type: 'learn',
          message: `🧠 [VERİTABANI GÜNCELLENDİ]: Soru ve doğru cevabı arşive kaydedildi!`
        });
      }
    } else {
      onAddLog({
        type: 'warning',
        message: `❌ Tıklanan şık kırmızı yandı ("${option}"). Ekrandaki YEŞİL şık ("${currentQ.correctAnswer}") otomatik tespit edildi!`
      });
      if (currentQ.correctAnswer) {
        onLearnQuestion(currentQ.id, currentQ.correctAnswer);
        onAddLog({
          type: 'learn',
          message: `🧠 [RENK ALGISI İLE ÖĞRENİLDİ]: Yanlış yapılmasına rağmen doğru cevap tespit edilip veritabanına işlendi!`
        });
      }
    }

    // Next question delay
    setTimeout(() => {
      if (currentQuestionIndex < SAMPLE_MATCH_QUESTIONS.length - 1) {
        setCurrentQuestionIndex(prev => prev + 1);
        setSelectedOption(null);
      } else {
        setIsGameOver(true);
        setSelectedOption(null);
      }
    }, 1600);
  };

  return (
    <div className="flex flex-col lg:flex-row gap-6 items-start justify-center">
      {/* Phone Mockup / Frame */}
      <div className="relative w-full max-w-[360px] mx-auto bg-slate-900 rounded-[44px] p-3 shadow-2xl border-4 border-slate-700">
        {/* Notch / Speaker */}
        <div className="absolute top-5 left-1/2 -translate-x-1/2 w-28 h-4 bg-slate-900 rounded-full z-30 flex items-center justify-center">
          <div className="w-10 h-1 bg-slate-700 rounded-full" />
        </div>

        {/* Game Canvas Container */}
        <div className="relative w-full h-[680px] rounded-[36px] overflow-hidden flex flex-col justify-between select-none shadow-inner text-white font-sans bg-gradient-to-b from-[#2e1b69] via-[#43239e] to-[#25135c]">
          {/* TRT Game Top Stats Bar */}
          <div className="pt-7 px-4 pb-2 z-20">
            <div className="flex items-center justify-between">
              {/* Back button */}
              <div className="w-9 h-9 rounded-xl bg-purple-900/60 border border-purple-500/40 flex items-center justify-center text-purple-200">
                <ChevronRight className="w-5 h-5 rotate-180" />
              </div>

              {/* Avatar Center */}
              <div className="relative -mt-1 flex flex-col items-center">
                <div className="w-13 h-13 rounded-full bg-slate-200 border-2 border-slate-400 flex items-center justify-center overflow-hidden shadow-md">
                  <div className="w-8 h-8 rounded-full bg-slate-400/80 -mb-2" />
                </div>
                <div className="absolute -bottom-2 px-2 py-0.5 bg-slate-800 border border-slate-500 text-[10px] font-bold rounded-md">
                  2
                </div>
              </div>

              {/* Gold Coins */}
              <div className="flex items-center gap-1.5 px-3 py-1 bg-purple-950/70 rounded-full border border-yellow-500/40">
                <span className="text-xs font-bold text-yellow-300">1.146</span>
                <Coins className="w-4 h-4 text-yellow-400" />
              </div>
            </div>

            {/* Stars & Hearts sub row */}
            <div className="flex items-center justify-between mt-2 px-1">
              <div className="flex items-center gap-1 px-2.5 py-0.5 bg-purple-900/60 rounded-full text-xs font-bold text-yellow-300 border border-purple-600/40">
                <Star className="w-3.5 h-3.5 fill-yellow-400 text-yellow-400" />
                <span>12</span>
              </div>

              <div className="flex items-center gap-1 px-2.5 py-0.5 bg-purple-900/60 rounded-full text-xs font-bold text-red-300 border border-purple-600/40">
                <Heart className="w-3.5 h-3.5 fill-red-400 text-red-400" />
                <span>10 DOLU</span>
              </div>
            </div>

            {/* 1 - 7 Question Numbers Bar */}
            <div className="flex items-center justify-center gap-1.5 mt-3 px-2">
              {[1, 2, 3, 4, 5, 6, 7].map((num, i) => {
                const isCurrent = i === currentQuestionIndex && !isGameOver;
                const isDone = i < currentQuestionIndex;
                return (
                  <div
                    key={num}
                    className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-all ${
                      isCurrent
                        ? 'bg-amber-100 text-purple-950 shadow-md scale-110 ring-2 ring-amber-300 font-extrabold'
                        : isDone
                        ? 'bg-purple-900/90 text-purple-300 border border-purple-600/50'
                        : 'bg-purple-950/50 text-purple-400/70 border border-purple-800/30'
                    }`}
                  >
                    {num}
                  </div>
                );
              })}
            </div>
          </div>

          {/* MAIN GAME SCREEN CONTENT */}
          <div className="flex-1 px-4 py-2 flex flex-col justify-between z-10 overflow-y-auto">
            <AnimatePresence mode="wait">
              {!isGameOver ? (
                <motion.div
                  key={currentQ.id}
                  initial={{ opacity: 0, y: 15 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  transition={{ duration: 0.25 }}
                  className="flex flex-col h-full justify-between"
                >
                  {/* Top Timer & Category Badge */}
                  <div className="flex items-center justify-between mb-2">
                    <div className="px-2.5 py-1 rounded-lg border border-purple-400/40 bg-purple-900/50 text-[11px] font-bold text-purple-200">
                      {currentQuestionIndex + 1}. Soru
                    </div>
                    <div className="flex items-center gap-1 text-[11px] font-bold text-amber-300 bg-purple-900/60 px-2 py-0.5 rounded-full border border-purple-600/40">
                      <Sparkles className="w-3 h-3 text-amber-400" />
                      <span>{currentQ.category || 'Genel Kültür'}</span>
                    </div>
                    <div className="flex items-center gap-1 text-xs font-bold text-purple-200 bg-purple-900/60 px-2 py-1 rounded-lg border border-purple-500/30">
                      <Clock className="w-3.5 h-3.5 text-red-400" />
                      <span>59s</span>
                    </div>
                  </div>

                  {/* Question Card (White rounded card matching screenshot) */}
                  <div className="bg-white rounded-3xl p-5 text-slate-800 shadow-xl border border-purple-200 text-center relative my-auto">
                    {/* Database Known Badge */}
                    {isCurrentlyKnownInDB && (
                      <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-emerald-600 text-white text-[10px] font-bold px-3 py-0.5 rounded-full shadow flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" />
                        <span>Arşivde Var (Doğru Cevap Biliniyor)</span>
                      </div>
                    )}
                    {!isCurrentlyKnownInDB && (
                      <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-amber-500 text-white text-[10px] font-bold px-3 py-0.5 rounded-full shadow flex items-center gap-1">
                        <HelpCircle className="w-3 h-3" />
                        <span>Yeni Soru (Rastgele Denenecek)</span>
                      </div>
                    )}

                    <p className="text-[14px] leading-relaxed font-semibold text-slate-800 tracking-tight">
                      {currentQ.questionText}
                    </p>

                    <div className="mt-3 flex items-center justify-center gap-1 text-xs font-bold text-amber-500">
                      <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                      <span>+10</span>
                    </div>
                  </div>

                  {/* 4 Options (Matching White Pill Buttons, Green for Correct, Red for False) */}
                  <div className="space-y-2.5 my-auto">
                    {currentQ.options.map((option, idx) => {
                      const isChosen = selectedOption === option;
                      const isCorrect = option === currentQ.correctAnswer;
                      const hasResult = selectedOption !== null;

                      let buttonStyle = 'bg-white text-purple-950 hover:bg-slate-50 border border-purple-100 shadow-sm';
                      if (hasResult) {
                        if (isCorrect) {
                          // Bright Green (Matching user's screenshot #22E555)
                          buttonStyle = 'bg-[#10b981] text-white shadow-lg ring-2 ring-emerald-300 font-bold scale-[1.02]';
                        } else if (isChosen && !isCorrect) {
                          // Coral Red (Matching user's screenshot #FF5A5A)
                          buttonStyle = 'bg-[#f43f5e] text-white shadow-lg ring-2 ring-rose-300 font-bold';
                        } else {
                          buttonStyle = 'bg-white/90 text-purple-950/60 opacity-60';
                        }
                      }

                      return (
                        <button
                          key={idx}
                          id={`option-btn-${idx}`}
                          disabled={Boolean(selectedOption)}
                          onClick={() => handleOptionClick(option)}
                          className={`w-full py-3.5 px-4 rounded-full text-center text-[14px] font-semibold transition-all duration-200 cursor-pointer ${buttonStyle}`}
                        >
                          {option}
                        </button>
                      );
                    })}
                  </div>

                  {/* Bottom Jokers / Power-ups (50/50, x2, skip matching screenshot) */}
                  <div className="flex items-center justify-center gap-4 pt-2">
                    <div className="flex flex-col items-center">
                      <div className="w-11 h-11 rounded-xl bg-slate-100 shadow flex items-center justify-center text-slate-700 font-bold text-xs border-2 border-slate-300">
                        50/50
                      </div>
                      <span className="text-[10px] text-amber-300 font-bold mt-0.5">200</span>
                    </div>
                    <div className="flex flex-col items-center">
                      <div className="w-11 h-11 rounded-xl bg-slate-100 shadow flex items-center justify-center text-slate-700 font-bold text-xs border-2 border-slate-300">
                        x2
                      </div>
                      <span className="text-[10px] text-amber-300 font-bold mt-0.5">200</span>
                    </div>
                    <div className="flex flex-col items-center">
                      <div className="w-11 h-11 rounded-xl bg-slate-100 shadow flex items-center justify-center text-slate-700 font-bold text-xs border-2 border-slate-300">
                        <RotateCcw className="w-4 h-4" />
                      </div>
                      <span className="text-[10px] text-amber-300 font-bold mt-0.5">100</span>
                    </div>
                  </div>
                </motion.div>
              ) : (
                /* GAME OVER RESULT SCREEN (Direct reproduction of Screenshot 1) */
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="flex flex-col h-full justify-between py-2"
                >
                  {/* Big Green Result Card (108-84 Tebrikler kazandınız) */}
                  <div className="bg-[#10b981] rounded-3xl p-6 text-white text-center shadow-2xl my-auto border-2 border-emerald-400">
                    <div className="flex items-center justify-center gap-8 mb-4">
                      {/* Player 1 */}
                      <div className="flex flex-col items-center">
                        <div className="w-14 h-14 rounded-full bg-amber-100 border-2 border-amber-400 flex items-center justify-center text-slate-700 font-bold shadow">
                          4
                        </div>
                        <span className="text-xs font-bold mt-1">Ingo S</span>
                      </div>

                      {/* Player 2 */}
                      <div className="flex flex-col items-center">
                        <div className="w-14 h-14 rounded-full bg-slate-200 border-2 border-slate-400 flex items-center justify-center text-slate-700 font-bold shadow">
                          1
                        </div>
                        <span className="text-xs font-bold mt-1">Iara</span>
                      </div>
                    </div>

                    <h2 className="text-4xl font-extrabold tracking-tight mb-1">
                      {matchScore.user}-{matchScore.opponent}
                    </h2>
                    <p className="text-lg font-bold text-emerald-50 mb-6">
                      Tebrikler kazandınız.
                    </p>

                    <div className="flex items-center justify-center gap-4">
                      <div className="flex items-center gap-2 bg-purple-900/60 px-4 py-1.5 rounded-full border border-purple-500/40">
                        <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                        <span className="font-extrabold text-sm text-yellow-300">108</span>
                      </div>
                      <div className="flex items-center gap-2 bg-purple-900/60 px-4 py-1.5 rounded-full border border-purple-500/40">
                        <Coins className="w-4 h-4 text-yellow-400" />
                        <span className="font-extrabold text-sm text-yellow-300">50</span>
                      </div>
                    </div>
                  </div>

                  {/* BOTTOM ACTION BUTTONS: Ana Menü vs Yeni Oyun (matching Screenshot 1) */}
                  <div className="flex items-center justify-around py-4">
                    {/* Left: Ana Menü */}
                    <div className="flex flex-col items-center gap-1">
                      <button
                        id="btn-main-menu"
                        onClick={() => {
                          setIsGameOver(false);
                          setCurrentQuestionIndex(0);
                        }}
                        className="w-16 h-16 rounded-full bg-[#4a1fb8] border-2 border-purple-400/50 flex items-center justify-center text-white shadow-lg hover:scale-105 transition-transform"
                      >
                        <Home className="w-7 h-7" />
                      </button>
                      <span className="text-xs font-bold text-purple-200">Ana Menü</span>
                    </div>

                    {/* Right: Yeni Oyun (Target button for bot auto restart!) */}
                    <div className="flex flex-col items-center gap-1 relative">
                      {settings.mode === 'auto' && settings.autoRestartGame && (
                        <div className="absolute -top-3 bg-emerald-500 text-white text-[9px] font-bold px-2 py-0.5 rounded-full animate-pulse shadow">
                          Bot Tıklayacak!
                        </div>
                      )}
                      <button
                        id="btn-restart-game"
                        onClick={() => {
                          setIsGameOver(false);
                          setCurrentQuestionIndex(0);
                          setSelectedOption(null);
                          setGameCycleCount(prev => prev + 1);
                        }}
                        className="w-16 h-16 rounded-full bg-[#4a1fb8] border-2 border-purple-400/50 flex items-center justify-center text-white shadow-lg hover:scale-105 transition-transform active:scale-95"
                      >
                        <RotateCcw className="w-7 h-7" />
                      </button>
                      <span className="text-xs font-bold text-purple-200">Yeni Oyun</span>
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Bottom simulated Home Indicator bar */}
          <div className="pb-2 flex justify-center">
            <div className="w-32 h-1 bg-white/30 rounded-full" />
          </div>
        </div>
      </div>

      {/* Simulator Control & Bot Status Side Card */}
      <div className="flex-1 max-w-md bg-white rounded-3xl p-6 border border-slate-200 shadow-sm flex flex-col justify-between">
        <div>
          <div className="flex items-center justify-between pb-4 border-b border-slate-100">
            <div>
              <h3 className="text-lg font-bold text-slate-800 flex items-center gap-2">
                <Zap className="w-5 h-5 text-amber-500" />
                TRT Bil Bakalım Simülatörü
              </h3>
              <p className="text-xs text-slate-500">
                Ekran görüntülerinizdeki oyun akışı ve bot tıklama davranışını birebir test edin.
              </p>
            </div>
            <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-purple-100 text-purple-800">
              Tur #{gameCycleCount}
            </span>
          </div>

          {/* Current Status Info */}
          <div className="mt-4 space-y-3">
            <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200">
              <div className="flex items-center justify-between text-xs text-slate-600 mb-1">
                <span className="font-medium">Aktif Mod:</span>
                <span className={`font-bold px-2 py-0.5 rounded-full ${
                  settings.mode === 'auto' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-800'
                }`}>
                  {settings.mode === 'auto' ? '⚡ Otomatik Bot' : '🖐️ Manuel Mod'}
                </span>
              </div>
              <div className="flex items-center justify-between text-xs text-slate-600 mb-1">
                <span className="font-medium">Tıklama Gecikmesi:</span>
                <span className="font-mono font-bold text-slate-800">{settings.clickDelayMs} ms</span>
              </div>
              <div className="flex items-center justify-between text-xs text-slate-600">
                <span className="font-medium">'Yeni Oyun' Otomatik Tıklama:</span>
                <span className="font-bold text-emerald-600">
                  {settings.autoRestartGame ? 'Açık' : 'Kapalı'}
                </span>
              </div>
            </div>

            {/* Live Bot Decision Tracker */}
            <div className="p-4 rounded-2xl bg-purple-50 border border-purple-100">
              <div className="flex items-center gap-2 text-xs font-bold text-purple-900 mb-2">
                <ShieldCheck className="w-4 h-4 text-purple-600" />
                <span>Bot Karar Motoru Durumu</span>
              </div>

              {!isGameOver ? (
                <div className="space-y-1.5 text-xs text-purple-950">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-600">Soru Durumu:</span>
                    <span className={`font-semibold ${isCurrentlyKnownInDB ? 'text-emerald-700' : 'text-amber-700'}`}>
                      {isCurrentlyKnownInDB ? 'Daha önce kaydedilmiş (Arşivde var)' : 'Yeni soru (İlk kez görülüyor)'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-600">Yapılacak İşlem:</span>
                    <span className="font-bold">
                      {settings.mode === 'manual'
                        ? 'Bekle (Kullanıcı tıklayacak)'
                        : isCurrentlyKnownInDB
                        ? `Doğru şıkka bas ("${dbMatch?.correctAnswer}")`
                        : 'Rastgele bir şıkkı dene ve renkten öğren'}
                    </span>
                  </div>
                  {isAutoWaiting && (
                    <div className="flex items-center gap-1.5 text-emerald-600 font-bold pt-1">
                      <Clock className="w-3.5 h-3.5 animate-spin" />
                      <span>{settings.clickDelayMs}ms gecikme sayılıyor...</span>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-xs text-purple-900">
                  <p className="font-semibold text-emerald-700">Maç Tamamlandı!</p>
                  <p className="text-slate-600 mt-1">
                    {settings.autoRestartGame
                      ? `Bot otomatik olarak "Yeni Oyun" butonuna tıklayarak sıradaki maçı başlatıyor...`
                      : `'Yeni Oyun' butonu için tıklama bekleniyor.`}
                  </p>
                </div>
              )}
            </div>

            {/* Learning Cycle Explanation */}
            <div className="p-3.5 rounded-2xl bg-amber-50/70 border border-amber-200/80 text-xs text-amber-900 leading-relaxed">
              <span className="font-bold">💡 Öğrenme Döngüsü Nasıl Çalışır?</span>
              <p className="mt-1 text-slate-700">
                Bot yeni bir soruyla karşılaştığında 4 şıktan birini rastgele tıklar. Yanlış çıksa bile oyunun yeşil yaktığı gerçek doğru şık anında Room SQLite veritabanına işlenir. Soru bir sonraki oyunda tekrar geldiğinde %100 doğrulukla doğrudan doğru şıkkı tıklar.
              </p>
            </div>
          </div>
        </div>

        <div className="pt-4 border-t border-slate-100 flex gap-2">
          <button
            id="btn-reset-match"
            onClick={() => {
              setCurrentQuestionIndex(0);
              setSelectedOption(null);
              setIsGameOver(false);
            }}
            className="w-full py-2.5 px-4 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold rounded-xl transition-colors"
          >
            Maçı Başa Sar
          </button>
        </div>
      </div>
    </div>
  );
};
