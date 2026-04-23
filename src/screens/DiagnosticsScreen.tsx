import React from 'react';
import {ScrollView, StyleSheet} from 'react-native';
import {List, Text, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {spacing} from '@theme/spacing';

export function DiagnosticsScreen(): React.JSX.Element {
  const theme = useTheme();
  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text variant="headlineMedium">Diagnostics</Text>
        <List.Section>
          <List.Subheader>Permissions</List.Subheader>
          <List.Item title="Overlay" description="—" />
          <List.Item title="Notifications" description="—" />
          <List.Item title="MediaProjection" description="Per-session" />
        </List.Section>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
  scroll: {padding: spacing.md},
});
