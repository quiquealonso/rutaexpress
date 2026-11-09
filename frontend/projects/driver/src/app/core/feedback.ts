let audio: AudioContext | null = null;

/** Pitido corto (agudo = bien, grave = aviso) y vibración, para confirmar un escaneo sin mirar la pantalla. */
export function scanFeedback(ok: boolean): void {
  try {
    navigator.vibrate?.(ok ? 80 : [120, 80, 120]);
    audio ??= new AudioContext();
    const osc = audio.createOscillator();
    const gain = audio.createGain();
    osc.frequency.value = ok ? 1200 : 300;
    gain.gain.value = 0.15;
    osc.connect(gain).connect(audio.destination);
    osc.start();
    osc.stop(audio.currentTime + (ok ? 0.08 : 0.25));
  } catch {
    /* sin audio */
  }
}

/** Lee un texto en voz alta (respuestas del asistente). */
export function speak(text: string): void {
  if (!('speechSynthesis' in window) || !text) return;
  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text);
  u.lang = 'es-ES';
  u.rate = 1.05;
  window.speechSynthesis.speak(u);
}

export function uuid(): string {
  return crypto.randomUUID();
}
