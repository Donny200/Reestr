import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { Loader2Icon } from 'lucide-react';
import { queryClient } from './lib/queryClient';
import { ThemeProvider } from './contexts/ThemeContext';
import { I18nProvider } from './contexts/I18nContext';
import { AuthProvider } from './contexts/AuthContext';
import { PublicLayout } from './components/layout/PublicLayout';
import { AdminLayout } from './components/layout/AdminLayout';
import { RequireAuth } from './components/auth/RequireAuth';
import { AppErrorBoundary } from './components/ui/AppErrorBoundary';
import { TooltipProvider } from './components/ui/Tooltip';
import { Toaster } from './components/ui/Toaster';
import { useI18n } from './contexts/i18n';
import { APPOINTMENT_VIEW_PERMISSIONS } from './features/appointments/access';
import { HELP_VIEW_PERMISSION } from './features/help/helpOptions';
import { REPORT_VIEW_PERMISSIONS } from './features/reports/reportOptions';
import { FUNCTION_PERMISSIONS } from './utils/functionPermissions';

const Home = lazy(() => import('./pages/Home').then((module) => ({ default: module.Home })));
const OrganizationDetail = lazy(() => import('./pages/OrganizationDetail').then((module) => ({ default: module.OrganizationDetail })));
const FunctionDetail = lazy(() => import('./pages/FunctionDetail').then((module) => ({ default: module.FunctionDetail })));
const ServiceFinder = lazy(() => import('./pages/ServiceFinder').then((module) => ({ default: module.ServiceFinder })));
const SavedServices = lazy(() => import('./pages/SavedServices').then((module) => ({ default: module.SavedServices })));
const BookAppointment = lazy(() => import('./pages/BookAppointment').then((module) => ({ default: module.BookAppointment })));
const AppointmentManage = lazy(() => import('./pages/AppointmentManage').then((module) => ({ default: module.AppointmentManage })));
const Appointments = lazy(() => import('./pages/admin/Appointments').then((module) => ({ default: module.Appointments })));
const Support = lazy(() => import('./pages/Support').then((module) => ({ default: module.Support })));
const VisitorQuestions = lazy(() => import('./pages/admin/VisitorQuestions').then((module) => ({ default: module.VisitorQuestions })));
const MfaChallenge = lazy(() => import('./pages/MfaChallenge').then((module) => ({ default: module.MfaChallenge })));
const Reports = lazy(() => import('./pages/admin/Reports').then((module) => ({ default: module.Reports })));
const OrganizationDashboard = lazy(() =>
  import('./pages/admin/OrganizationDashboard').then((module) => ({ default: module.OrganizationDashboard })),
);
const Login = lazy(() => import('./pages/Login').then((module) => ({ default: module.Login })));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword').then((module) => ({ default: module.ForgotPassword })));
const ResetPassword = lazy(() => import('./pages/ResetPassword').then((module) => ({ default: module.ResetPassword })));
const NotFound = lazy(() => import('./pages/NotFound').then((module) => ({ default: module.NotFound })));
const SecuritySettings = lazy(() => import('./pages/SecuritySettings').then((module) => ({ default: module.SecuritySettings })));
const Dashboard = lazy(() => import('./pages/admin/Dashboard').then((module) => ({ default: module.Dashboard })));
const Organizations = lazy(() => import('./pages/admin/Organizations').then((module) => ({ default: module.Organizations })));
const OrgAdmins = lazy(() => import('./pages/admin/OrgAdmins').then((module) => ({ default: module.OrgAdmins })));
const Moderators = lazy(() => import('./pages/admin/Moderators').then((module) => ({ default: module.Moderators })));
const Roles = lazy(() => import('./pages/admin/Roles').then((module) => ({ default: module.Roles })));
const Languages = lazy(() => import('./pages/admin/Languages').then((module) => ({ default: module.Languages })));
const LegacyUsers = lazy(() => import('./pages/admin/LegacyUsers').then((module) => ({ default: module.LegacyUsers })));
const AdminFunctionsPage = lazy(() => import('./pages/admin/AdminFunctionsPage').then((module) => ({ default: module.AdminFunctionsPage })));
const CreateFunctionPage = lazy(() => import('./pages/admin/CreateFunctionPage').then((module) => ({ default: module.CreateFunctionPage })));
const EditFunctionPage = lazy(() => import('./pages/admin/EditFunctionPage').then((module) => ({ default: module.EditFunctionPage })));
const LegacyFunctionEditorRedirect = lazy(() =>
  import('./pages/admin/EditFunctionPage').then((module) => ({ default: module.LegacyFunctionEditorRedirect })),
);

function RouteLoading() {
  const { t } = useI18n();
  return (
    <div className="flex min-h-[45vh] items-center justify-center" role="status" aria-live="polite">
      <span className="inline-flex items-center gap-3 text-sm font-medium text-secondary">
        <Loader2Icon className="h-5 w-5 animate-spin text-foreground motion-reduce:animate-none" aria-hidden="true" />
        {t('state.loading')}
      </span>
    </div>
  );
}

