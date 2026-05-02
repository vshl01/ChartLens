import * as Keychain from 'react-native-keychain';

const SERVICE = 'chartlens.gemini';
const USER = 'gemini-api-key';

// DEBUG ONLY — hardcoded fallback while we diagnose the network/auth flow.
// Remove this and the fallback in getGeminiKey() before sharing the build.
const DEBUG_FALLBACK_KEY = 'AIzaSyDXst_Ia4S2QTjnWryB16qWoDuxCzqwr1k';

export async function setGeminiKey(key: string): Promise<void> {
  await Keychain.setGenericPassword(USER, key, {
    service: SERVICE,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED,
    storage: Keychain.STORAGE_TYPE.AES_GCM_NO_AUTH,
  });
}

export async function getGeminiKey(): Promise<string | null> {
  try {
    const result = await Keychain.getGenericPassword({service: SERVICE});
    if (result && result.password) return result.password;
  } catch {}
  return DEBUG_FALLBACK_KEY;
}

export async function clearGeminiKey(): Promise<void> {
  await Keychain.resetGenericPassword({service: SERVICE});
}
