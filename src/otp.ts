export type Sms = { sender: string; body: string; date: number };

export const KEYWORD = 'hotstar'; // matches "Hotstar" and "JioHotstar" in sender or body

// Hotstar OTPs stop working after about 10 minutes
export const OTP_TTL_MS = 10 * 60 * 1000;

export const isHotstar = (sms: Sms) =>
  `${sms.sender} ${sms.body}`.toLowerCase().includes(KEYWORD);

// First standalone 4-6 digit number in the message
export const extractOtp = (body: string) =>
  body.match(/\b\d{4,6}\b/)?.[0] ?? null;
