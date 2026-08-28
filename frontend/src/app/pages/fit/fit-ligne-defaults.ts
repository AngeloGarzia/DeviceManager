import { Fit, Mas } from '../../models/models';

export interface FitLigneMasDefaults {
  numeroSocle: string;
  numeroEmplacement: string;
}

/** Pré-remplit socle / emplacement à partir de la MAS et de l'historique FIT. */
export function fitLigneDefaultsFromMas(mas: Mas, fit?: Fit | null): FitLigneMasDefaults {
  return {
    numeroSocle: mas.numeroSocle?.trim() || '',
    numeroEmplacement: latestFitLigneEmplacement(fit)
  };
}

function latestFitLigneEmplacement(fit?: Fit | null): string {
  const lignes = fit?.lignes;
  if (!lignes?.length) {
    return '';
  }
  const sorted = [...lignes].sort((a, b) => {
    const byDate = (b.dateOperation || '').localeCompare(a.dateOperation || '');
    if (byDate !== 0) {
      return byDate;
    }
    return b.id - a.id;
  });
  return sorted[0]?.numeroEmplacement?.trim() || '';
}
