export type BrokerEntry = {
  id: string;
  name: string;
  packageName: string;
  iconFallback?: number;
  brand: {primary: string; onPrimary?: string};
  deepLink?: string;
  notes?: string;
};

export const BROKERS: readonly BrokerEntry[] = [
  {id: 'zerodha_kite', name: 'Zerodha Kite', packageName: 'com.zerodha.kite3', brand: {primary: '#387ED1'}},
  {id: 'groww', name: 'Groww', packageName: 'com.nextbillion.groww', brand: {primary: '#00B386'}},
  {id: 'upstox', name: 'Upstox', packageName: 'in.upstox.pro', brand: {primary: '#722ED1'}},
  {id: 'dhan', name: 'Dhan', packageName: 'in.dhan.live', brand: {primary: '#1E40AF'}},
  {id: 'angelone', name: 'Angel One', packageName: 'com.msf.angelmobile', brand: {primary: '#FF1744'}},
  {id: 'icicidirect', name: 'ICICI Direct', packageName: 'com.icicidirect.icicidirect', brand: {primary: '#B91C1C'}},
  {id: 'fivepaisa', name: '5paisa', packageName: 'com.fivepaisa.android', brand: {primary: '#0EA5E9'}},
  {id: 'tradingview', name: 'TradingView', packageName: 'com.tradingview.tradingviewapp', brand: {primary: '#2962FF'}},
] as const;

export const findBroker = (id: string): BrokerEntry | undefined =>
  BROKERS.find(b => b.id === id);
