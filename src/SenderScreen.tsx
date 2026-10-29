import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  Button,
  DeviceEventEmitter,
  Linking,
  NativeModules,
  PermissionsAndroid,
  Text,
  View,
} from 'react-native';
import { KEYWORD, Sms, isHotstar } from './otp';
import { SmsCard, useNow } from './SmsCard';
import { styles } from './styles';

const { SmsModule } = NativeModules;

const SMS_PERMISSIONS = [
  PermissionsAndroid.PERMISSIONS.READ_SMS,
  PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
  PermissionsAndroid.PERMISSIONS.SEND_SMS,
];

// Native constants are a property on the old architecture, getConstants() on the new one
const RECIPIENTS: string[] =
  SmsModule.recipients ?? SmsModule.getConstants?.().recipients ?? [];

type SendResult = { result: string; time: number };

const hasSmsPermission = async () =>
  (
    await Promise.all(SMS_PERMISSIONS.map(p => PermissionsAndroid.check(p)))
  ).every(Boolean);

/** Shows the latest Hotstar OTP. Forwarding itself happens natively in SmsReceiver. */
export function SenderScreen() {
  const [sms, setSms] = useState<Sms | null>(null);
  const [status, setStatus] = useState('Requesting SMS permission...');
  const [denied, setDenied] = useState(false);
  const [sendError, setSendError] = useState('');
  const [results, setResults] = useState<Record<string, SendResult>>({});
  const [lastTrigger, setLastTrigger] = useState(0);
  const listening = useRef(false);
  const now = useNow(2000);

  // Carrier answers arrive natively a few seconds after sending; re-read them every tick
  useEffect(() => {
    SmsModule.getResults().then((json: string) => setResults(JSON.parse(json)));
    SmsModule.getLastTrigger().then(setLastTrigger);
  }, [now]);

  const startListening = useCallback(async () => {
    if (listening.current) return;
    listening.current = true;
    setDenied(false);

    // Show the most recent Hotstar SMS already in the inbox
    const latest: Sms | null = await SmsModule.getLatest(KEYWORD);
    if (latest) setSms(latest);

    SmsModule.startListening();
    setStatus(
      `Forwarding Hotstar OTPs by SMS to ${RECIPIENTS.join(
        ' and ',
      )}, even when this app is closed.`,
    );
  }, []);

  useEffect(() => {
    const smsSub = DeviceEventEmitter.addListener('onSms', (incoming: Sms) => {
      if (isHotstar(incoming)) setSms(incoming);
    });

    // Asks only the first time; once granted, Android doesn't ask again
    (async () => {
      const result = await PermissionsAndroid.requestMultiple(SMS_PERMISSIONS);
      const granted = Object.values(result).every(
        r => r === PermissionsAndroid.RESULTS.GRANTED,
      );
      if (granted) {
        startListening();
      } else {
        setDenied(true);
        setStatus('SMS permission is off, so nothing is being forwarded.');
      }
    })();

    // Start as soon as the user comes back from settings with permission granted
    const appStateSub = AppState.addEventListener('change', async state => {
      if (
        state === 'active' &&
        !listening.current &&
        (await hasSmsPermission())
      ) {
        startListening();
      }
    });

    return () => {
      smsSub.remove();
      appStateSub.remove();
      SmsModule.stopListening();
      listening.current = false;
    };
  }, [startListening]);

  const resend = async () => {
    if (!sms) return;
    setSendError('');
    try {
      await SmsModule.forward(sms.body);
    } catch (e) {
      setSendError(`Couldn't send: ${(e as Error).message}`);
    }
  };

  return (
    <>
      <Text style={styles.title}>Sharing your OTPs</Text>
      <Text style={styles.status}>{status}</Text>

      {denied ? (
        <View>
          <Text style={styles.body}>To turn on SMS access:</Text>
          <Text style={styles.body}>
            1. Tap "Open settings" below.{'\n'}
            2. Tap ⋮ (top right) → "Allow restricted settings".{'\n'}
            3. Go to Permissions → SMS → Allow.{'\n'}
            4. Come back to this app.
          </Text>
          <Text style={styles.meta}>
            If you don't see ⋮, try step 3 once, then come back to step 2.
          </Text>
          <View style={styles.button}>
            <Button
              title="Open settings"
              onPress={() => Linking.openSettings()}
            />
          </View>
        </View>
      ) : (
        <>
          <SmsCard sms={sms} now={now} />

          <Text style={styles.label}>Last forward</Text>
          {sms && lastTrigger >= sms.date && (
            <Text style={styles.meta}>
              Started automatically at{' '}
              {new Date(lastTrigger).toLocaleTimeString()} (
              {Math.round((lastTrigger - sms.date) / 1000)} s after the OTP
              arrived)
            </Text>
          )}
          {RECIPIENTS.map(number => {
            const r = results[number];
            return (
              <Text key={number} style={styles.meta}>
                {number}:{' '}
                {r
                  ? `${r.result} (${new Date(r.time).toLocaleTimeString()})`
                  : 'nothing sent yet'}
              </Text>
            );
          })}

          {sms && (
            <View style={styles.button}>
              <Button title="Send this OTP to family again" onPress={resend} />
              {!!sendError && <Text style={styles.meta}>{sendError}</Text>}
            </View>
          )}
        </>
      )}
    </>
  );
}
