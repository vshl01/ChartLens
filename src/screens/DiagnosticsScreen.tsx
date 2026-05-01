import React from 'react';
import {ScrollView, StyleSheet} from 'react-native';
import {List, Text, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {spacing} from '@theme/spacing';
import {useFlow} from '@services/captureOrchestrator';
import {useHistory} from '@store/historyStore';

export function DiagnosticsScreen(): React.JSX.Element {
  const theme = useTheme();
  const flow = useFlow();
  const last = useHistory(s => s.entries[0]);

  const lastBboxes = last?.matches?.slice(0, 3) ?? [];

  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text variant="headlineMedium">Diagnostics</Text>

        <List.Section>
          <List.Subheader>Flow</List.Subheader>
          <List.Item title="State" description={flow.state} />
          <List.Item
            title="Last frame"
            description={
              flow.lastFrameWidth > 0
                ? `${flow.lastFrameWidth} × ${flow.lastFrameHeight}`
                : '—'
            }
          />
          <List.Item title="Match count" description={String(flow.matchCount)} />
        </List.Section>

        <List.Section>
          <List.Subheader>Last response</List.Subheader>
          <List.Item
            title="Pattern"
            description={last ? last.patternName : '—'}
          />
          <List.Item
            title="Frame W × H"
            description={
              last ? `${last.frameWidth} × ${last.frameHeight}` : '—'
            }
          />
          <List.Item
            title="Matches returned"
            description={last ? String(last.matches.length) : '—'}
          />
          {lastBboxes.map((m, i) => (
            <List.Item
              key={i}
              title={`#${m.idx} · conf ${(m.confidence * 100).toFixed(0)}%`}
              description={`bbox ${m.bbox.x},${m.bbox.y} ${m.bbox.w}×${m.bbox.h}`}
            />
          ))}
          {last?.error ? (
            <List.Item title="Error" description={last.error} />
          ) : null}
        </List.Section>

        <List.Section>
          <List.Subheader>Recent transitions</List.Subheader>
          {flow.transitions.slice(-8).reverse().map((t, i) => (
            <List.Item
              key={i}
              title={`${t.from} → ${t.to}`}
              description={t.note ?? ''}
            />
          ))}
        </List.Section>

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
