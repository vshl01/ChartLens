import React from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Text, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useSettings} from '@store/settingsStore';
import {spacing} from '@theme/spacing';

export function OnboardingScreen(): React.JSX.Element {
  const theme = useTheme();
  const setSetting = useSettings(s => s.set);

  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <View style={styles.content}>
        <Text variant="displaySmall">Welcome to ChartLens</Text>
        <Text variant="bodyLarge" style={{color: theme.colors.onSurfaceVariant}}>
          Capture any candlestick chart from your broker app and get instant pattern analysis powered by Gemini.
        </Text>
      </View>
      <Button
        mode="contained"
        style={styles.cta}
        onPress={() => setSetting('onboardingComplete', true)}>
        Get started
      </Button>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, padding: spacing.lg, justifyContent: 'space-between'},
  content: {gap: spacing.md, marginTop: spacing.xl},
  cta: {marginBottom: spacing.lg},
});
