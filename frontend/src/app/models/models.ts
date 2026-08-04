export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMs: number;
  username: string;
  nom?: string;
  prenom?: string;
  role: string;
  groupeId?: number;
  groupeNom?: string;
  atelierId?: number;
  ateliers?: AtelierSummary[];
}

export interface AtelierSummary {
  id: number;
  nom: string;
  casinoId: number;
  casinoNom: string;
  groupeId: number;
  groupeNom: string;
  label: string;
  coordonnees?: Coordonnees;
  responsables?: AtelierResponsable[];
}

export interface CasinoSummary {
  id: number;
  nom: string;
  groupeId: number;
  groupeNom: string;
}

export interface AtelierResponsable {
  id: number;
  username: string;
  nom: string;
  prenom: string;
  email: string;
}

export interface AdressePostale {
  ligne1?: string;
  ligne2?: string;
  codePostal?: string;
  ville?: string;
  pays?: string;
}

export interface EmailCoord {
  id?: number;
  valeur: string;
  principal?: boolean;
}

export interface TelephoneCoord {
  id?: number;
  valeur: string;
  label?: string;
  principal?: boolean;
}

export type TypeReseauSocial =
  | 'LINKEDIN'
  | 'FACEBOOK'
  | 'INSTAGRAM'
  | 'X'
  | 'YOUTUBE'
  | 'SITE_WEB'
  | 'AUTRE';

export interface ReseauSocial {
  id?: number;
  type: TypeReseauSocial;
  url: string;
}

export interface Coordonnees {
  id?: number;
  adresse?: AdressePostale;
  emails?: EmailCoord[];
  telephones?: TelephoneCoord[];
  reseauxSociaux?: ReseauSocial[];
}

export interface AtelierRequest {
  nom: string;
  casinoId: number;
  adresse?: AdressePostale;
  emails?: EmailCoord[];
  telephones?: TelephoneCoord[];
  reseauxSociaux?: ReseauSocial[];
  responsableIds?: number[];
}

export interface SfmContact {
  id?: number;
  nom: string;
  telephone: string;
  email: string;
  /** Si true, reçoit les e-mails de commande validée. */
  receiveOrderMails?: boolean;
}

export interface MarqueMasOption {
  id: number;
  code?: string;
  label: string;
  value?: number;
}

export interface Sfm {
  id: number;
  nom: string;
  responsable: string;
  telephone: string;
  email: string;
  contacts: SfmContact[];
  marqueIds?: number[];
  marques?: MarqueMasOption[];
}

export interface SfmForm {
  nom: string;
  contacts: SfmContact[];
  marqueIds: number[];
}

export interface Mas {
  id: number;
  numero: string;
  marqueId: number;
  marque?: string;
  marqueLabel?: string;
  utilise: boolean;
}

export interface MasForm {
  numero: string;
  marqueId: number | null;
  utilise: boolean;
}

export interface DevicePhoto {
  id: number;
  photoUrl: string;
  contentType?: string;
  fileSize?: number;
  position: number;
}

export interface Device {
  id: number;
  nom: string;
  reference?: string | null;
  usage: string;
  dateAcquisition: string;
  obsolete: boolean;
  photoUrl?: string;
  contentType?: string;
  fileSize?: number;
  photos?: DevicePhoto[];
  sfmId?: number | null;
  sfmNom?: string | null;
  masId?: number | null;
  masNumero?: string | null;
  masMarque?: string | null;
  marqueId?: number | null;
  marque?: string | null;
  marqueLabel?: string | null;
}

export interface DeviceForm {
  nom: string;
  reference?: string | null;
  usage: string;
  dateAcquisition: string;
  obsolete: boolean;
  sfmId: number | null;
  masId: number | null;
  keepPhotoIds?: number[];
}

export interface OrderRequestLineForm {
  deviceId: number;
  quantite: number;
}

export interface OrderRequestForm {
  message: string;
  lignes: OrderRequestLineForm[];
}

export interface OrderRequestLine {
  id?: number;
  deviceId: number;
  pieceNom?: string;
  reference?: string;
  quantite: number;
  photoUrl?: string;
  sfmId?: number | null;
  sfmNom?: string | null;
}

export interface OrderRequest {
  id: number;
  requestedBy: string;
  technicienNom?: string;
  pieceNom?: string;
  reference?: string;
  quantite: number;
  totalPieces?: number;
  totalQuantite?: number;
  message: string;
  deviceId?: number;
  photoUrl?: string;
  status: string;
  dateDemande?: string;
  createdAt: string;
  lignes?: OrderRequestLine[];
}

export interface AppUser {
  id: number;
  username: string;
  nom: string;
  prenom: string;
  email: string;
  role: string;
  preferredAtelierId?: number | null;
  preferredAtelierNom?: string | null;
  createdAt: string;
}

export interface AppUserForm {
  username: string;
  nom: string;
  prenom: string;
  email: string;
  password?: string;
  role: string;
  preferredAtelierId?: number | null;
}

export interface AppSetting {
  key: string;
  value: string;
  label: string;
  category: string;
  secret: boolean;
}

