import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';
import { passwordChangeGuard, requirePasswordChangeGuard } from './guards/password-change.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'devices' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'change-password',
    canActivate: [requirePasswordChangeGuard],
    loadComponent: () =>
      import('./pages/change-password/change-password.component').then((m) => m.ChangePasswordComponent)
  },
  {
    path: '',
    canActivate: [authGuard, passwordChangeGuard],
    runGuardsAndResolvers: 'always',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'devices',
        loadComponent: () =>
          import('./pages/device-list/device-list.component').then((m) => m.DeviceListComponent)
      },
      {
        path: 'devices/stock',
        loadComponent: () =>
          import('./pages/device-stock/device-stock.component').then((m) => m.DeviceStockComponent)
      },
      {
        path: 'devices/new',
        loadComponent: () =>
          import('./pages/device-form/device-form.component').then((m) => m.DeviceFormComponent)
      },
      {
        path: 'devices/:id/edit',
        loadComponent: () =>
          import('./pages/device-form/device-form.component').then((m) => m.DeviceFormComponent)
      },
      {
        path: 'devices/:id',
        loadComponent: () =>
          import('./pages/device-detail/device-detail.component').then((m) => m.DeviceDetailComponent)
      },
      {
        path: 'order-request',
        loadComponent: () =>
          import('./pages/order-request-form/order-request-form.component').then(
            (m) => m.OrderRequestFormComponent
          )
      },
      {
        path: 'order-requests',
        loadComponent: () =>
          import('./pages/order-request-list/order-request-list.component').then(
            (m) => m.OrderRequestListComponent
          )
      },
      {
        path: 'mas',
        loadComponent: () => import('./pages/mas-list/mas-list.component').then((m) => m.MasListComponent)
      },
      {
        path: 'mas/new',
        loadComponent: () => import('./pages/mas-form/mas-form.component').then((m) => m.MasFormComponent)
      },
      {
        path: 'mas/:id/edit',
        loadComponent: () => import('./pages/mas-form/mas-form.component').then((m) => m.MasFormComponent)
      },
      {
        path: 'mas/:id',
        loadComponent: () =>
          import('./pages/mas-detail/mas-detail.component').then((m) => m.MasDetailComponent)
      },
      {
        path: 'sfm',
        loadComponent: () => import('./pages/sfm-list/sfm-list.component').then((m) => m.SfmListComponent)
      },
      {
        path: 'sfm/new',
        loadComponent: () => import('./pages/sfm-form/sfm-form.component').then((m) => m.SfmFormComponent)
      },
      {
        path: 'sfm/:id/edit',
        loadComponent: () => import('./pages/sfm-form/sfm-form.component').then((m) => m.SfmFormComponent)
      },
      {
        path: 'sfm/:id',
        loadComponent: () =>
          import('./pages/sfm-detail/sfm-detail.component').then((m) => m.SfmDetailComponent)
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./pages/user-list/user-list.component').then((m) => m.UserListComponent)
      },
      {
        path: 'users/new',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./pages/user-form/user-form.component').then((m) => m.UserFormComponent)
      },
      {
        path: 'users/:id/edit',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./pages/user-form/user-form.component').then((m) => m.UserFormComponent)
      },
      {
        path: 'setup',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/setup/setup.component').then((m) => m.SetupComponent)
      },
      {
        path: 'ai',
        loadComponent: () =>
          import('./pages/ai-assistant/ai-assistant.component').then((m) => m.AiAssistantComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'devices' }
];
