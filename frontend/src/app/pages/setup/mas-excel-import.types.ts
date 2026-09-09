/** Champs MAS pouvant être mappés depuis une colonne Excel. */
export type MasImportFieldKey =
  | 'numero'
  | 'numeroSocle'
  | 'numeroSerie'
  | 'dateMiseEnService'
  | 'dateCessation'
  | 'marque'
  | 'typeMachine'
  | 'denoLabel'
  | 'denoValeur'
  | 'tauxRedistribution';

export interface MasImportFieldDef {
  key: MasImportFieldKey;
  label: string;
  /** Obligatoire pour créer une MAS. */
  requiredForCreate: boolean;
  /** Alias d’en-têtes Excel (normalisés) pour auto-mapping. */
  headerAliases: string[];
}

export const MAS_IMPORT_FIELDS: MasImportFieldDef[] = [
  {
    key: 'numero',
    label: 'N° machine',
    requiredForCreate: true,
    headerAliases: ['machine', 'numero', 'n machine', 'n° machine', 'num machine']
  },
  {
    key: 'numeroSocle',
    label: 'N° socle',
    requiredForCreate: false,
    headerAliases: ['socle', 'numero socle', 'n socle', 'n° socle']
  },
  {
    key: 'numeroSerie',
    label: 'N° de série',
    requiredForCreate: false,
    headerAliases: ['n de serie', 'n° de serie', 'numero de serie', 'n serie', 'serie']
  },
  {
    key: 'dateMiseEnService',
    label: 'Date mise en service',
    requiredForCreate: false,
    headerAliases: ['debut config', 'début config', 'date mise en service', 'mise en service', 'debut']
  },
  {
    key: 'dateCessation',
    label: 'Date de cessation',
    requiredForCreate: false,
    headerAliases: ['fin config', 'date cessation', 'cessation', 'fin']
  },
  {
    key: 'marque',
    label: 'Marque',
    requiredForCreate: true,
    headerAliases: ['marque', 'fabricant', 'brand']
  },
  {
    key: 'typeMachine',
    label: 'Type de machine',
    requiredForCreate: false,
    headerAliases: ['type', 'type machine', 'modele', 'modèle']
  },
  {
    key: 'denoLabel',
    label: 'Déno (libellé)',
    requiredForCreate: false,
    headerAliases: ['deno', 'déno', 'déno.', 'deno.', 'denomination', 'dénomination']
  },
  {
    key: 'denoValeur',
    label: 'Valeur déno',
    requiredForCreate: false,
    headerAliases: ['valeur deno', 'valeur déno', 'valeur denomination']
  },
  {
    key: 'tauxRedistribution',
    label: 'Taux MAS %',
    requiredForCreate: false,
    headerAliases: ['taux mas', 'taux mas %', 'taux', 'rtp']
  }
];

export type FileDupPolicy = 'keep-first' | 'keep-last' | 'skip-all';
export type DbDupPolicy = 'skip' | 'update';

export interface MasExcelColumn {
  index: number;
  header: string;
}

export interface MasExcelRawRow {
  rowNumber: number;
  values: Record<number, unknown>;
}

export type MasImportRowStatus =
  | 'ready'
  | 'dup-file'
  | 'dup-db-skip'
  | 'dup-db-update'
  | 'invalid';

export interface MasImportPreviewRow {
  rowNumber: number;
  numero: string;
  marque: string;
  numeroSocle: string;
  numeroSerie: string;
  typeMachine: string;
  denoLabel: string;
  denoValeur: number | null;
  tauxRedistribution: number | null;
  dateMiseEnService: string | null;
  dateCessation: string | null;
  status: MasImportRowStatus;
  message: string;
  existingMasId?: number;
}

export function normalizeHeader(raw: string): string {
  return raw
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[\n\r]+/g, ' ')
    .replace(/[^a-z0-9%]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function suggestColumnMappings(
  columns: MasExcelColumn[]
): Partial<Record<MasImportFieldKey, number | null>> {
  const byNorm = new Map<string, number>();
  for (const col of columns) {
    const n = normalizeHeader(col.header);
    if (n && !byNorm.has(n)) {
      byNorm.set(n, col.index);
    }
  }
  const mapping: Partial<Record<MasImportFieldKey, number | null>> = {};
  for (const field of MAS_IMPORT_FIELDS) {
    let found: number | null = null;
    for (const alias of field.headerAliases) {
      const idx = byNorm.get(normalizeHeader(alias));
      if (idx != null) {
        found = idx;
        break;
      }
    }
    // Fallback: contains match
    if (found == null) {
      for (const [norm, idx] of byNorm) {
        if (field.headerAliases.some((a) => norm.includes(normalizeHeader(a)) || normalizeHeader(a).includes(norm))) {
          found = idx;
          break;
        }
      }
    }
    mapping[field.key] = found;
  }
  // Prefer Type over Modèle for typeMachine when both match
  const typeCol = columns.find((c) => normalizeHeader(c.header) === 'type');
  if (typeCol) {
    mapping.typeMachine = typeCol.index;
  }
  return mapping;
}
