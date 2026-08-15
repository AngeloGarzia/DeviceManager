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
  /** Si true, l'utilisateur doit changer son mot de passe avant d'utiliser l'app. */
  mustChangePassword?: boolean;
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
  /** Utilisateurs ayant cet atelier comme atelier préféré. */
  utilisateursPreferes?: AtelierResponsable[];
}

export interface CasinoSummary {
  id: number;
  nom: string;
  groupeId: number;
  groupeNom: string;
  /** Nombre d'ateliers rattachés (casino → atelier). */
  atelierCount?: number;
}

export interface CasinoRequest {
  nom: string;
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
  utilisateurPrefereIds?: number[];
}

export interface SfmContact {
  id?: number;
  nom: string;
  telephone: string;
  email: string;
  /** Si true, reçoit les e-mails de commande validée. */
  receiveOrderMails?: boolean;
  /** Technicien SFM — peut être rattaché à plusieurs SFM. */
  technicienSfm?: boolean;
}

/** Technicien SFM existant (réutilisation multi-SFM). */
export interface SfmTechnicien {
  id: number;
  nom: string;
  telephone: string;
  email: string;
  receiveOrderMails?: boolean;
  sfmIds?: number[];
  sfmNoms?: string[];
}

export interface MarqueMasOption {
  id: number;
  code?: string;
  label: string;
  value?: number;
}

