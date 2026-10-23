
export type RoleName =
'ROLE_SUPER_ADMIN' |
'ROLE_ORG_ADMIN' |
'ROLE_MODERATOR' |
'ROLE_USER';

/** Second-factor state as reported by identity-service; never includes the secret or recovery codes. */
export interface MfaSummary {
  required: boolean;
  enrolled: boolean;
  enrollmentDeadline: string | null;
  enrollmentOverdue: boolean;
}

export interface AuthUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: RoleName;
  enabled: boolean;
  mustChangePassword: boolean;
  permissions: string[];
  organizations: { id: number; name: string }[];
  organizationIds: number[];
  mfa?: MfaSummary | null;
}

/** Password accepted; a second factor must be completed before any session exists. */
export interface LoginOutcome {
  mfaRequired: true;
  enrollmentRequired: boolean;
  expiresAt: string;
}

export function isLoginOutcome(value: AuthUser | LoginOutcome): value is LoginOutcome {
  return (value as LoginOutcome).mfaRequired === true;
}

export interface MfaStatus {
  enabled: boolean;
  required: boolean;
  enrolled: boolean;
  confirmedAt: string | null;
  enrollmentDeadline: string | null;
  enrollmentOverdue: boolean;
  recoveryCodesRemaining: number;
}

/** Shown exactly once while the user adds the key to an authenticator app. */
export interface MfaEnrollment {
  secret: string;
  otpauthUri: string;
  issuer: string;
  account: string;
}

export interface MfaConfirmResult {
  recoveryCodes: string[];
  user: AuthUser | null;
}

export interface SessionInfo {
  sessionId: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  userAgent: string | null;
  current: boolean;
}

export type PublicChangeType = 'FIRST_PUBLICATION' | 'UPDATE' | 'REPUBLISHED';

/** Public history entry: only the publication moment, its kind and the names of changed sections. */
export interface PublicChange {
  publishedAt: string;
  changeType: PublicChangeType;
  sections: string[];
}

/** Staff member responsible for keeping a card current (administrative responses only). */
export interface AssigneeSummary {
  userId: number;
  name: string | null;
  email: string | null;
  assignedAt: string | null;
}

export interface StaffMember {
  id: number;
  firstName: string | null;
  lastName: string | null;
  email: string;
  role: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
    currentPassword: string;
  newPassword: string;
}

export interface PasswordResetOptions {
  selfServiceAvailable: boolean;
}

/** Returned once to the SUPER_ADMIN who started a reset; the token inside the path is never shown again. */
export interface PasswordResetLink {
  userId: number;
  resetPath: string;
  expiresAt: string;
}

export type VerificationStatus = 'UNVERIFIED' | 'VERIFIED' | 'DUE' | 'OUTDATED';

export interface OrganizationContact {
  address: string | null;
  phone: string | null;
  workingHours: string | null;
  latitude: number | null;
  longitude: number | null;
  mapUrl: string | null;
  regionCode: string | null;
}

export interface VerificationInfo {
  officialSourceUrl?: string | null;
  lastVerifiedAt?: string | null;
  verificationStatus?: VerificationStatus;
}

export interface PublicOrganization extends VerificationInfo {
  id: number;
  name: string;
  description: string | null;
  nameTranslations?: Record<string, TranslatedText>;
  descriptionTranslations?: Record<string, TranslatedText>;
  contact?: OrganizationContact | null;
}

export interface Organization extends PublicOrganization {
  enabled: boolean;
  createdAt: string;
  verifiedByUserId?: number | null;
}

export interface TranslatedText {
  text: string;
  source: 'human' | 'machine';
}

export interface CreateOrganizationRequest {
  name: string;
  description: string | null;
  contact?: OrganizationContact;
  officialSourceUrl?: string;
}

export interface UpdateOrganizationRequest {
  name?: string | null;
  description?: string | null;
  contact?: OrganizationContact;
  officialSourceUrl?: string;
}

export interface StaffUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: string;
  enabled: boolean;
  organizationIds: number[];
}

export interface CreateOrgAdminRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
  organizationId: number;
}

export interface PromoteOrgAdminRequest {
  userId: number;
  organizationId: number;
}

export interface UpdateOrgAdminRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  organizationId?: number;
  enabled?: boolean;
}

export interface CreateModeratorRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
  organizationIds: number[];
}

export interface PromoteModeratorRequest {
  userId: number;
  organizationIds: number[];
}

export interface UpdateModeratorRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
    organizationIds: number[];
  enabled?: boolean;
}

