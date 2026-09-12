export type PlayMode = 'manual' | 'auto';

export interface Question {
  id: string;
  questionText: string;
  options: string[];
  correctAnswer: string | null;
  category?: string;
  known: boolean;
  timesSeen: number;
}

export interface BotLog {
  id: string;
  timestamp: string;
  type: 'info' | 'success' | 'warning' | 'click' | 'learn';
  message: string;
}

export interface BotSettings {
  mode: PlayMode;
  clickDelayMs: number;
  autoRestartGame: boolean;
  autoSolveKnown: boolean;
  randomGuessUnknown: boolean;
}
