import { createRouter, createWebHistory } from 'vue-router';

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
      path: '/mgmt',
      name: 'mgmt-placeholder',
      component: () => import('@/views/PlatformPlaceholderView.vue'),
      meta: {
        platformCode: 'MGMT'
      }
    },
    {
      path: '/emgc',
      name: 'emgc-placeholder',
      component: () => import('@/views/PlatformPlaceholderView.vue'),
      meta: {
        platformCode: 'EMGC'
      }
    },
    {
      path: '/diag',
      name: 'diag-placeholder',
      component: () => import('@/views/PlatformPlaceholderView.vue'),
      meta: {
        platformCode: 'DIAG'
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

export default router;