export interface Permission {
  id: number;
  code: string;
  name: string;
  category: string | null;
}

export interface RoleEntity {
  id: number;
  name: string;
  permissions: Permission[];
}

export interface CreateRoleRequest {
  name: string;
}

export interface AssignRoleRequest {
  userId: number;
  roleId: number;
}

export interface UpdateRolePermissionsRequest {
  permissionIds: number[];
}

export interface Region {
  id: number;
  name: string;
  code: string;
}

export interface Language {
  id: number;
  code: string;
  name: string;
  nativeName: string | null;
  defaultLanguage: boolean;
}

export interface LanguageSearchResult {
  code: string;
  name: string;
  nativeName: string;
  alreadyAdded: boolean;
}

export interface ServiceInstructions {
  whoCanUse: string | null;
  steps: string | null;
  requiredDocuments: string | null;
  whereHowToApply: string | null;
  processingTime: string | null;
  fee: string | null;
}

export type InstructionField = keyof ServiceInstructions;

export type InstructionTranslations = Partial<Record<InstructionField, Record<string, TranslatedText>>>;

export interface CatalogFunction extends VerificationInfo {
  id: number;
  name: string;
  description: string | null;
  organizationId: number;
  requirements: string | null;
  category: string | null;
  categoryId?: number | null;
  sourceLanguage?: string;
  nameTranslations?: Record<string, TranslatedText>;
  descriptionTranslations?: Record<string, TranslatedText>;
  instructions?: ServiceInstructions | null;
  instructionTranslations?: InstructionTranslations;
  /** Latest approved publication, when the card has a public change history. */
  lastUpdatedAt?: string | null;
  /** Structured details of lines of instructions.requiredDocuments; empty or absent when none were provided. */
  documentDetails?: DocumentDetail[];
}

export type DocumentForm = 'ORIGINAL' | 'COPY' | 'ORIGINAL_AND_COPY' | 'FILLED_FORM' | 'OTHER';

/** What the organization says about one required document; every part except the document line is optional. */
export interface DocumentDetail {
  /** The line of requiredDocuments (source language) this detail belongs to. */
  document: string;
  form?: DocumentForm | null;
  copies?: number | null;
  validity?: string | null;
  notes?: string | null;
  /** Link to the organization's approved blank form. */
  formUrl?: string | null;
}

export interface UpdateFunctionRequirementsRequest {
  requirements: string;
}

export interface FunctionQuery {
  organizationId?: number;
  category?: string;
}

export interface LegacyUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: string;
  enabled: boolean;
  organizations: number[];
  mfa: MfaSummary | null;
}

export interface LegacyUserApi {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: string;
  enabled: boolean;
  organizationIds: number[];
  mfa?: MfaSummary | null;
}

export interface CreateLegacyUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone: string;
  roleId: number;
  organizationIds: number[];
}

export interface UpdateLegacyUserRequest {
  firstName: string;
  lastName: string;
  email: string;
}

export type InterfaceTranslationJobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'UNSUPPORTED';

export interface InterfaceTranslationJob {
  status: InterfaceTranslationJobStatus;
  providerCode: string | null;
  attempts: number;
  missingKeys: number;
  translatedKeys: number;
  requestedAt: string;
  nextAttemptAt: string;
  finishedAt: string | null;
  lastError: string | null;
}

/** Coverage of one active interface language (GET /api/interface-translations/overview). */
export interface InterfaceLanguageCoverage {
  languageCode: string;
  /** Azure Translator code used for this language, or null when Azure cannot translate into it. */
  providerCode: string | null;
  supported: boolean;
  englishBase: boolean;
  requiredKeys: number;
  translatedKeys: number;
  missingKeys: number;
  percentage: number;
  seedKeys: number;
  humanKeys: number;
  machineKeys: number;
  staleMachineKeys: number;
  outdatedHumanKeys: number;
  missingSample: string[];
  job: InterfaceTranslationJob | null;
}

/** Outcome of POST /api/interface-translations/{lang}/import (a JSON backup from …/export). */
export interface InterfaceTranslationImportResult {
  languageCode: string;
  created: number;
  updated: number;
  unchanged: number;
  /** Values a person edited in this installation, left as they are. */
  keptHuman: number;
  skipped: number;
  unknownKeys: string[];
}

export interface InterfaceTranslationOverview {
  automatic: boolean;
  providerConfigured: boolean;
  workerEnabled: boolean;
  activeLanguagesAvailable: boolean;
  languages: InterfaceLanguageCoverage[];
}
