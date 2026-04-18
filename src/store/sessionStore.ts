import {create} from 'zustand';

export type SessionState = {
  active: boolean;
  brokerId?: string;
  startedAt?: number;
  start(brokerId: string): void;
  stop(): void;
};

export const useSession = create<SessionState>(set => ({
  active: false,
  start: brokerId => set({active: true, brokerId, startedAt: Date.now()}),
  stop: () => set({active: false, brokerId: undefined, startedAt: undefined}),
}));
