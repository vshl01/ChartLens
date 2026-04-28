import 'react-native-gesture-handler';
import React from 'react';
import {StatusBar, useColorScheme} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {GestureHandlerRootView} from 'react-native-gesture-handler';
import {PaperProvider} from 'react-native-paper';
import {NavigationContainer, DarkTheme as NavDark, DefaultTheme as NavLight} from '@react-navigation/native';
import {RootNavigator} from '@/navigation';
import {darkTheme, lightTheme} from '@theme/paperTheme';
import {useSettings} from '@store/settingsStore';

function App(): React.JSX.Element {
  const system = useColorScheme();
  const mode = useSettings(s => s.settings.themeMode);
  const isDark = mode === 'system' ? system === 'dark' : mode === 'dark';
  const paperTheme = isDark ? darkTheme : lightTheme;
  const navTheme = isDark
    ? {...NavDark, colors: {...NavDark.colors, background: paperTheme.colors.background, primary: paperTheme.colors.primary}}
    : {...NavLight, colors: {...NavLight.colors, background: paperTheme.colors.background, primary: paperTheme.colors.primary}};

  return (
    <GestureHandlerRootView style={{flex: 1}}>
      <SafeAreaProvider>
        <PaperProvider theme={paperTheme}>
          <NavigationContainer theme={navTheme}>
            <StatusBar
              barStyle={isDark ? 'light-content' : 'dark-content'}
              backgroundColor={paperTheme.colors.background}
            />
            <RootNavigator />
          </NavigationContainer>
        </PaperProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

export default App;