/** Earlier links to the question form (/help?service=…) open the support thread with the same subject. */
function LegacyHelpRedirect() {
  const { search } = useLocation();
  return <Navigate to={`/support${search}`} replace />;
}

function PageBoundary({ children }: { children: ReactNode }) {
  return <Suspense fallback={<RouteLoading />}>{children}</Suspense>;
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TooltipProvider>
          <BrowserRouter>
            <I18nProvider>
              <AuthProvider>
                <AppErrorBoundary>
                  <Routes>
                    <Route element={<PublicLayout />}>
                      <Route path="/" element={<PageBoundary><Home /></PageBoundary>} />
                      <Route path="/organizations/:id" element={<PageBoundary><OrganizationDetail /></PageBoundary>} />
                      <Route path="/functions/:id" element={<PageBoundary><FunctionDetail /></PageBoundary>} />
                      <Route path="/functions/:id/book" element={<PageBoundary><BookAppointment /></PageBoundary>} />
                      <Route path="/appointments/:reference" element={<PageBoundary><AppointmentManage /></PageBoundary>} />
                      <Route path="/finder" element={<PageBoundary><ServiceFinder /></PageBoundary>} />
                      <Route path="/saved" element={<PageBoundary><SavedServices /></PageBoundary>} />
                      <Route path="/support" element={<PageBoundary><Support /></PageBoundary>} />
                      <Route path="/help" element={<LegacyHelpRedirect />} />
                      <Route path="/help/conversation" element={<Navigate to="/support" replace />} />
                      <Route path="*" element={<PageBoundary><NotFound /></PageBoundary>} />
                    </Route>

                    <Route path="/login" element={<PageBoundary><Login /></PageBoundary>} />
                    <Route path="/login/mfa" element={<PageBoundary><MfaChallenge /></PageBoundary>} />
                    <Route path="/forgot-password" element={<PageBoundary><ForgotPassword /></PageBoundary>} />
                    <Route path="/reset-password" element={<PageBoundary><ResetPassword /></PageBoundary>} />

                    <Route element={<RequireAuth />}>
                      <Route element={<AdminLayout />}>
                        <Route element={<RequireAuth anyPermissions={FUNCTION_PERMISSIONS} />}>
                          <Route path="/admin/functions" element={<PageBoundary><AdminFunctionsPage /></PageBoundary>} />
                        </Route>
                        <Route element={<RequireAuth anyPermissions={['FUNCTIONS_CREATE']} />}>
                          <Route path="/admin/functions/new" element={<PageBoundary><CreateFunctionPage /></PageBoundary>} />
                        </Route>
                        <Route element={<RequireAuth anyPermissions={['FUNCTIONS_VIEW']} />}>
                          <Route path="/admin/functions/:id" element={<PageBoundary><EditFunctionPage /></PageBoundary>} />
                          <Route path="/admin/functions/:id/edit" element={<PageBoundary><LegacyFunctionEditorRedirect /></PageBoundary>} />
                        </Route>
                        <Route element={<RequireAuth anyPermissions={REPORT_VIEW_PERMISSIONS} />}>
                          <Route path="/admin/reports" element={<PageBoundary><Reports /></PageBoundary>} />
                        </Route>
                        <Route element={<RequireAuth anyPermissions={[HELP_VIEW_PERMISSION]} />}>
                          <Route path="/admin/questions" element={<PageBoundary><VisitorQuestions /></PageBoundary>} />
                        </Route>
                        <Route element={<RequireAuth anyPermissions={APPOINTMENT_VIEW_PERMISSIONS} />}>
                          <Route path="/admin/appointments" element={<PageBoundary><Appointments /></PageBoundary>} />
                        </Route>
                        <Route element={<RequireAuth anyPermissions={['ORG_ANALYTICS_VIEW']} />}>
                          <Route path="/admin/organization-dashboard" element={<PageBoundary><OrganizationDashboard /></PageBoundary>} />
                        </Route>
                        <Route path="/admin" element={<PageBoundary><Dashboard /></PageBoundary>} />
                        <Route path="/admin/organizations" element={<PageBoundary><Organizations /></PageBoundary>} />
                        <Route path="/settings/security" element={<PageBoundary><SecuritySettings /></PageBoundary>} />

                        <Route element={<RequireAuth roles={['ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN']} />}>
                          <Route path="/admin/moderators" element={<PageBoundary><Moderators /></PageBoundary>} />
                        </Route>

                        <Route element={<RequireAuth roles={['ROLE_SUPER_ADMIN']} />}>
                          <Route path="/admin/org-admins" element={<PageBoundary><OrgAdmins /></PageBoundary>} />
                          <Route path="/admin/roles" element={<PageBoundary><Roles /></PageBoundary>} />
                          <Route path="/admin/languages" element={<PageBoundary><Languages /></PageBoundary>} />
                          <Route path="/admin/users" element={<PageBoundary><LegacyUsers /></PageBoundary>} />
                        </Route>
                      </Route>
                    </Route>
                  </Routes>
                  <Toaster />
                </AppErrorBoundary>
              </AuthProvider>
            </I18nProvider>
          </BrowserRouter>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
