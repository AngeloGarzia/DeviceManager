import {
  buildFitFeuilleHeaderDisplay,
  buildFitFeuilleLigneDisplays,
  fitDenoLabel
} from './fit-feuille-display';

describe('fit-feuille-display', () => {
  const fit = {
    id: 10,
    masId: 1,
    numeroMachineCasino: 'MAS-42',
    numeroSerieMachine: 'SERIE-FIT',
    numeroSocle: 'SOC-FIT',
    tauxRedistribution: 88,
    denoLabel: '2 €',
    typeMachine: 'Slot',
    marque: 'IGT',
    lignes: []
  };

  it('uses frozen FIT header fields only', () => {
    const header = buildFitFeuilleHeaderDisplay(fit);
    expect(header.numeroSerieMachine).toBe('SERIE-FIT');
    expect(header.numeroSocle).toBe('SOC-FIT');
    expect(header.tauxRedistribution).toBe(88);
    expect(header.denoLabel).toBe('2 €');
    expect(header.typeMachine).toBe('Slot');
  });

  it('shows MultiDéno from frozen FIT header', () => {
    expect(fitDenoLabel({ ...fit, multiDeno: true, denoLabel: '1 €' })).toBe('MultiDéno');
  });

  it('carries serie lecteur on lignes without MAS fallback', () => {
    const rows = buildFitFeuilleLigneDisplays(
      [
        {
          id: 1,
          dateOperation: '2024-01-01',
          motifNatureOperations: 'A',
          numeroSerieLecteur: 'LECT-1',
          tauxRedistribution: 90
        },
        {
          id: 2,
          dateOperation: '2025-01-01',
          motifNatureOperations: 'B',
          numeroSocle: 'SOC-2'
        }
      ],
      fit
    );
    expect(rows[0].numeroSerieLecteur).toBe('LECT-1');
    expect(rows[0].tauxRedistribution).toBe(90);
    expect(rows[1].numeroSerieLecteur).toBe('LECT-1');
    expect(rows[1].numeroSocle).toBe('SOC-2');
    expect(rows[1].tauxRedistribution).toBeNull();
  });
});
