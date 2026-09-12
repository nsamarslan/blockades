import React from 'react';
import { BotLog } from '../types';
import { Terminal, Trash2 } from 'lucide-react';

interface BotLogsProps {
  logs: BotLog[];
  onClearLogs: () => void;
}

export const BotLogs: React.FC<BotLogsProps> = ({ logs, onClearLogs }) => {
  return (
    <div className="bg-slate-900 rounded-3xl p-5 border border-slate-800 text-white font-mono text-xs shadow-lg space-y-3">
      <div className="flex items-center justify-between border-b border-slate-800 pb-3">
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4 text-emerald-400" />
          <span className="font-bold text-slate-200">Canlı Erişilebilirlik & Bot Logları</span>
          <span className="text-[10px] bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full">
            {logs.length} olay
          </span>
        </div>
        <button
          onClick={onClearLogs}
          className="text-slate-500 hover:text-slate-300 transition-colors cursor-pointer"
          title="Logları temizle"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>

      <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-700">
        {logs.length === 0 ? (
          <div className="text-slate-500 italic py-4 text-center">
            Henüz log üretilmedi. Simülatörden veya oyundan bir işlem yapıldığında burada anlık gösterilir.
          </div>
        ) : (
          logs.map(log => {
            let color = 'text-slate-300';
            if (log.type === 'success') color = 'text-emerald-400';
            if (log.type === 'warning') color = 'text-amber-400';
            if (log.type === 'click') color = 'text-cyan-400 font-bold';
            if (log.type === 'learn') color = 'text-purple-400 font-bold';

            return (
              <div key={log.id} className="flex items-start gap-2 leading-relaxed">
                <span className="text-slate-500 shrink-0 select-none">[{log.timestamp}]</span>
                <span className={color}>{log.message}</span>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
