import { createRouter, createWebHistory } from 'vue-router';

import { getAccessToken } from '@/services/api';
import { checkPermissions, isManagedPlatformCode } from '@/services/permissions';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/portal'
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
      name: 'mgmt-gis-3d-placeholder',
      component: () => import('@/views/Gis3DPlaceholderView.vue'),
      meta: {
        requiresAuth: true,
        platformCode: 'MGMT',
        entryPermission: 'ENTRY:MGMT',
        menuPermission: 'MENU:ASSET:READ',
        permissionLabel: '3D 占位视图'
      }
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
      path: '/:pathMatch(.*)*',
      redirect: '/portal'
    }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach(async (to) => {
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
