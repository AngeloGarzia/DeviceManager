import { Mas, MasStatut } from '../models/models';

const LABELS: Record<string, string> = {
  UTILISEE: 'Machine utilisée',
  EN_RESERVE: 'En réserve',
  VENDUE: 'Vendue',
  DETRUITE: 'Détruite'
};

type MasStatutSource = Pick<Mas, 'statut' | 'utilise'> & { statutLabel?: string | null };

/** Libellé d'affichage du statut MAS. */
export function masStatutLabel(mas: MasStatutSource | null | undefined): string {
  if (!mas) {
    return '—';
  }
  if (mas.statutLabel) {
    return mas.statutLabel;
  }
  const code = resolveMasStatut(mas);
  return LABELS[code] || code;
}

export function resolveMasStatut(mas: Pick<Mas, 'statut' | 'utilise'>): MasStatut | string {
  if (mas.statut) {
    return mas.statut;
  }
  return mas.utilise ? 'UTILISEE' : 'EN_RESERVE';
}

/** Classes CSS badge selon le statut. */
export function masStatutBadgeClass(mas: Pick<Mas, 'statut' | 'utilise'>): string {
  switch (resolveMasStatut(mas)) {
    case 'UTILISEE':
      return 'bg-emerald-50 text-emerald-700';
    case 'EN_RESERVE':
      return 'bg-sky-50 text-sky-800';
    case 'VENDUE':
      return 'bg-violet-50 text-violet-800';
    case 'DETRUITE':
      return 'bg-rose-50 text-rose-700';
    default:
      return 'bg-amber-50 text-amber-800';
  }
}
