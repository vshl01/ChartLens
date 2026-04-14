import * as Keychain from 'react-native-keychain';

const SERVICE = 'chartlens.gemini';
const USER = 'gemini-api-key';

export async function setGeminiKey(key: string): Promise<void> {
  await Keychain.setGenericPassword(USER, key, {
    service: SERVICE,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED,
    storage: Keychain.STORAGE_TYPE.AES_GCM_NO_AUTH,
  });
}

export async function getGeminiKey(): Promise<string | null> {
  const result = await Keychain.getGenericPassword({service: SERVICE});
  if (!result) return null;
  return result.password;
}

export async function clearGeminiKey(): Promise<void> {
  await Keychain.resetGenericPassword({service: SERVICE});
}
