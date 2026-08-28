import { fitLigneDefaultsFromMas } from './fit-ligne-defaults';

describe('fitLigneDefaultsFromMas', () => {
  const mas = {
    id: 1,
    numero: 'MAS-01',
    numeroSocle: 'SOC-42',
    marqueId: 1,
    statut: 'UTILISEE',
    utilise: true
  };

  it('pre-fills numero socle from MAS', () => {
    expect(fitLigneDefaultsFromMas(mas)).toEqual({
      numeroSocle: 'SOC-42',
      numeroEmplacement: ''
    });
  });

  it('uses latest FIT ligne emplacement when available', () => {
    const defaults = fitLigneDefaultsFromMas(mas, {
      id: 10,
      numeroMachineCasino: 'MAS-01',
      lignes: [
        { id: 1, dateOperation: '2024-01-01', motifNatureOperations: 'A', numeroEmplacement: 'E1' },
        { id: 2, dateOperation: '2025-06-01', motifNatureOperations: 'B', numeroEmplacement: 'E2' }
      ]
    });
    expect(defaults.numeroEmplacement).toBe('E2');
  });
});
