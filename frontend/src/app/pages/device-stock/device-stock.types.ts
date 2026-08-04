import { Device } from '../../models/models';

/** Mode de regroupement du stock : aucun, par SFM, par marque ou les deux. */
export type StockGroupMode = 'none' | 'sfm' | 'marque' | 'both';

/** Groupe de pièces détachées affiché dans la vue stock (éventuellement imbriqué). */
export interface StockGroup {
  /** Identifiant stable du groupe (clé de repliage). */
  key: string;
  /** Libellé affiché dans l'interface. */
  label: string;
  /** Pièces directement rattachées à ce groupe. */
  items: Device[];
  /** Sous-groupes (ex. marques sous un SFM). */
  children?: StockGroup[];
}
