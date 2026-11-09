/** Abre un PDF/imagen recibido como Blob en una pestaña nueva (para imprimir etiquetas o ver firmas). */
export function openBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
