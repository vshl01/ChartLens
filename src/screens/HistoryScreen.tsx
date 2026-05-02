import React from 'react';
import {StyleSheet, View} from 'react-native';
import {List, Text, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {FlashList} from '@shopify/flash-list';
import {format} from 'date-fns';
import {useHistory} from '@store/historyStore';
import {spacing} from '@theme/spacing';

export function HistoryScreen(): React.JSX.Element {
  const theme = useTheme();
  const entries = useHistory(s => s.entries);

  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <View style={styles.header}>
        <Text variant="headlineMedium">History</Text>
      </View>
      {entries.length === 0 ? (
        <View style={styles.empty}>
          <Text variant="bodyMedium" style={{color: theme.colors.onSurfaceVariant}}>
            Your captures will appear here.
          </Text>
        </View>
      ) : (
        <FlashList
          data={entries}
          keyExtractor={e => e.id}
          renderItem={({item}) => (
            <List.Item
              title={item.patternName}
              description={`${item.brokerId} • ${item.matches.length} match${item.matches.length === 1 ? '' : 'es'} • ${format(item.capturedAt, 'PP p')}`}
            />
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
  header: {padding: spacing.md},
  empty: {flex: 1, alignItems: 'center', justifyContent: 'center'},
});
