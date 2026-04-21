import React, {useEffect, useMemo, useState} from 'react';
import {FlatList, RefreshControl, StyleSheet, View} from 'react-native';
import {Banner, Button, Chip, Searchbar, Text, useTheme} from 'react-native-paper';
import {SafeAreaView} from 'react-native-safe-area-context';
import {BROKERS, type BrokerEntry} from '@registry/brokers';
import {AppLauncher} from '@native/AppLauncherModule';
import {Overlay} from '@native/OverlayModule';
import {BrokerCard} from '@components/BrokerCard';
import {spacing} from '@theme/spacing';
import {useSettings} from '@store/settingsStore';
import {useSession} from '@store/sessionStore';
import {stopSession} from '@services/captureOrchestrator';

export function HomeScreen(): React.JSX.Element {
  const theme = useTheme();
  const [installed, setInstalled] = useState<Record<string, boolean>>({});
  const [icons, setIcons] = useState<Record<string, string | null>>({});
  const [query, setQuery] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [hasOverlay, setHasOverlay] = useState(true);
  const hidden = useSettings(s => s.settings.hiddenBrokerIds);
  const session = useSession();

  const refresh = async () => {
    setRefreshing(true);
    const visible = BROKERS.filter(b => !hidden.includes(b.id));
    const checks = await Promise.all(
      visible.map(async b => [b.id, await AppLauncher.isAppInstalled(b.packageName)] as const),
    );
    setInstalled(Object.fromEntries(checks));
    const iconResults = await Promise.all(
      visible.map(async b => [b.id, await AppLauncher.getAppIcon(b.packageName)] as const),
    );
    setIcons(Object.fromEntries(iconResults));
    setHasOverlay(await Overlay.hasOverlayPermission());
    setRefreshing(false);
  };

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hidden]);

  const filtered: BrokerEntry[] = useMemo(() => {
    const q = query.trim().toLowerCase();
    return BROKERS.filter(b => !hidden.includes(b.id)).filter(
      b => !q || b.name.toLowerCase().includes(q),
    );
  }, [query, hidden]);

  return (
    <SafeAreaView style={[styles.root, {backgroundColor: theme.colors.background}]}>
      <View style={styles.header}>
        <Text variant="headlineMedium" style={{color: theme.colors.onSurface}}>
          ChartLens
        </Text>
        <Text variant="bodyMedium" style={{color: theme.colors.onSurfaceVariant}}>
          Pick a broker to start a capture session
        </Text>
        {session.active ? (
          <View style={styles.sessionRow}>
            <Chip
              icon="record-circle"
              mode="flat"
              compact
              style={{backgroundColor: theme.colors.primary + '22'}}>
              Active session: {session.brokerId}
            </Chip>
            <Button compact mode="text" onPress={() => void stopSession()}>
              Stop
            </Button>
          </View>
        ) : null}
      </View>

      <Banner
        visible={!hasOverlay}
        actions={[
          {
            label: 'Grant',
            onPress: async () => {
              await Overlay.requestOverlayPermission();
            },
          },
          {label: 'Recheck', onPress: () => void refresh()},
        ]}
        icon="alert-circle">
        ChartLens needs the "Display over other apps" permission for the floating bubble.
      </Banner>

      <Searchbar
        placeholder="Search brokers"
        value={query}
        onChangeText={setQuery}
        style={styles.search}
      />
      <FlatList
        data={filtered}
        keyExtractor={b => b.id}
        numColumns={2}
        contentContainerStyle={styles.grid}
        columnWrapperStyle={styles.row}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        renderItem={({item}) => (
          <BrokerCard
            broker={item}
            installed={!!installed[item.id]}
            iconBase64={icons[item.id] ?? undefined}
          />
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
  header: {paddingHorizontal: spacing.md, paddingTop: spacing.md, gap: spacing.xxs},
  sessionRow: {flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginTop: spacing.xs},
  search: {marginHorizontal: spacing.md, marginVertical: spacing.sm},
  grid: {paddingHorizontal: spacing.md, paddingBottom: spacing.xl, gap: spacing.sm},
  row: {gap: spacing.sm},
});