export interface DenoOption {
  id: number;
  valeur: number;
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

export type MasStatut = 'UTILISEE' | 'EN_RESERVE' | 'VENDUE' | 'DETRUITE';

export interface Mas {
  id: number;
  numero: string;
  numeroSocle?: string | null;
  tauxRedistribution?: number | null;
  marqueId: number;
  marque?: string;
  marqueLabel?: string;
  denoId?: number | null;
  denoValeur?: number | null;
  denoLabel?: string | null;
  statut: MasStatut | string;
  statutLabel?: string;
  /** true si statut = UTILISEE */
  utilise: boolean;
}

export interface MasForm {
  numero: string;
  numeroSocle?: string | null;
  tauxRedistribution?: number | null;
  marqueId: number | null;
  denoId?: number | null;
  statut: MasStatut | string;
  utilise?: boolean;
}

export interface DevicePhoto {
  id: number;
  photoUrl: string;
  contentType?: string;
  fileSize?: number;
  position: number;
}

export type DeviceDocumentType = 'MANUAL' | 'DATASHEET' | 'NOTICE';

export interface DeviceDocument {
  id: number;
  docType: DeviceDocumentType | string;
  fileUrl: string;
  originalName: string;
  contentType?: string;
  fileSize?: number;
}

export interface Device {
  id: number;
  nom: string;
  reference?: string | null;
  usage: string;
  informationTechnique?: string | null;
  dateAcquisition: string;
  obsolete: boolean;
  /** Quantité disponible en stock (0 = rupture). */
  stock: number;
  photoUrl?: string;
  contentType?: string;
  fileSize?: number;
  photos?: DevicePhoto[];
  documents?: DeviceDocument[];
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
  informationTechnique?: string | null;
  dateAcquisition: string;
  obsolete: boolean;
  stock: number;
  sfmId: number | null;
  masId: number | null;
  keepPhotoIds?: number[];
  keepDocumentIds?: number[];
  newDocumentTypes?: DeviceDocumentType[];
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
  dateValidation?: string | null;
  dateReception?: string | null;
  createdAt: string;
  lignes?: OrderRequestLine[];
}

export type TimelineEventType =
  | 'ORDER_REQUEST'
  | 'ORDER_VALIDATED'
  | 'ORDER_RECEIVED'
  | 'INTERVENTION'
  | 'INTERVENTION_TECHNIQUE'
  | 'FIT'
  | 'STOCK_ADJUSTMENT';

/** Colonnes d'abscisse (swimlanes). */
export type TimelineColumn =
  | 'COMMANDES'
  | 'BONS'
  | 'INTERVENTIONS'
  | 'FIT'
  | 'STOCK';

export interface TimelineLine {
  deviceId?: number | null;
  pieceNom?: string | null;
  pieceReference?: string | null;
  quantite?: number | null;
  stockAvant?: number | null;
  stockApres?: number | null;
  delta?: number | null;
}

export interface TimelineEvent {
  type: TimelineEventType | string;
  /** Colonne d'abscisse : COMMANDES | BONS | INTERVENTIONS | FIT | STOCK. */
  column?: TimelineColumn | string | null;
  at: string;
  title: string;
  subtitle?: string | null;
  acteur?: string | null;
  refType?: string | null;
  refId?: number | null;
  masId?: number | null;
  masNumero?: string | null;
  deltaStock?: number | null;
  lignes?: TimelineLine[];
}

export interface InterventionLineForm {
  deviceId: number;
  quantite: number;
}

export interface InterventionForm {
  dateIntervention: string;
  emplacement?: string | null;
  machineMas?: string | null;
  masId?: number | null;
  /** Si true + masId : crée une ligne FIT liée au bon. */
  associerFit?: boolean | null;
  motif: string;
  diagnostic?: string | null;
  travaux: string;
  observations?: string | null;
  /** Data URL PNG — obligatoire si associerFit. */
  signatureAdmin?: string | null;
  signatureTechnicien?: string | null;
  signataireAdminNom?: string | null;
  signataireTechnicienNom?: string | null;
  lignes: InterventionLineForm[];
}

export interface InterventionLine {
  id?: number;
  deviceId: number;
  pieceNom?: string;
  pieceReference?: string | null;
  quantite: number;
  stockAvant?: number;
  stockApres?: number;
  photoUrl?: string;
}

export interface Intervention {
  id: number;
  numero: string;
  dateIntervention: string;
  technicienNom: string;
  emplacement?: string | null;
  machineMas?: string | null;
  masId?: number | null;
  motif: string;
  diagnostic?: string | null;
  travaux: string;
  observations?: string | null;
  createdAt: string;
  totalPieces?: number;
  totalQuantite?: number;
  lignes?: InterventionLine[];
}

export interface FitLigne {
  id: number;
  interventionId?: number | null;
  interventionNumero?: string | null;
  dateOperation: string;
  numeroSocle?: string | null;
  numeroEmplacement?: string | null;
  numeroSerieLecteur?: string | null;
  tauxRedistribution?: number | null;
  valeurUnitaireMises?: number | null;
  denoId?: number | null;
  denoLabel?: string | null;
  motifNatureOperations: string;
  signatureAdmin?: string | null;
  signatureTechnicien?: string | null;
  signataireAdminNom?: string | null;
  signataireTechnicienNom?: string | null;
  signatureDirecteur?: boolean;
  createdAt?: string;
}

export interface Fit {
  id: number;
  masId?: number | null;
  masNumero?: string | null;
  casinoNom?: string | null;
  numeroMachineCasino: string;
  dateMiseEnService?: string | null;
  marque?: string | null;
  typeMachine?: string | null;
  numeroSerieMachine?: string | null;
  numeroSerieLecteur?: string | null;
  dateCessation?: string | null;
  destinationMachineUsagee?: string | null;
  modeleNumero?: string | null;
  referenceLegale?: string | null;
  createdAt?: string;
  updatedAt?: string | null;
  totalLignes?: number;
  lignes?: FitLigne[];
}

export interface FitFromMasForm {
  masId: number;
}

export interface FitLigneForm {
  dateOperation: string;
  numeroSocle?: string | null;
  numeroEmplacement?: string | null;
  numeroSerieLecteur?: string | null;
  tauxRedistribution?: number | null;
  valeurUnitaireMises?: number | null;
  denoId?: number | null;
  motifNatureOperations: string;
  signatureAdmin: string;
  signatureTechnicien: string;
  signataireAdminNom?: string | null;
  signataireTechnicienNom?: string | null;
  /** Bon d'intervention optionnel. */
  interventionId?: number | null;
}

export interface FitSignataire {
  id: number;
  username: string;
  nom?: string | null;
  prenom?: string | null;
  displayName: string;
  role: string;
}

export interface FitSignataires {
  admins: FitSignataire[];
  techniciens: FitSignataire[];
}

export interface InterventionTechniqueForm {
  dateIntervention: string;
  masIds: number[];
  emplacement?: string | null;
  motif: string;
  diagnostic?: string | null;
  travaux: string;
  observations?: string | null;
  associerFit?: boolean | null;
  signatureAdmin?: string | null;
  signatureTechnicien?: string | null;
  signataireAdminNom?: string | null;
  signataireTechnicienNom?: string | null;
  commandeId?: number | null;
  bonInterventionId?: number | null;
}

export interface InterventionTechnique {
  id: number;
  visiteGroupeId: string;
  dateIntervention: string;
  technicienNom: string;
  emplacement?: string | null;
  masId: number;
  masNumero?: string | null;
  masMarque?: string | null;
  motif: string;
  diagnostic?: string | null;
  travaux: string;
  observations?: string | null;
  fitId?: number | null;
  fitLigneId?: number | null;
  commandeId?: number | null;
  bonInterventionId?: number | null;
  bonInterventionNumero?: string | null;
  createdAt?: string;
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

