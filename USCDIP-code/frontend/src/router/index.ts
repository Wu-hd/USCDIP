import { createRouter, createWebHistory } from 'vue-router';

import { getAccessToken } from '@/services/api';
import { resolvePostLoginRoute } from '@/services/auth';
import { checkPermissions, isManagedPlatformCode } from '@/services/permissions';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/portal',
      name: 'portal',
      component: () => import('@/views/PortalView.vue')
    },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('@/views/AuthCallbackView.vue')
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue')
    },
    {
      path: '/home',
      name: 'national-overview',
      component: () => import('@/views/NationalOverviewView.vue'),
      meta: {
        requiresAuth: true,
        authOnly: true,
        permissionLabel: '全国地下管网三维健康态势'
      }
    },
    {
      path: '/mgmt',
      name: 'mgmt-placeholder',
      component: () => import('@/views/PlatformPlaceholderView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '综合管理平台'
      }
    },
    {
      path: '/mgmt/gis',
      name: 'mgmt-gis-map',
      component: () => import('@/views/GisMapView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '2D 一张图'
      }
    },
    {
      path: '/mgmt/gis/3d',
      redirect: (to) => ({
        path: '/home',
        query: to.query
      })
    },
    {
      path: '/mgmt/devices',
      name: 'mgmt-device-ledger',
      component: () => import('@/views/DeviceLedgerView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '设备台账'
      }
    },
    {
      path: '/mgmt/changes',
      name: 'mgmt-master-data-changes',
      component: () => import('@/views/MasterDataChangeView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:WRITE',
        permissionLabel: '资产变更申请'
      }
    },
    {
      path: '/mgmt/accounts',
      name: 'mgmt-account-management',
      component: () => import('@/views/AccountManagementView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:WRITE',
        permissionLabel: '账号管理',
        requiredRole: 'PLATFORM_ADMIN'
      }
    },
    {
      path: '/mgmt/trends',
      name: 'mgmt-trends',
      component: () => import('@/views/TrendAnalyticsView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '时序趋势'
      }
    },
    {
      path: '/mgmt/alerts',
      name: 'mgmt-alerts',
      component: () => import('@/views/RealtimeAlertsView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '实时告警'
      }
    },
    {
      path: '/mgmt/incidents/:id',
      name: 'mgmt-incident-detail',
      component: () => import('@/views/IncidentDetailView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '事件详情'
      }
    },
    {
      path: '/emgc',
      name: 'emgc-placeholder',
      component: () => import('@/views/PlatformPlaceholderView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'EMGC',
        entryPermission: 'ENTRY:EMGC',
        menuPermission: 'MENU:WORKORDER:READ',
        permissionLabel: '应急指挥平台'
      }
    },
    {
      path: '/emgc/workorders',
      name: 'emgc-workorder-list',
      component: () => import('@/views/WorkOrderListView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'EMGC',
        entryPermission: 'ENTRY:EMGC',
        menuPermission: 'MENU:WORKORDER:READ',
        permissionLabel: '应急工单'
      }
    },
    {
      path: '/emgc/workorders/:id',
      name: 'emgc-workorder-detail',
      component: () => import('@/views/WorkOrderDetailView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'EMGC',
        entryPermission: 'ENTRY:EMGC',
        menuPermission: 'MENU:WORKORDER:READ',
        permissionLabel: '工单详情'
      }
    },
    {
      path: '/diag',
      name: 'diag-placeholder',
      component: () => import('@/views/PlatformPlaceholderView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'DIAG',
        entryPermission: 'ENTRY:DIAG',
        menuPermission: 'MENU:MODEL:READ',
        permissionLabel: '智能诊断中枢'
      }
    },
    {
      path: '/diag/models',
      name: 'diag-model-governance',
      component: () => import('@/views/ModelGovernanceView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'DIAG',
        entryPermission: 'ENTRY:DIAG',
        menuPermission: 'MENU:MODEL:READ',
        permissionLabel: '模型治理'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home'
    }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach(async (to) => {
  if (to.path === '/portal' && getAccessToken()) {
    const authStore = useAuthStore();
    authStore.bindSessionEvents();
    if (await authStore.ensureCurrentUser()) {
      return resolvePostLoginRoute(to.query.redirect);
    }
  }

  if (!to.meta.requiresAuth) {
    return true;
  }

  const authStore = useAuthStore();
  authStore.bindSessionEvents();

  if (!getAccessToken()) {
    return {
      path: '/portal',
      query: {
        auth: 'required',
        redirect: to.fullPath
      }
    };
  }

  const hasUser = await authStore.ensureCurrentUser();
  if (!hasUser) {
    return {
      path: '/portal',
      query: {
        auth: 'required',
        redirect: to.fullPath
      }
    };
  }

  if (to.meta.authOnly === true) {
    return true;
  }

  const requiredRole = String(to.meta.requiredRole ?? '').trim().toUpperCase();
  if (requiredRole && !authStore.permissionSnapshot.roleCodes.includes(requiredRole)) {
    return {
      path: '/forbidden',
      query: {
        reason: 'ROLE_REQUIRED',
        from: to.fullPath,
        requiredRole
      }
    };
  }

  const platformCode = String(to.meta.platformCode ?? '');
  const permissionLabel = String(to.meta.permissionLabel ?? platformCode);
  const entryPermission = String(to.meta.entryPermission ?? '');
  const menuPermission = String(to.meta.menuPermission ?? '');

  if (!isManagedPlatformCode(platformCode) || !entryPermission || !menuPermission) {
    return {
      path: '/forbidden',
      query: {
        reason: 'ROUTE_PERMISSION_META_INVALID',
        from: to.fullPath
      }
    };
  }

  const result = checkPermissions(
    authStore.user,
    [entryPermission, menuPermission],
    permissionLabel
  );
  if (result.allowed) {
    return true;
  }

  return {
    path: '/forbidden',
    query: {
      reason: 'PERMISSION_DENIED',
      from: to.fullPath,
      platform: platformCode,
      required: result.requiredPermissions.join(','),
      missing: result.missingPermissions.join(',')
    }
  };
});

export default router;
