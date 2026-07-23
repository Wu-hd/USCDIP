import type { ManagedPlatformCode } from '@/types/api';
import 'vue-router';

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean;
    authOnly?: boolean;
    platformCode?: ManagedPlatformCode;
    entryPermission?: string;
    menuPermission?: string;
    permissionLabel?: string;
    requiredRole?: string;
  }
}

export {};
