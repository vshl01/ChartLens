import React from 'react';
import {Image, StyleSheet, View} from 'react-native';
import {Card, Text, TouchableRipple} from 'react-native-paper';
import type {BrokerEntry} from '@registry/brokers';
import {startSession} from '@services/captureOrchestrator';
import {useSettings} from '@store/settingsStore';
import {spacing, radius} from '@theme/spacing';

type Props = {
  broker: BrokerEntry;
  installed: boolean;
  iconBase64?: string | null;
};

export function BrokerCard({broker, installed, iconBase64}: Props): React.JSX.Element {
  const setLast = useSettings(s => s.set);

  const onPress = async () => {
    if (!installed) return;
    setLast('lastBrokerId', broker.id);
    await startSession(broker.id);
  };

  return (
    <Card
      mode="contained"
      style={[styles.card, {backgroundColor: broker.brand.primary + '22', opacity: installed ? 1 : 0.5}]}>
      <TouchableRipple borderless style={styles.ripple} onPress={onPress}>
        <View style={styles.content}>
          <View
            style={[
              styles.iconWrap,
              {backgroundColor: broker.brand.primary},
            ]}>
            {iconBase64 ? (
              <Image
                source={{uri: `data:image/png;base64,${iconBase64}`}}
                style={styles.icon}
              />
            ) : (
              <Text variant="titleLarge" style={{color: broker.brand.onPrimary ?? '#fff'}}>
                {broker.name.charAt(0)}
              </Text>
            )}
          </View>
          <Text variant="titleMedium" numberOfLines={1}>
            {broker.name}
          </Text>
          <Text variant="labelSmall" style={styles.badge}>
            {installed ? 'Installed' : 'Not installed'}
          </Text>
        </View>
      </TouchableRipple>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {flex: 1, borderRadius: radius.lg, overflow: 'hidden'},
  ripple: {borderRadius: radius.lg},
  content: {
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.sm,
    alignItems: 'center',
    gap: spacing.xs,
  },
  iconWrap: {
    width: 56,
    height: 56,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  icon: {width: '100%', height: '100%'},
  badge: {opacity: 0.7},
});
