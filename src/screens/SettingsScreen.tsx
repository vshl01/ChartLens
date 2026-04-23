import React, {useEffect, useState} from 'react';
import {ScrollView, StyleSheet, View} from 'react-native';
import {Button, List, Switch, Text, TextInput, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useSettings} from '@store/settingsStore';
import {useHistory} from '@store/historyStore';
import {clearGeminiKey, getGeminiKey, setGeminiKey} from '@services/secureStorage';
import {pingApiKey} from '@services/gemini';
import type {GeminiModel} from '@/types';
import {spacing} from '@theme/spacing';

export function SettingsScreen(): React.JSX.Element {
  const theme = useTheme();
  const settings = useSettings(s => s.settings);
  const setSetting = useSettings(s => s.set);
  const clearHistory = useHistory(s => s.clear);

  const [apiKey, setApiKey] = useState('');
  const [hasKey, setHasKey] = useState(false);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    void getGeminiKey().then(k => setHasKey(!!k));
  }, []);

  const onSave = async () => {
    if (!apiKey) return;
    setBusy(true);
    setStatus(null);
    const ok = await pingApiKey(apiKey, settings.model);
    if (!ok) {
      setStatus('Key did not validate against Gemini.');
      setBusy(false);
      return;
    }
    await setGeminiKey(apiKey);
    setHasKey(true);
    setApiKey('');
    setStatus('Saved.');
    setBusy(false);
  };

  const onClear = async () => {
    await clearGeminiKey();
    setHasKey(false);
  };

  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text variant="headlineMedium">Settings</Text>

        <List.Section>
          <List.Subheader>Gemini</List.Subheader>
          <View style={styles.row}>
            <TextInput
              label={hasKey ? 'Replace API key' : 'API key'}
              value={apiKey}
              onChangeText={setApiKey}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              style={{flex: 1}}
            />
          </View>
          <View style={styles.actions}>
            <Button mode="contained" onPress={onSave} loading={busy} disabled={!apiKey || busy}>
              Save & test
            </Button>
            {hasKey ? (
              <Button mode="outlined" onPress={onClear}>
                Remove
              </Button>
            ) : null}
          </View>
          {status ? (
            <Text variant="bodySmall" style={{paddingHorizontal: spacing.md}}>
              {status}
            </Text>
          ) : null}
          <List.Item
            title="Model"
            description={settings.model}
            onPress={() =>
              setSetting(
                'model',
                (settings.model === 'gemini-2.5-flash'
                  ? 'gemini-2.5-pro'
                  : 'gemini-2.5-flash') as GeminiModel,
              )
            }
          />
        </List.Section>

        <List.Section>
          <List.Subheader>Bubble</List.Subheader>
          <List.Item title="Size" description={`${settings.bubbleSize}dp`} />
          <List.Item title="Opacity" description={settings.bubbleOpacity.toFixed(2)} />
        </List.Section>

        <List.Section>
          <List.Subheader>Theme</List.Subheader>
          <List.Item
            title="Follow system"
            right={() => (
              <Switch
                value={settings.themeMode === 'system'}
                onValueChange={v => setSetting('themeMode', v ? 'system' : 'light')}
              />
            )}
          />
        </List.Section>

        <List.Section>
          <List.Subheader>Data</List.Subheader>
          <List.Item title="Clear history" onPress={clearHistory} />
        </List.Section>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
  scroll: {padding: spacing.md, gap: spacing.sm},
  row: {flexDirection: 'row', paddingHorizontal: spacing.md, gap: spacing.sm},
  actions: {flexDirection: 'row', gap: spacing.sm, paddingHorizontal: spacing.md, paddingTop: spacing.sm},
});
