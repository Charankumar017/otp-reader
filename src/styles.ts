import { StyleSheet } from 'react-native';

export const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: '#fff' },
  title: { fontSize: 22, fontWeight: '700', color: '#111', marginBottom: 8 },
  status: { color: '#666', marginBottom: 32 },
  label: {
    fontSize: 13,
    color: '#888',
    textTransform: 'uppercase',
    marginTop: 16,
  },
  otp: { fontSize: 56, fontWeight: '700', letterSpacing: 6, color: '#111' },
  expired: { color: '#bbb', textDecorationLine: 'line-through' },
  meta: { color: '#666', marginTop: 6 },
  body: { fontSize: 16, color: '#222', marginTop: 6 },
  button: { marginTop: 24 },
});
