import { existsSync, readFileSync } from 'node:fs';
import { expect, type Locator, type Page } from '@playwright/test';
import type { CreateFunctionRequest, AdminFunction } from '../src/types/adminFunctions';
import type { LegacyUserApi, Organization, PublicChange, Region, SessionInfo, StaffMember, InterfaceTranslationOverview } from '../src/types/api';
import type { EngagementCounts, EngagementReport, OrganizationSummary, QualityQueue, QualityReminder } from '../src/types/analytics';
import type { InformationReport, ReportHistoryEntry } from '../src/types/reports';
import type { HelpConversationStatus, SupportContact } from '../src/types/help';
import type { ReportCategory } from '../src/types/reports';
import { FUNCTION_PERMISSIONS } from '../src/utils/functionPermissions';

export const CATALOG = '../reference-service/src/main/resources/i18n/interface';

export const allPermissions = [...FUNCTION_PERMISSIONS, 'FUNCTIONS_MANAGE_ANY_ORGANIZATION'];
export const organization = { id: 10, name: 'zafar', enabled: true, createdAt: '2026-09-01T00:00:00' };
export const languages = [
  { id: 1, code: 'en', name: 'English', nativeName: 'English', defaultLanguage: true },
  { id: 2, code: 'ru', name: 'Russian', nativeName: 'Русский', defaultLanguage: false },
  { id: 3, code: 'uz', name: 'Uzbek', nativeName: 'O‘zbek', defaultLanguage: false },
  { id: 4, code: 'fr', name: 'French', nativeName: 'Français', defaultLanguage: false },
];
/** GET /api/interface-translations/overview with every fixture language complete and no machine translation job. */
export const translationOverview: InterfaceTranslationOverview = {
  automatic: false, providerConfigured: false, workerEnabled: false, activeLanguagesAvailable: true,
  languages: languages.map(({ code }) => ({ languageCode: code, providerCode: code, supported: true, englishBase: code === 'en',
    requiredKeys: 10, translatedKeys: 10, missingKeys: 0, percentage: 100, seedKeys: 10, humanKeys: 0, machineKeys: 0,
    staleMachineKeys: 0, outdatedHumanKeys: 0, missingSample: [], job: null })),
};
/** What reference-service serves: the English catalog overlaid with each level of the language (fr-FR: fr, fr-FR). */
export function dictionary(language: string): Record<string, string> {
  const read = (code: string): Record<string, string> => {
    const file = `${CATALOG}/${code}.json`;
    return existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : {};
  };
  const parts = language.split('-');
  const levels = parts.map((_, index) => parts.slice(0, index + 1).join('-'));
  return Object.assign({}, read('en'), ...levels.filter((code) => code !== 'en').map(read));
}
export async function chooseOption(trigger: Locator, value: string) {
  await trigger.click();
  const listbox = trigger.page().getByRole('listbox');
  await listbox.locator(`[role="option"][data-value="${value}"]`).click();
  await expect(listbox).toBeHidden();
}
export function card(id = 1, status: AdminFunction['status'] = 'DRAFT'): AdminFunction {
  return { id, name: 'Service ' + id, description: 'Description ' + id, requirements: 'Passport',
    organizationId: 10, categoryId: 1, category: 'Legal', status, sourceLanguage: 'en',
    nameTranslations: { ru: { text: 'Услуга ' + id, source: 'human' }, uz: { text: 'Xizmat ' + id, source: 'machine' } },
    descriptionTranslations: { ru: { text: 'Описание ' + id, source: 'human' }, uz: { text: 'Tavsif ' + id, source: 'machine' } } };
}
export interface FixtureOptions {
  records?: AdminFunction[]; permissions?: string[]; role?: string; selfServiceReset?: boolean; legacyUsers?: LegacyUserApi[];
  autoAvailable?: boolean; realBackend?: string; token?: string;
  /** Background translation worker enabled (defaults to autoAvailable). */
  autoAutomatic?: boolean;
  organizations?: Organization[]; regions?: Region[]; reports?: InformationReport[]; anonymous?: boolean;
  summary?: OrganizationSummary; engagement?: EngagementReport; qualityQueue?: QualityQueue; reminders?: QualityReminder[];
  organizationIds?: number[];
  /** Public change history per published service id. */
  history?: Record<number, PublicChange[]>;
  /** Staff members per organization id for assignment candidates; defaults to the signed-in user plus one colleague. */
  members?: Record<number, StaffMember[]>;
  /** Second-factor state of the signed-in user and whether the password step must be followed by a code or enrollment. */
  mfa?: { enrolled: boolean; required: boolean; loginRequires?: 'verify' | 'enroll'; deadline?: string | null };
  sessions?: SessionInfo[];
  /** E-mail notices for support replies configured on the server. */
  supportNotifications?: boolean;
  /** Routed conversations of the one visitor of this page (and what staff see). */
  supportConversations?: SupportFixture[];
  /** The visitor already has a support identity in this browser (cookie) before the test starts. */
  supportIdentity?: 'NONE' | 'BROWSER' | 'ACCOUNT';
}
export interface SupportFixtureMessage {
  id: number; author: 'VISITOR' | 'STAFF'; body: string; createdAt: string; clientMessageId?: string | null; serviceId?: number | null;
  contextLabel?: string | null; category?: ReportCategory | null; reportId?: number | null; readAt?: string | null; staffLabel?: string | null;
}
export interface SupportFixture {
  id: number; status: HelpConversationStatus; organizationId: number | null; serviceId: number | null; serviceName: string | null;
  assignedUserId: number | null; assignedUserName: string | null; messages: SupportFixtureMessage[];
}
const csv = (rows: string[]) => '\uFEFF' + rows.join('\r\n') + '\r\n';
export function counts(values: Partial<EngagementCounts> = {}): EngagementCounts {
  return { CATALOG_VIEW: 0, SERVICE_VIEW: 0, OFFICIAL_LINK_CLICK: 0, PHONE_CLICK: 0, MAP_CLICK: 0, PRINT: 0, ...values };
}
export function summary(overrides: Partial<OrganizationSummary> = {}): OrganizationSummary {
  return {
    generatedAt: '2026-10-09T08:00:00Z',
    period: { from: '2026-09-10', to: '2026-10-09', previousFrom: '2026-08-11', previousTo: '2026-09-09', timeZone: 'Asia/Tashkent' },
    organizationId: null, categoryId: null,
    services: { PUBLISHED: 12, PENDING_REVIEW: 2, DRAFT: 3, DEACTIVATED: 1 },
    servicesNeedingAttention: 4,
    attention: { VERIFICATION_OVERDUE: 2, SOURCE_MISSING: 1, INFORMATION_INCOMPLETE: 1, TRANSLATIONS_MISSING: 3 },
    translationsChecked: true, activeLanguages: ['en', 'ru', 'uz'],
    reports: { unresolved: 3, newReports: 2, inProgress: 1, receivedInPeriod: 4, receivedInPreviousPeriod: 2 },
    openReminders: 1, assignedToMe: { services: 0, openReminders: 0, openReports: 0 }, ...overrides,
  };
}
export async function setup(page: Page, options: FixtureOptions = {}) {
  const records = structuredClone(options.records ?? Array.from({ length: 11 }, (_, i) => card(i + 1, i < 3 ? 'PUBLISHED' : 'DRAFT')));
  const permissions = options.permissions ?? allPermissions;
  const role = options.role ?? 'ROLE_SUPER_ADMIN';
  const organizationIds = options.organizationIds ?? [10];
  const mfa = options.mfa ?? { enrolled: false, required: false };
  const user = { id: 42, firstName: 'Editor', lastName: 'Test', email: 'editor@example.test', phone: null,
    role, enabled: true, mustChangePassword: false, organizationIds,
    organizations: organizationIds.map(id => (id === organization.id ? organization : { id, name: 'Organization ' + id })), permissions,
    mfa: { required: mfa.required, enrolled: mfa.enrolled, enrollmentDeadline: mfa.deadline ?? null, enrollmentOverdue: mfa.required && !mfa.enrolled && !mfa.deadline } };
  const members: Record<number, StaffMember[]> = options.members ?? {
    [organization.id]: [{ id: 42, firstName: 'Editor', lastName: 'Test', email: 'editor@example.test', role },
      { id: 7, firstName: 'Ann', lastName: 'Lee', email: 'ann@example.test', role: 'ROLE_MODERATOR' }] };
  const publicHistory = options.history ?? {};
  const sessions = structuredClone(options.sessions ?? [{ sessionId: 'current', createdAt: '2026-10-12T08:00:00Z', lastUsedAt: '2026-10-12T09:00:00Z',
    expiresAt: '2026-10-19T08:00:00Z', userAgent: 'Firefox on macOS', current: true }]);
  const feedbackVotes: { serviceId: number; helpful: boolean }[] = [];
  const supportConversations: SupportFixture[] = structuredClone(options.supportConversations ?? []);
  const support = { identity: options.supportIdentity ?? (supportConversations.length ? 'BROWSER' : 'NONE'),
    contact: { email: null, notifyByEmail: false } as SupportContact, recoveryCode: null as string | null };
  let supportMessageId = Math.max(100, ...supportConversations.flatMap(item => item.messages.map(message => message.id)));
  const supportMessages = () => supportConversations.flatMap(item => item.messages.map(message => ({ message, conversation: item })))
    .sort((a, b) => a.message.id - b.message.id);
  const visitorMessage = ({ message, conversation }: { message: SupportFixtureMessage; conversation: SupportFixture }) => ({
    id: message.id, author: message.author, body: message.body, createdAt: message.createdAt,
    clientMessageId: message.author === 'VISITOR' ? message.clientMessageId ?? null : null, serviceId: message.serviceId ?? null,
    contextLabel: message.contextLabel ?? null, organizationId: conversation.organizationId, category: message.category ?? null, readAt: message.readAt ?? null });
  const supportUnread = () => {
    const replies = supportMessages().filter(entry => entry.message.author === 'STAFF');
    return { unread: replies.filter(entry => !entry.message.readAt).length, latestReplyId: replies.length ? replies[replies.length - 1].message.id : null };
  };
  const supportThread = () => ({ identity: support.identity, messages: supportMessages().map(visitorMessage), unread: supportUnread().unread,
    contact: support.contact, notificationsAvailable: options.supportNotifications ?? false, recoveryCodeSet: support.recoveryCode !== null });
  const supportSummary = (item: SupportFixture) => {
    const last = item.messages[item.messages.length - 1];
    return { id: item.id, status: item.status, serviceId: item.serviceId, serviceName: item.serviceName, organizationId: item.organizationId,
      general: item.organizationId === null, language: 'en', assignedUserId: item.assignedUserId, assignedUserName: item.assignedUserName,
      createdAt: item.messages[0]?.createdAt ?? '2026-10-16T08:00:00Z', updatedAt: last?.createdAt ?? '2026-10-16T08:00:00Z',
      lastMessageAt: last?.createdAt ?? null, lastMessagePreview: last?.body ?? null, lastMessageAuthor: last?.author ?? null,
      messageCount: item.messages.length, unreadCount: item.messages.filter(message => message.author === 'VISITOR' && !message.readAt).length,
      contactAvailable: Boolean(support.contact.email) };
  };
  const supportDetail = (item: SupportFixture, revealContact: boolean) => ({ conversation: supportSummary(item),
    messages: item.messages.map(message => ({ id: message.id, author: message.author, body: message.body, createdAt: message.createdAt,
      staffUserId: message.author === 'STAFF' ? 42 : null, staffLabel: message.staffLabel ?? null, serviceId: message.serviceId ?? null,
      contextLabel: message.contextLabel ?? null, category: message.category ?? null, reportId: message.reportId ?? null, readAt: message.readAt ?? null })),
    contact: revealContact && support.contact.email ? support.contact : null });
  let mfaEnrolled = mfa.enrolled;
  let pendingSecret: string | null = null;
  let challengePending: 'verify' | 'enroll' | null = null;
  type Payload = Partial<CreateFunctionRequest> & { reason?: string; languages?: string[]; overwriteMachine?: boolean;
    status?: InformationReport['status'] | HelpConversationStatus; note?: string; instructions?: Record<string, string>; email?: string; token?: string; newPassword?: string;
    code?: string; password?: string; serviceId?: number; helpful?: boolean; userId?: number; body?: string; clientMessageId?: string;
    organizationId?: number; reportCategory?: ReportCategory; upTo?: number; message?: string; notifyByEmail?: boolean };
  const organizations = structuredClone(options.organizations ?? [organization as Organization]);
  const reports = structuredClone(options.reports ?? []);
  const reminders = structuredClone(options.reminders ?? []);
  const history = new Map<number, ReportHistoryEntry[]>();
  reports.forEach(item => history.set(item.id, [{ id: item.id * 100, performedByUserId: null, performedBy: 'public:anonymous',
    action: 'REPORT_RECEIVED', performedAt: item.createdAt, details: 'FUNCTION #' + item.entityId }]));
  const canViewReports = permissions.includes('REPORTS_VIEW') || permissions.includes('ORG_REPORTS_MANAGE');
  const canManageReports = permissions.includes('ORG_REPORTS_MANAGE') || (permissions.includes('REPORTS_VIEW') && permissions.includes('REPORTS_MANAGE'));
  const canViewAnalytics = permissions.includes('ORG_ANALYTICS_VIEW');
  const visible = (item: InformationReport) => ({ ...item, contact: canManageReports ? item.contact : null, contactAvailable: item.contact !== null });
  const requests: { method: string; path: string; query: URLSearchParams; body: Payload }[] = [];
  const failures = new Map<string, number>();
  const audits: { id: number; functionId: number; performedBy: string; performedAt: string; action: string; details: string }[] = [];
  const legacyUsers = structuredClone(options.legacyUsers ?? []);
  const resetTokens: { token: string; userId: number; used: boolean }[] = [];
  /** Admin responses carry the pending-revision summary of a published record, like the backend does. */
  const withRevision = (item: AdminFunction): AdminFunction => {
    const revision = records.find(other => other.revisionOfId === item.id);
    return revision ? { ...item, revision: { id: revision.id, status: revision.status } } : { ...item, revision: null };
  };
  await page.addInitScript(() => window.localStorage.setItem('reestr-task-locale', 'en'));
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const body = (request.postData() ? request.postDataJSON() : {}) as Payload;
    const query = new URL(request.url()).searchParams;
    requests.push({ method, path, query, body });
    const reply = (json: unknown, status = 200) => route.fulfill({ status, json });
    const failureKey = method + ' ' + path;
    if (failures.has(failureKey)) {
      const status = failures.get(failureKey) ?? 500; failures.delete(failureKey);
      return reply({ message: 'Please retry this operation' }, status);
    }
    if (path.startsWith('/api/functions') && options.realBackend) {
      const response = await route.fetch({ url: options.realBackend + path,
        headers: { ...request.headers(), cookie: 'accessToken=' + options.token } });
      return route.fulfill({ response });
    }
    if (path === '/api/auth/password-reset/options') return reply({ selfServiceAvailable: options.selfServiceReset ?? false });
    if (path === '/api/auth/password-reset/request' && method === 'POST') {
      if (!options.selfServiceReset) return reply({ message: 'Self-service password reset is not configured' }, 503);
      return reply({ accepted: true }, 202);
    }
    if (path === '/api/auth/password-reset/complete' && method === 'POST') {
      const found = resetTokens.find(item => item.token === body.token && !item.used);
      if (!found || (body.newPassword ?? '').length < 8) return reply({ message: 'This password reset link is invalid or has expired' }, 400);
      found.used = true;
      return reply({ reset: true });
    }
    const adminReset = path.match(/^\/api\/user\/(\d+)\/password-reset$/);
    if (adminReset && method === 'POST') {
      if (role !== 'ROLE_SUPER_ADMIN') return reply({ message: 'Forbidden' }, 403);
      const token = 'token-' + adminReset[1] + '-' + (resetTokens.length + 1);
      resetTokens.forEach(item => { if (item.userId === Number(adminReset[1])) item.used = true; });
      resetTokens.push({ token, userId: Number(adminReset[1]), used: false });
      return reply({ userId: Number(adminReset[1]), resetPath: '/reset-password?token=' + token, expiresAt: '2026-10-12T12:00:00Z' }, 201);
    }
    if (path === '/api/user' && method === 'GET') return reply(legacyUsers);
    if (path === '/api/auth/me') return options.anonymous ? reply({ message: 'Authentication is required' }, 401) : reply({ ...user, mfa: { ...user.mfa, enrolled: mfaEnrolled } });
    if (path === '/api/auth/refresh') return reply({}, 401);
    if (path === '/api/auth/login') {
      if (mfa.loginRequires) {
        challengePending = mfa.loginRequires;
        return reply({ mfaRequired: true, enrollmentRequired: mfa.loginRequires === 'enroll', expiresAt: '2026-10-12T09:05:00Z' }, 202);
      }
      return reply(user);
    }
    if (path === '/api/auth/logout') return reply({});
    if (path === '/api/auth/mfa' && method === 'GET') {
      return reply({ enabled: true, required: mfa.required, enrolled: mfaEnrolled, confirmedAt: mfaEnrolled ? '2026-10-01T00:00:00Z' : null,
        enrollmentDeadline: mfa.deadline ?? null, enrollmentOverdue: mfa.required && !mfaEnrolled && !mfa.deadline, recoveryCodesRemaining: mfaEnrolled ? 8 : 0 });
    }
    if (path === '/api/auth/mfa/verify' && method === 'POST') {
      if (challengePending !== 'verify') return reply({ message: 'Sign in again' }, 401);
      if (body.code !== '123456' && body.code !== 'ABCDE-FGHJK') return reply({ message: 'Invalid verification code' }, 401);
      challengePending = null;
      return reply({ ...user, mfa: { ...user.mfa, enrolled: true } });
    }
    if (path === '/api/auth/mfa/enroll' && method === 'POST') {
      if (options.anonymous && challengePending !== 'enroll') return reply({ message: 'Sign in again' }, 401);
      pendingSecret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
      return reply({ secret: pendingSecret, otpauthUri: 'otpauth://totp/ReestrGov:editor%40example.test?secret=' + pendingSecret + '&issuer=ReestrGov', issuer: 'ReestrGov', account: user.email });
    }
    if (path === '/api/auth/mfa/confirm' && method === 'POST') {
      if (!pendingSecret) return reply({ message: 'Start enrollment before confirming it' }, 409);
      if (body.code !== '123456') return reply({ message: 'Invalid verification code' }, 401);
      pendingSecret = null; mfaEnrolled = true;
      const fromChallenge = challengePending === 'enroll';
      challengePending = null;
      return reply({ recoveryCodes: ['AAAAA-BBBBB', 'CCCCC-DDDDD', 'EEEEE-FFFFF'], user: fromChallenge ? { ...user, mfa: { ...user.mfa, enrolled: true } } : null });
    }
    if (path === '/api/auth/mfa/disable' && method === 'POST') {
      if (body.password !== 'Password123!' || body.code !== '123456') return reply({ message: 'Invalid verification code' }, 401);
      mfaEnrolled = false;
      return reply({ enabled: true, required: mfa.required, enrolled: false, confirmedAt: null, enrollmentDeadline: null, enrollmentOverdue: mfa.required, recoveryCodesRemaining: 0 });
    }
    if (path === '/api/auth/sessions' && method === 'GET') return reply(sessions);
    const ownSession = path.match(/^\/api\/auth\/sessions\/([^/]+)$/);
    if (ownSession && method === 'DELETE') {
      const index = sessions.findIndex(item => item.sessionId === ownSession[1]);
      if (index < 0) return reply({ message: 'Session not found' }, 404);
      sessions.splice(index, 1);
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/auth/sessions/revoke-others' && method === 'POST') {
      const removed = sessions.filter(item => !item.current).length;
      sessions.splice(0, sessions.length, ...sessions.filter(item => item.current));
      return reply({ revoked: removed });
    }
    const userSessions = path.match(/^\/api\/user\/(\d+)\/sessions(?:\/([^/]+))?$/);
    if (userSessions) {
      if (role !== 'ROLE_SUPER_ADMIN') return reply({ message: 'Forbidden' }, 403);
      if (method === 'GET') return reply(sessions.map(item => ({ ...item, current: false })));
      if (method === 'DELETE' && userSessions[2]) { sessions.splice(sessions.findIndex(item => item.sessionId === userSessions[2]), 1); return route.fulfill({ status: 204 }); }
      if (method === 'DELETE') { sessions.splice(0, sessions.length); return route.fulfill({ status: 204 }); }
    }
    const userMfa = path.match(/^\/api\/user\/(\d+)\/mfa(?:\/(reset))?$/);
    if (userMfa) {
      if (role !== 'ROLE_SUPER_ADMIN') return reply({ message: 'Forbidden' }, 403);
      if (userMfa[2] === 'reset' && method === 'POST') {
        const found = legacyUsers.find(item => item.id === Number(userMfa[1]));
        if (found?.mfa) found.mfa = { ...found.mfa, enrolled: false };
        return route.fulfill({ status: 204 });
      }
      return reply({ enabled: true, required: true, enrolled: true, confirmedAt: '2026-10-01T00:00:00Z', enrollmentDeadline: null, enrollmentOverdue: false, recoveryCodesRemaining: 8 });
    }
    const orgMembers = path.match(/^\/api\/organizations\/(\d+)\/members$/);
    if (orgMembers && method === 'GET') return reply(members[Number(orgMembers[1])] ?? []);
    if (path === '/api/feedback' && method === 'POST') {
      const target = records.find(item => item.id === body.serviceId && item.status === 'PUBLISHED');
      if (!target) return reply({ message: 'Service not found' }, 404);
      feedbackVotes.push({ serviceId: body.serviceId as number, helpful: body.helpful as boolean });
      return reply({}, 202);
    }
    if (path === '/api/languages') return reply(languages);
    if (path === '/api/interface-translations/overview') return reply(translationOverview);
    if (path.startsWith('/api/interface-translations/')) return reply(dictionary(path.split('/').at(-1) ?? 'en'));
    if (path === '/api/public/organizations' || path === '/api/organizations') return reply(organizations);
    const organizationMatch = path.match(/^\/api\/(?:public\/)?organizations\/(\d+)(?:\/(verify))?$/);
    if (organizationMatch) {
      const found = organizations.find(item => item.id === Number(organizationMatch[1]));
      if (!found) return reply({ message: 'Organization not found' }, 404);
      if (method === 'POST' && organizationMatch[2] === 'verify') {
        Object.assign(found, { lastVerifiedAt: new Date().toISOString(), verificationStatus: 'VERIFIED', verifiedByUserId: user.id });
      } else if (method === 'PUT') {
        Object.assign(found, body);
      }
      return reply(found);
    }
    if (path === '/api/regions') return reply(options.regions ?? []);
    if (path.startsWith('/api/admin/')) return reply([]);
    if (path === '/api/reports' && method === 'POST') return reply({ received: true }, 202);
    if (path === '/api/analytics/events' && method === 'POST') return reply({}, 202);
    if (path.startsWith('/api/analytics/')) {
      if (!canViewAnalytics) return reply({ message: 'Forbidden' }, 403);
      if (path === '/api/analytics/summary') return reply(options.summary ?? summary());
      if (path === '/api/analytics/engagement') return options.engagement ? reply(options.engagement) : reply({ message: 'No engagement fixture' }, 500);
      if (path === '/api/analytics/quality-queue') {
        const queue = options.qualityQueue ?? { translationsChecked: true, activeLanguages: ['en'], items: [] };
        const issue = query.get('issue');
        const status = query.get('status');
        return reply({ ...queue, items: queue.items.filter(item => (!issue || item.issues.some(found => found.type === issue)) && (!status || item.status === status)) });
      }
      if (path === '/api/analytics/reminders') {
        const mine = query.get('assignee') === 'me' ? reminders.filter(item => item.assignedUserId === user.id) : reminders;
        return reply({ total: mine.length, items: mine });
      }
      if (path === '/api/analytics/feedback') {
        const totals = (votes: typeof feedbackVotes) => ({ helpful: votes.filter(v => v.helpful).length, notHelpful: votes.filter(v => !v.helpful).length, total: votes.length });
        const byService = [...new Set(feedbackVotes.map(v => v.serviceId))].map(id => {
          const record = records.find(item => item.id === id);
          const own = feedbackVotes.filter(v => v.serviceId === id);
          return { functionId: id, name: record?.name ?? null, nameTranslations: record?.nameTranslations ?? null, status: record?.status ?? null,
            organizationId: record?.organizationId ?? null, categoryId: record?.categoryId ?? null, category: record?.category ?? null, ...totals(own) };
        });
        return reply({ period: summary().period, totals: totals(feedbackVotes), previousTotals: { helpful: 0, notHelpful: 0, total: 0 }, services: byService });
      }
      const acknowledged = path.match(/^\/api\/analytics\/reminders\/(\d+)\/acknowledge$/);
      if (acknowledged && method === 'POST') {
        const index = reminders.findIndex(item => item.id === Number(acknowledged[1]));
        if (index < 0) return reply({ message: 'Reminder not found' }, 404);
        reminders.splice(index, 1);
        return route.fulfill({ status: 204 });
      }
      if (path.endsWith('/export') && query.get('format')) {
        const extension = query.get('format') === 'PDF' ? 'pdf' : 'xlsx';
        return route.fulfill({ status: 200, contentType: extension === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          body: 'binary', headers: { 'Content-Disposition': 'attachment; filename="organization-report.' + extension + '"', 'X-Export-Truncated': 'false' } });
      }
      if (path.endsWith('/export')) {
        return route.fulfill({ status: 200, contentType: 'text/csv;charset=UTF-8', body: csv(['service_id,service_name', '1,Service 1']),
          headers: { 'Content-Disposition': 'attachment; filename="' + path.split('/')[3] + '-export.csv"', 'X-Export-Truncated': 'false' } });
      }
    }
    if (path === '/api/reports/export' && method === 'GET') {
      if (!canViewReports) return reply({ message: 'Forbidden' }, 403);
      if (query.get('format') === 'XLSX' || query.get('format') === 'PDF') {
        const extension = query.get('format') === 'PDF' ? 'pdf' : 'xlsx';
        return route.fulfill({ status: 200, contentType: extension === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          body: 'binary', headers: { 'Content-Disposition': 'attachment; filename="visitor-reports.' + extension + '"', 'X-Export-Truncated': 'false' } });
      }
      return route.fulfill({ status: 200, contentType: 'text/csv;charset=UTF-8', body: csv(['report_id,status', '1,NEW']),
        headers: { 'Content-Disposition': 'attachment; filename="visitor-reports.csv"', 'X-Export-Truncated': 'false' } });
    }
    if (path === '/api/reports' && method === 'GET') {
      if (!canViewReports) return reply({ message: 'Forbidden' }, 403);
      const status = query.get('status') ?? 'OPEN';
      const category = query.get('category');
      const entityId = query.get('entityId');
      return reply(reports.filter(item => (status === 'ALL' || (status === 'OPEN' ? ['NEW', 'IN_PROGRESS'].includes(item.status) : item.status === status))
        && (!category || item.category === category) && (!entityId || item.entityId === Number(entityId))).map(visible));
    }
    const reportDetail = path.match(/^\/api\/reports\/(\d+)(?:\/(history|status))?$/);
    if (reportDetail) {
      if (!canViewReports) return reply({ message: 'Forbidden' }, 403);
      const found = reports.find(item => item.id === Number(reportDetail[1]));
      if (!found) return reply({ message: 'Report not found' }, 404);
      if (reportDetail[2] === 'history') return reply(history.get(found.id) ?? []);
      if (reportDetail[2] === 'status' && method === 'PUT') {
        if (!canManageReports) return reply({ message: 'Forbidden' }, 403);
        const note = (body.note ?? '').trim();
        if (body.status === 'REJECTED' && !note) {
          return reply({ message: 'note: An explanation is required to reject a report', fieldErrors: { note: 'An explanation is required to reject a report' } }, 400);
        }
        const previous = found.status;
        Object.assign(found, { status: body.status, resolutionNote: note || found.resolutionNote, handledByUserId: user.id,
          contact: body.status === 'RESOLVED' || body.status === 'REJECTED' ? null : found.contact });
        history.get(found.id)?.push({ id: Date.now(), performedByUserId: user.id, performedBy: user.email, action: 'REPORT_STATUS_CHANGE',
          performedAt: new Date().toISOString(), details: 'Report #' + found.id + ': ' + previous + ' -> ' + found.status + (note ? '\n' + note : '') });
        return reply(visible(found));
      }
      return reply(visible(found));
    }
    if (path.startsWith('/api/help/')) {
      const canView = permissions.includes('VISITOR_QUESTIONS_VIEW');
      const canReply = canView && permissions.includes('VISITOR_QUESTIONS_REPLY');
      const global = role === 'ROLE_SUPER_ADMIN';
      const visibleToStaff = (item: SupportFixture) => (item.organizationId === null ? global : global || organizationIds.includes(item.organizationId));
      const now = () => new Date().toISOString();
      if (path === '/api/help/options') return reply({ notificationsAvailable: options.supportNotifications ?? false, maxLength: 2000 });
      if (path === '/api/help/support' && method === 'GET') return reply(supportThread());
      if (path === '/api/help/support/unread') return reply(supportUnread());
      if (path === '/api/help/support/updates') {
        const after = Number(query.get('after') ?? '0');
        return reply({ messages: supportMessages().filter(entry => entry.message.id > after).map(visitorMessage), ...supportUnread() });
      }
      if (path === '/api/help/support/messages' && method === 'POST') {
        const text = (body.body ?? '').trim();
        if (!text) return reply({ message: 'body: Write a message', fieldErrors: { body: 'Write a message' } }, 400);
        const repeated = supportMessages().find(entry => entry.message.clientMessageId === body.clientMessageId);
        if (repeated) return reply(visitorMessage(repeated), 201);
        const service = body.serviceId ? records.find(item => item.id === body.serviceId && item.status === 'PUBLISHED') : undefined;
        if (body.serviceId && !service) return reply({ message: 'Service not found' }, 404);
        if (body.reportCategory && !service && !body.organizationId) return reply({ message: 'reportCategory: choose', fieldErrors: { reportCategory: 'Choose the service or organization the report is about' } }, 400);
        const organizationId = service?.organizationId ?? body.organizationId ?? null;
        let conversation = supportConversations.find(item => item.organizationId === organizationId);
        if (!conversation) {
          conversation = { id: supportConversations.length + 1, status: 'NEW', organizationId, serviceId: service?.id ?? null, serviceName: service?.name ?? null,
            assignedUserId: null, assignedUserName: null, messages: [] };
          supportConversations.push(conversation);
        }
        let reportId: number | null = null;
        if (body.reportCategory) {
          reportId = reports.length + 1;
          reports.push({ id: reportId, entityType: service ? 'FUNCTION' : 'ORGANIZATION', entityId: service?.id ?? (body.organizationId as number),
            entityLabel: service?.name ?? 'Organization', organizationId: organizationId ?? 10, category: body.reportCategory, description: text, contact: null,
            contactAvailable: false, language: 'en', status: 'NEW', resolutionNote: null, handledByUserId: null, createdAt: now(), updatedAt: now(),
            serviceChangedSinceReport: null } as InformationReport);
        }
        const message: SupportFixtureMessage = { id: ++supportMessageId, author: 'VISITOR', body: text, createdAt: now(), clientMessageId: body.clientMessageId,
          serviceId: service?.id ?? null, contextLabel: service?.name ?? null, category: body.reportCategory ?? null, reportId, readAt: null };
        conversation.messages.push(message);
        if (conversation.status === 'CLOSED') conversation.status = 'NEW';
        else if (conversation.status === 'ANSWERED') conversation.status = 'IN_PROGRESS';
        if (support.identity === 'NONE') support.identity = 'BROWSER';
        return reply(visitorMessage({ message, conversation }), 201);
      }
      if (path === '/api/help/support/read' && method === 'POST') {
        supportMessages().forEach(entry => { if (entry.message.author === 'STAFF' && entry.message.id <= (body.upTo ?? 0) && !entry.message.readAt) entry.message.readAt = now(); });
        return reply(supportUnread());
      }
      if (path === '/api/help/support/contact' && method === 'PUT') {
        support.contact = { email: body.email || null, notifyByEmail: Boolean(body.email && body.notifyByEmail) };
        return reply(support.contact);
      }
      if (path === '/api/help/support/recovery-code' && method === 'POST') {
        support.recoveryCode = 'ABCDE-FGHJK-LMNPQ-RSTVW';
        return reply({ code: support.recoveryCode });
      }
      if (path === '/api/help/support/recover' && method === 'POST') {
        const canonical = (value: string | null | undefined) => (value ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
        if (!support.recoveryCode || canonical(body.code) !== canonical(support.recoveryCode)) {
          return reply({ message: 'code: This recovery code is not valid', fieldErrors: { code: 'This recovery code is not valid' } }, 400);
        }
        support.identity = 'BROWSER';
        return reply(supportThread());
      }
      if (path === '/api/help/support/session' && method === 'DELETE') {
        support.identity = 'NONE';
        return route.fulfill({ status: 204 });
      }
      if (!canView) return reply({ message: 'Forbidden' }, 403);
      if (path === '/api/help/inbox/unread') {
        const mine = supportConversations.filter(visibleToStaff).map(supportSummary).filter(item => item.unreadCount > 0);
        return reply({ conversations: mine.length, messages: mine.reduce((sum, item) => sum + item.unreadCount, 0) });
      }
      if (path === '/api/help/inbox') {
        const status = query.get('status') ?? 'ACTIVE';
        return reply(supportConversations.filter(visibleToStaff)
          .filter(item => status === 'ALL' || (status === 'ACTIVE' ? item.status !== 'CLOSED' : status === 'OPEN' ? ['NEW', 'IN_PROGRESS'].includes(item.status) : item.status === status))
          .filter(item => query.get('general') !== 'true' || item.organizationId === null)
          .map(supportSummary).filter(item => query.get('unread') !== 'true' || item.unreadCount > 0));
      }
      const inbox = path.match(/^\/api\/help\/inbox\/(\d+)(?:\/(replies|status|candidates|assignee|read))?$/);
      const found = inbox ? supportConversations.find(item => item.id === Number(inbox[1])) : undefined;
      if (!inbox || !found) return reply({ message: 'Conversation not found' }, 404);
      if (!visibleToStaff(found)) return reply({ message: 'Forbidden' }, 403);
      if (!inbox[2]) return reply(supportDetail(found, canReply));
      if (inbox[2] === 'read') {
        found.messages.forEach(message => { if (message.author === 'VISITOR' && message.id <= (body.upTo ?? 0) && !message.readAt) message.readAt = now(); });
        return reply(supportDetail(found, canReply));
      }
      if (!canReply) return reply({ message: 'Forbidden' }, 403);
      if (inbox[2] === 'candidates') return reply(members[found.organizationId ?? 0] ?? []);
      if (inbox[2] === 'replies') {
        if (found.status === 'CLOSED') return reply({ message: 'Reopen the conversation before replying' }, 409);
        if (!found.messages.some(message => message.author === 'STAFF' && message.clientMessageId && message.clientMessageId === body.clientMessageId)) {
          found.messages.forEach(message => { if (message.author === 'VISITOR' && !message.readAt) message.readAt = now(); });
          found.messages.push({ id: ++supportMessageId, author: 'STAFF', body: String(body.message ?? ''), createdAt: now(), staffLabel: user.email,
            clientMessageId: body.clientMessageId, readAt: null });
          found.status = 'ANSWERED';
          if (!found.assignedUserId) { found.assignedUserId = user.id; found.assignedUserName = user.email; }
        }
        return reply(supportDetail(found, canReply));
      }
      if (inbox[2] === 'status') { found.status = body.status as HelpConversationStatus; return reply(supportDetail(found, canReply)); }
      if (inbox[2] === 'assignee') {
        const member = (members[found.organizationId ?? 0] ?? []).find(item => item.id === body.userId);
        found.assignedUserId = member?.id ?? null;
        found.assignedUserName = member ? [member.firstName, member.lastName].filter(Boolean).join(' ') : null;
        return reply(supportDetail(found, canReply));
      }
    }
    if (path === '/api/functions/categories') return reply([{ id: 1, name: 'Legal', nameTranslations: { ru: { text: 'Правовые услуги', source: 'human' } } }]);
    if (path === '/api/functions/assignments') {
      if (!['FUNCTIONS_VIEW', 'FUNCTIONS_ASSIGN', 'ORG_ANALYTICS_VIEW'].some(p => permissions.includes(p))) return reply({ message: 'Forbidden' }, 403);
      const items = records.filter(item => !item.revisionOfId && item.organizationId !== null).map(item => {
        const active = (members[item.organizationId ?? 0] ?? []).some(member => member.id === item.assignee?.userId);
        const state = !item.assignee ? 'UNASSIGNED' : active ? 'ASSIGNED' : 'NEEDS_REASSIGNMENT';
        return { functionId: item.id, name: item.name, nameTranslations: item.nameTranslations ?? null, status: item.status, organizationId: item.organizationId,
          categoryId: item.categoryId ?? null, category: item.category ?? null, assignee: item.assignee ?? null, state };
      });
      const wanted = query.get('state');
      return reply({ counts: { assigned: items.filter(i => i.state === 'ASSIGNED').length, unassigned: items.filter(i => i.state === 'UNASSIGNED').length,
        needsReassignment: items.filter(i => i.state === 'NEEDS_REASSIGNMENT').length }, membersChecked: true, items: items.filter(i => !wanted || i.state === wanted) });
    }
    if (path === '/api/functions/translation-capabilities') return reply({ available: options.autoAvailable ?? false, automatic: options.autoAutomatic ?? options.autoAvailable ?? false });
    if (path === '/api/functions/translations/backfill') return reply({ examined: 0, queued: 0, alreadyQueued: 0, limit: 50, limitReached: false }, 202);
    if (path === '/api/functions/admin') return permissions.includes('FUNCTIONS_VIEW') ? reply(records.map(withRevision)) : reply({}, 403);
    if (path === '/api/functions' && method === 'GET') return reply(records.filter(item => item.status === 'PUBLISHED'));
    if (path === '/api/functions' && method === 'POST') {
      const created: AdminFunction = { name: body.name ?? '', description: body.description ?? '', requirements: body.requirements ?? '',
        organizationId: body.organizationId ?? 10, sourceLanguage: body.sourceLanguage ?? 'en', id: Math.max(0, ...records.map(item => item.id)) + 1,
        status: 'DRAFT', categoryId: body.categoryId ?? null, category: body.categoryId ? 'Legal' : null,
        nameTranslations: {}, descriptionTranslations: {} };
      records.push(created); return reply(created, 201);
    }
    const match = path.match(/^\/api\/functions\/(\d+)(?:\/(.*))?$/);
    if (!match) return reply({ message: 'Unexpected API path ' + path }, 404);
    const record = records.find(item => item.id === Number(match[1]));
    if (!record) return reply({ message: 'Not found' }, 404);
    const action = match[2];
    if (action === 'admin') return reply(withRevision(record));
    if (action === 'translation-job' && method === 'POST') {
      if (!permissions.includes('FUNCTIONS_TRANSLATIONS_EDIT')) return reply({ message: 'Forbidden' }, 403);
      record.machineTranslation = { automatic: true, status: 'PENDING', attempts: 0, requestedAt: '2026-10-15T08:00:00Z', nextAttemptAt: '2026-10-15T08:00:00Z', lastError: null, missingLanguages: record.machineTranslation?.missingLanguages ?? [] };
      return reply(record.machineTranslation, 202);
    }
    if (action === 'history' && method === 'GET') return record.status === 'PUBLISHED' ? reply(publicHistory[record.id] ?? []) : reply({ message: 'Not found' }, 404);
    if (action === 'assignee/candidates') {
      if (!permissions.includes('FUNCTIONS_ASSIGN')) return reply({ message: 'Forbidden' }, 403);
      return reply(members[record.organizationId ?? 0] ?? []);
    }
    if (action === 'assignee') {
      if (!permissions.includes('FUNCTIONS_ASSIGN')) return reply({ message: 'Forbidden' }, 403);
      if (method === 'DELETE') { record.assignee = null; return reply(withRevision(record)); }
      const member = (members[record.organizationId ?? 0] ?? []).find(item => item.id === body.userId);
      if (!member) return reply({ message: 'User ' + body.userId + ' is not an active member of organization ' + record.organizationId }, 400);
      record.assignee = { userId: member.id, name: [member.firstName, member.lastName].filter(Boolean).join(' '), email: member.email, assignedAt: new Date().toISOString() };
      audits.push({ id: audits.length + 1, functionId: record.id, performedBy: user.email, performedAt: new Date().toISOString(), action: 'ASSIGNEE_CHANGE', details: 'Responsible staff: nobody -> #' + member.id });
      return reply(withRevision(record));
    }
    if (action === 'verify' && method === 'POST') {
      Object.assign(record, { lastVerifiedAt: new Date().toISOString(), verificationStatus: 'VERIFIED', verifiedByUserId: user.id });
      return reply(record);
    }
    if (action === 'revisions' && method === 'POST') {
      if (record.status !== 'PUBLISHED') return reply({ message: 'Only PUBLISHED cards are edited through a revision' }, 409);
      const existing = records.find(item => item.revisionOfId === record.id);
      if (existing) return reply(existing);
      const revision: AdminFunction = { ...structuredClone(record), revision: null, id: Math.max(0, ...records.map(item => item.id)) + 1, status: 'DRAFT', revisionOfId: record.id };
      records.push(revision);
      audits.push({ id: audits.length + 1, functionId: record.id, performedBy: user.email, performedAt: new Date().toISOString(),
        action: 'REVISION_CREATE', details: 'Draft revision #' + revision.id + ' opened; public content unchanged' });
      return reply(revision);
    }
    if (action === 'revision' && method === 'DELETE') {
      const index = records.findIndex(item => item.revisionOfId === record.id);
      if (index < 0) return reply({ message: 'No draft revision' }, 404);
      if (records[index].status !== 'DRAFT') return reply({ message: 'Withdraw the revision from review before discarding it' }, 409);
      records.splice(index, 1);
      return route.fulfill({ status: 204 });
    }
    if (action === 'publish' && record.revisionOfId) {
      if (record.status !== 'PENDING_REVIEW') return reply({ message: 'Expected PENDING_REVIEW' }, 409);
      const published = records.find(item => item.id === record.revisionOfId);
      if (!published) return reply({ message: 'Published card missing' }, 409);
      Object.assign(published, structuredClone(record), { id: published.id, status: 'PUBLISHED', revisionOfId: undefined, revision: null });
      records.splice(records.indexOf(record), 1);
      audits.push({ id: audits.length + 1, functionId: published.id, performedBy: user.email, performedAt: new Date().toISOString(),
        action: 'PUBLISH', details: 'Revision #' + record.id + ' approved: public content replaced' });
      return reply(published);
    }
    if (action === 'audit') return reply(audits.filter(entry => entry.functionId === record.id));
    if (action === 'guide.pdf') {
      if (record.status !== 'PUBLISHED') return reply({ message: 'Not found' }, 404);
      return route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.4 guide',
        headers: { 'Content-Disposition': 'attachment; filename="service-guide-' + record.id + '-' + (query.get('lang') ?? 'en') + '.pdf"' } });
    }
    if (method === 'GET') return record.status === 'PUBLISHED' ? reply(record) : reply({}, 404);
    if (method === 'PUT' && !action) Object.assign(record, body);
    else if (method === 'PUT' && action === 'requirements') record.requirements = body.requirements ?? '';
    else if (method === 'PUT' && action?.startsWith('translations/')) {
      const language = action.split('/')[1];
      record.nameTranslations ??= {};
      record.nameTranslations[language] = { text: body.name ?? '', source: 'human' };
      record.descriptionTranslations ??= {};
      record.descriptionTranslations[language] = { text: body.description ?? '', source: 'human' };
      for (const [field, text] of Object.entries(body.instructions ?? {})) {
        const key = field as keyof NonNullable<AdminFunction['instructionTranslations']>;
        record.instructionTranslations ??= {};
        const values = { ...(record.instructionTranslations[key] ?? {}) };
        if (text) values[language] = { text, source: 'human' };
        else delete values[language];
        record.instructionTranslations[key] = values;
      }
    } else if (action === 'translate') {
      for (const language of body.languages ?? []) {
        for (const field of ['nameTranslations', 'descriptionTranslations'] as const) {
          const existing = record[field]?.[language];
          if (!existing || (body.overwriteMachine && existing.source === 'machine')) {
            const values = record[field] ?? {};
            values[language] = { text: 'Translated ' + language, source: 'machine' };
            record[field] = values;
          }
        }
      }
    } else if (action === 'submit-for-review' || action === 'reactivate') record.status = 'PENDING_REVIEW';
    else if (action === 'publish') record.status = 'PUBLISHED';
    else if (action === 'reject' || action === 'withdraw') record.status = 'DRAFT';
    else if (method === 'DELETE') record.status = 'DEACTIVATED';
    else return reply({ message: 'Unexpected action' }, 400);
    audits.push({ id: audits.length + 1, functionId: record.id, performedBy: user.email,
      performedAt: new Date().toISOString(), action: action ?? 'UPDATE', details: body?.reason ?? 'Changed card' });
    return method === 'DELETE' ? route.fulfill({ status: 204 }) : reply(record);
  });
  return { records, requests, failures, audits, organizations, reports, reminders, resetTokens, feedbackVotes, sessions, supportConversations, support };
}
