import { describe, expect, it } from 'vitest';
import { DistancePipe, DurationPipe, LabelPipe } from './pipes';

describe('pipes', () => {
  it('traduce estados', () => {
    expect(new LabelPipe().transform('OUT_FOR_DELIVERY')).toBe('En reparto');
    expect(new LabelPipe().transform('DESCONOCIDO')).toBe('DESCONOCIDO');
    expect(new LabelPipe().transform(null)).toBe('');
  });

  it('formatea distancias', () => {
    expect(new DistancePipe().transform(350)).toBe('350 m');
    expect(new DistancePipe().transform(12_345)).toBe('12.3 km');
  });

  it('formatea duraciones', () => {
    expect(new DurationPipe().transform(600)).toBe('10 min');
    expect(new DurationPipe().transform(5400)).toBe('1 h 30 min');
  });
});
