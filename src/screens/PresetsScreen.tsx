import React from 'react';
import {FlatList, StyleSheet, View} from 'react-native';
import {List, Text, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {usePresets} from '@store/presetStore';
import {spacing} from '@theme/spacing';

export function PresetsScreen(): React.JSX.Element {
  const theme = useTheme();
  const presets = usePresets(s => s.presets);
  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <View style={styles.header}>
        <Text variant="headlineMedium">Presets</Text>
      </View>
      <FlatList
        data={presets}
        keyExtractor={p => p.id}
        renderItem={({item}) => (
          <List.Item
            title={`${item.emoji}  ${item.name}`}
            description={item.prompt || 'Custom prompt'}
            descriptionNumberOfLines={2}
          />
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
  header: {padding: spacing.md},
});
