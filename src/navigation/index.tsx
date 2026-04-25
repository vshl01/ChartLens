import React from 'react';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
import {HomeScreen} from '@screens/HomeScreen';
import {PresetsScreen} from '@screens/PresetsScreen';
import {HistoryScreen} from '@screens/HistoryScreen';
import {SettingsScreen} from '@screens/SettingsScreen';
import {OnboardingScreen} from '@screens/OnboardingScreen';
import {DiagnosticsScreen} from '@screens/DiagnosticsScreen';
import {useSettings} from '@store/settingsStore';

export type RootStackParamList = {
  Onboarding: undefined;
  Tabs: undefined;
  Diagnostics: undefined;
};

export type TabParamList = {
  Home: undefined;
  Presets: undefined;
  History: undefined;
  Settings: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<TabParamList>();

function Tabs(): React.JSX.Element {
  return (
    <Tab.Navigator
      screenOptions={({route}) => ({
        headerShown: false,
        tabBarIcon: ({color, size}) => {
          const map: Record<keyof TabParamList, string> = {
            Home: 'view-grid',
            Presets: 'lightning-bolt',
            History: 'history',
            Settings: 'cog',
          };
          return <MaterialCommunityIcons name={map[route.name]} color={color} size={size} />;
        },
      })}>
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Presets" component={PresetsScreen} />
      <Tab.Screen name="History" component={HistoryScreen} />
      <Tab.Screen name="Settings" component={SettingsScreen} />
    </Tab.Navigator>
  );
}

export function RootNavigator(): React.JSX.Element {
  const onboardingComplete = useSettings(s => s.settings.onboardingComplete);
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      {!onboardingComplete ? (
        <Stack.Screen name="Onboarding" component={OnboardingScreen} />
      ) : (
        <Stack.Screen name="Tabs" component={Tabs} />
      )}
      <Stack.Screen
        name="Diagnostics"
        component={DiagnosticsScreen}
        options={{headerShown: true, title: 'Diagnostics'}}
      />
    </Stack.Navigator>
  );
}
