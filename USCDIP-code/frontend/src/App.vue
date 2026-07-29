<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import AppHeader from '@/components/AppHeader.vue';

const route = useRoute();
const showAppHeader = computed(
  () => route.meta.requiresAuth === true && route.meta.fullscreen !== true
);
</script>

<template>
  <div v-if="showAppHeader" class="application-shell">
    <AppHeader />
    <div class="application-content">
      <RouterView />
    </div>
  </div>
  <RouterView v-else />
</template>
