import React from 'react';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { SenderScreen } from './src/SenderScreen';
import { styles } from './src/styles';

export default function App() {
  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container}>
        <SenderScreen />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}
