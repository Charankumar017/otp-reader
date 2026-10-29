import React, { useEffect, useState } from 'react';
import { Text } from 'react-native';
import { OTP_TTL_MS, Sms, extractOtp } from './otp';
import { styles } from './styles';

/** Big OTP plus the SMS it came from. Crosses the OTP out once it has expired. */
export function SmsCard({ sms, now }: { sms: Sms | null; now: number }) {
  const otp = sms ? extractOtp(sms.body) : null;
  const expired = sms ? now - sms.date > OTP_TTL_MS : false;

  return (
    <>
      <Text style={styles.label}>Latest OTP{expired ? ' (expired)' : ''}</Text>
      <Text style={[styles.otp, expired && styles.expired]} selectable>
        {otp ?? '—'}
      </Text>

      <Text style={styles.label}>Latest SMS</Text>
      {sms ? (
        <>
          <Text style={styles.meta}>
            {sms.sender} · {new Date(sms.date).toLocaleString()}
          </Text>
          <Text style={styles.body} selectable>
            {sms.body}
          </Text>
        </>
      ) : (
        <Text style={styles.body}>No Hotstar SMS yet.</Text>
      )}
    </>
  );
}

/** Current time, refreshed every few seconds so expiry updates on screen. */
export function useNow(intervalMs = 5000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
