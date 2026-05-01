import React, {useEffect, useState} from 'react';
import {ScrollView, StyleSheet, View} from 'react-native';
import {Button, List, Menu, Switch, Text, TextInput, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useSettings} from '@store/settingsStore';
import {useHistory} from '@store/historyStore';
import {clearGeminiKey, getGeminiKey, setGeminiKey} from '@services/secureStorage';
import {pingApiKey} from '@services/gemini';
import {PATTERNS} from '@registry/patterns';
import type {BoxAutoDismiss, GeminiModel, MinConfidence} from '@/types';
import {spacing} from '@theme/spacing';

const AUTO_DISMISS_OPTIONS: {value: BoxAutoDismiss; label: string}[] = [
  {value: 5, label: '5 sec'},
  {value: 10, label: '10 sec'},
  {value: 20, label: '20 sec'},
  {value: 30, label: '30 sec'},
  {value: 0, label: 'Never'},
];

const CONFIDENCE_OPTIONS: {value: MinConfidence; label: string}[] = [
  {value: 0.4, label: '0.4 (loose)'},
  {value: 0.6, label: '0.6 (default)'},
  {value: 0.8, label: '0.8 (strict)'},
];

export function SettingsScreen(): React.JSX.Element {
  const theme = useTheme();
  const settings = useSettings(s => s.settings);
  const setSetting = useSettings(s => s.set);
  const clearHistory = useHistory(s => s.clear);

  const [apiKey, setApiKey] = useState('');
  const [hasKey, setHasKey] = useState(false);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [patternMenuOpen, setPatternMenuOpen] = useState(false);
  const [dismissMenuOpen, setDismissMenuOpen] = useState(false);
  const [confMenuOpen, setConfMenuOpen] = useState(false);

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

  const defaultPattern =
    PATTERNS.find(p => p.id === settings.defaultPatternId) ?? PATTERNS[0]!;
  const dismissLabel =
    AUTO_DISMISS_OPTIONS.find(o => o.value === settings.boxAutoDismissSec)?.label ?? '20 sec';
  const confLabel =
    CONFIDENCE_OPTIONS.find(o => o.value === settings.minConfidence)?.label ?? '0.6';

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
          <List.Subheader>Pattern detection</List.Subheader>
          <Menu
            visible={patternMenuOpen}
            onDismiss={() => setPatternMenuOpen(false)}
            anchor={
              <List.Item
                title="Default pattern (Quick Capture)"
                description={`${defaultPattern.emoji}  ${defaultPattern.name}`}
                onPress={() => setPatternMenuOpen(true)}
              />
            }>
            {PATTERNS.map(p => (
              <Menu.Item
                key={p.id}
                title={`${p.emoji}  ${p.name}`}
                onPress={() => {
                  setSetting('defaultPatternId', p.id);
                  setPatternMenuOpen(false);
                }}
              />
            ))}
          </Menu>

          <Menu
            visible={dismissMenuOpen}
            onDismiss={() => setDismissMenuOpen(false)}
            anchor={
              <List.Item
                title="Box auto-dismiss"
                description={dismissLabel}
                onPress={() => setDismissMenuOpen(true)}
              />
            }>
            {AUTO_DISMISS_OPTIONS.map(o => (
              <Menu.Item
                key={o.value}
                title={o.label}
                onPress={() => {
                  setSetting('boxAutoDismissSec', o.value);
                  setDismissMenuOpen(false);
                }}
              />
            ))}
          </Menu>

          <Menu
            visible={confMenuOpen}
            onDismiss={() => setConfMenuOpen(false)}
            anchor={
              <List.Item
                title="Min confidence"
                description={confLabel}
                onPress={() => setConfMenuOpen(true)}
              />
            }>
            {CONFIDENCE_OPTIONS.map(o => (
              <Menu.Item
                key={o.value}
                title={o.label}
                onPress={() => {
                  setSetting('minConfidence', o.value);
                  setConfMenuOpen(false);
                }}
              />
            ))}
          </Menu>

          {settings.boxAutoDismissSec === 0 ? (
            <Text
              variant="bodySmall"
              style={{
                paddingHorizontal: spacing.md,
                color: theme.colors.onSurfaceVariant,
              }}>
              Boxes will stay until you tap Clear or re-tap the bubble.
            </Text>
          ) : null}
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
