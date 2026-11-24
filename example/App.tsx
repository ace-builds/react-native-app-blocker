import { useEffect, useState } from 'react';
import {
  AppState,
  Button,
  FlatList,
  Platform,
  SafeAreaView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import * as AppBlocker from 'react-native-app-blocker';

const SESSION_MS = 2 * 60 * 1000;

export default function App() {
  const [hasPermission, setHasPermission] = useState(false);
  const [blocking, setBlocking] = useState(false);
  const [mode, setMode] = useState<AppBlocker.BlockingMode>('blocklist');
  const [status, setStatus] = useState('');
  // Android
  const [apps, setApps] = useState<AppBlocker.AppInfo[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const refresh = async () => {
    setHasPermission(await AppBlocker.hasPermission());
    setBlocking(await AppBlocker.isBlocking());
  };

  useEffect(() => {
    refresh();
    AppBlocker.setShieldOptions({
      title: 'Not now',
      subtitle: '{app} is blocked for another {time}.',
      accentColor: '#7C3AED',
    });
    if (Platform.OS === 'android') {
      AppBlocker.getInstalledApps().then(setApps);
      AppBlocker.getSelectedApps().then((s) => setSelected(new Set(s.map((a) => a.bundleId))));
    } else {
      AppBlocker.getAppPickerSummary().then((s) => setStatus(`${s.applicationCount} apps picked`));
    }
    // Android permission is granted in system settings, so re-check on return.
    const appState = AppState.addEventListener('change', (s) => s === 'active' && refresh());
    const blocked = AppBlocker.addBlockedAppListener((e) => setStatus(`Blocked ${e.appName}`));
    return () => {
      appState.remove();
      blocked.remove();
    };
  }, []);

  const toggleApp = (bundleId: string) => {
    const next = new Set(selected);
    next.has(bundleId) ? next.delete(bundleId) : next.add(bundleId);
    setSelected(next);
    AppBlocker.setSelectedApps(apps.filter((a) => next.has(a.bundleId)));
  };

  const start = async () => {
    try {
      await AppBlocker.startBlocking({ mode, endTime: Date.now() + SESSION_MS, message: 'You chose this.' });
      setBlocking(true);
    } catch (e) {
      setStatus(String(e));
    }
  };

  const stop = async () => {
    await AppBlocker.stopBlocking();
    setBlocking(false);
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.header}>react-native-app-blocker</Text>
      <Text>Permission: {hasPermission ? 'granted' : 'not granted'}</Text>
      {!hasPermission && (
        <Button title="Grant permission" onPress={() => AppBlocker.requestPermission().then(refresh)} />
      )}

      <View style={styles.row}>
        <Text>Allowlist</Text>
        <Switch value={mode === 'blocklist'} onValueChange={(v) => setMode(v ? 'blocklist' : 'allowlist')} />
        <Text>Blocklist</Text>
      </View>
      <Text style={styles.hint}>
        {mode === 'blocklist' ? 'Selected apps are blocked.' : 'Everything except selected apps is blocked.'}
      </Text>

      {Platform.OS === 'ios' ? (
        <Button
          title="Pick apps"
          disabled={!hasPermission}
          onPress={async () => {
            const s = await AppBlocker.presentAppPicker();
            setStatus(`${s.applicationCount} apps picked`);
          }}
        />
      ) : (
        <FlatList
          style={styles.list}
          data={apps}
          keyExtractor={(a) => a.bundleId}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <Switch value={selected.has(item.bundleId)} onValueChange={() => toggleApp(item.bundleId)} />
              <Text>{item.name}</Text>
            </View>
          )}
        />
      )}

      <Button
        title={blocking ? 'Stop blocking' : 'Block for 2 minutes'}
        disabled={!hasPermission}
        onPress={blocking ? stop : start}
      />
      <Text style={styles.hint}>{status}</Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, gap: 12 },
  header: { fontSize: 22, fontWeight: '600', marginTop: 20 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 4 },
  hint: { color: '#666' },
  list: { flex: 1 },
});
