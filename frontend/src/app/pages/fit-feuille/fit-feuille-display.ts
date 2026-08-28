import { Fit, FitLigne } from '../../models/models';

export interface FitFeuilleHeaderDisplay {
  casinoNom: string;
  numeroMachineCasino: string;
  dateMiseEnService: string | null;
  marque: string;
  typeMachine: string;
  numeroSerieMachine: string;
  numeroSocle: string;
  numeroSerieLecteur: string;
  tauxRedistribution: number | null;
  denoLabel: string;
  dateCessation: string | null;
  destinationMachineUsagee: string;
}

export interface FitFeuilleLigneDisplay {
  ligne: FitLigne;
  numeroSocle: string;
  numeroEmplacement: string;
  numeroSerieLecteur: string;
  tauxRedistribution: number | null;
  valeurUnitaireMises: number | null;
  denoLabel: string;
}

/** Libellé déno figé sur l'en-tête FIT. */
export function fitDenoLabel(fit: Fit): string {
  if (fit.multiDeno) {
    return 'MultiDéno';
  }
  return fit.denoLabel?.trim() || '';
}

/**
 * En-tête figé sur la FIT (snapshot à la création).
 * Seules cessation et destination restent évolutives sur l'entité FIT.
 */
export function buildFitFeuilleHeaderDisplay(fit: Fit): FitFeuilleHeaderDisplay {
  return {
    casinoNom: fit.casinoNom?.trim() || '',
    numeroMachineCasino: fit.numeroMachineCasino?.trim() || '',
    dateMiseEnService: fit.dateMiseEnService || null,
    marque: fit.marque?.trim() || '',
    typeMachine: fit.typeMachine?.trim() || '',
    numeroSerieMachine: fit.numeroSerieMachine?.trim() || '',
    numeroSocle: fit.numeroSocle?.trim() || '',
    numeroSerieLecteur: fit.numeroSerieLecteur?.trim() || '',
    tauxRedistribution: fit.tauxRedistribution ?? null,
    denoLabel: fitDenoLabel(fit),
    dateCessation: fit.dateCessation || null,
    destinationMachineUsagee: fit.destinationMachineUsagee?.trim() || ''
  };
}

/** Lignes : valeurs enregistrées + report chronologique emplacement / lecteur. */
export function buildFitFeuilleLigneDisplays(lignes: FitLigne[], fit: Fit | null): FitFeuilleLigneDisplay[] {
  let carriedEmplacement = '';
  let carriedSerieLecteur = fit?.numeroSerieLecteur?.trim() || '';

  return lignes.map((ligne) => {
    if (ligne.numeroEmplacement?.trim()) {
      carriedEmplacement = ligne.numeroEmplacement.trim();
    }
    if (ligne.numeroSerieLecteur?.trim()) {
      carriedSerieLecteur = ligne.numeroSerieLecteur.trim();
    }

    return {
      ligne,
      numeroSocle: ligne.numeroSocle?.trim() || '',
      numeroEmplacement: ligne.numeroEmplacement?.trim() || carriedEmplacement,
      numeroSerieLecteur: ligne.numeroSerieLecteur?.trim() || carriedSerieLecteur,
      tauxRedistribution: ligne.tauxRedistribution ?? null,
      valeurUnitaireMises: ligne.valeurUnitaireMises ?? null,
      denoLabel: ligne.denoLabel?.trim() || ''
    };
  });
}

export function displayCellValue(value: string | null | undefined, empty = '—'): string {
  const trimmed = value?.trim();
  return trimmed || empty;
}
