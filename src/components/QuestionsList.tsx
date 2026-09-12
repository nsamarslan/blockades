import React, { useState } from 'react';
import { Question } from '../types';
import { Search, CheckCircle2, HelpCircle, Download, FileSpreadsheet, Sparkles, Filter, Trash2 } from 'lucide-react';

interface QuestionsListProps {
  questions: Question[];
  onClearQuestions?: () => void;
}

export const QuestionsList: React.FC<QuestionsListProps> = ({
  questions,
  onClearQuestions
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<'all' | 'known' | 'unknown'>('all');

  const filtered = questions.filter(q => {
    const matchesSearch = q.questionText.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (q.correctAnswer && q.correctAnswer.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (q.category && q.category.toLowerCase().includes(searchTerm.toLowerCase()));

    if (filterType === 'known') return matchesSearch && Boolean(q.correctAnswer);
    if (filterType === 'unknown') return matchesSearch && !q.correctAnswer;
    return matchesSearch;
  });

  const knownCount = questions.filter(q => Boolean(q.correctAnswer)).length;

  const exportAsJSON = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(questions, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `soru_arsivi_${new Date().toISOString().slice(0, 10)}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-5 rounded-3xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500">Toplam Soru Sayısı</div>
          <div className="text-3xl font-extrabold text-slate-800 mt-1">{questions.length}</div>
          <div className="text-[11px] text-slate-400 mt-1">Veritabanında kayıtlı</div>
        </div>

        <div className="bg-white p-5 rounded-3xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-emerald-600">Doğru Cevabı Bilinenler</div>
          <div className="text-3xl font-extrabold text-emerald-600 mt-1">{knownCount}</div>
          <div className="text-[11px] text-emerald-600/70 mt-1">
            Bot bu soruları %100 doğru çözer
          </div>
        </div>

        <div className="bg-white p-5 rounded-3xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-amber-600">Henüz Doğrulanmayanlar</div>
          <div className="text-3xl font-extrabold text-amber-600 mt-1">
            {questions.length - knownCount}
          </div>
          <div className="text-[11px] text-amber-600/70 mt-1">İlk karşılaşmada öğrenilecek</div>
        </div>
      </div>

      {/* Controls Bar */}
      <div className="bg-white p-4 rounded-3xl border border-slate-200 shadow-sm flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            id="search-questions"
            type="text"
            placeholder="Soru metni veya cevap ara..."
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-purple-500"
          />
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
          <button
            onClick={() => setFilterType('all')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-colors ${
              filterType === 'all'
                ? 'bg-purple-600 text-white shadow-sm'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            Tümü ({questions.length})
          </button>
          <button
            onClick={() => setFilterType('known')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-colors ${
              filterType === 'known'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            Bilinenler ({knownCount})
          </button>

          <button
            onClick={exportAsJSON}
            className="px-3.5 py-1.5 bg-slate-800 hover:bg-slate-900 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            <span>JSON İndir</span>
          </button>
        </div>
      </div>

      {/* Questions Cards */}
      <div className="space-y-3">
        {filtered.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-3xl border border-slate-200 p-6">
            <p className="text-sm font-semibold text-slate-500">Aramanıza uygun soru bulunamadı.</p>
          </div>
        ) : (
          filtered.map((q, idx) => (
            <div
              key={q.id}
              className="bg-white p-5 rounded-3xl border border-slate-200 shadow-sm hover:border-purple-200 transition-all"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold px-2 py-0.5 rounded-md bg-purple-50 text-purple-700">
                      #{idx + 1}
                    </span>
                    {q.category && (
                      <span className="text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-slate-600">
                        {q.category}
                      </span>
                    )}
                    {q.correctAnswer ? (
                      <span className="text-[11px] font-bold px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> Doğru Cevap Kayıtlı
                      </span>
                    ) : (
                      <span className="text-[11px] font-bold px-2 py-0.5 rounded-md bg-amber-100 text-amber-800 flex items-center gap-1">
                        <HelpCircle className="w-3 h-3" /> Henüz Öğrenilmedi
                      </span>
                    )}
                  </div>

                  <p className="text-sm font-bold text-slate-800 leading-relaxed">
                    {q.questionText}
                  </p>

                  {/* Options list */}
                  <div className="grid grid-cols-2 gap-2 pt-2">
                    {q.options.map((opt, optIdx) => {
                      const isCorrect = opt === q.correctAnswer;
                      return (
                        <div
                          key={optIdx}
                          className={`px-3 py-2 rounded-xl text-xs font-medium border ${
                            isCorrect
                              ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-bold'
                              : 'bg-slate-50 border-slate-200 text-slate-700'
                          }`}
                        >
                          {opt} {isCorrect && '✓ (Doğru)'}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
