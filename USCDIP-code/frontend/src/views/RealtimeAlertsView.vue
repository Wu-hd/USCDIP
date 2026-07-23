<template>
  <div class="app-frame app-viewport flex flex-col bg-[#FAFAFA] pt-4 text-slate-800 dark:bg-[#111111] dark:text-slate-100">
    <!-- Header -->
    <header class="flex-none px-8 py-6 mb-4 flex justify-between items-center border-b border-slate-200 dark:border-slate-800">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Real-time Analytics</h1>
        <p class="text-sm font-medium text-slate-500 dark:text-slate-400 mt-1">Monitor your critical system alerts as they happen.</p>
      </div>

      <!-- Live Sync Indicator -->
      <div 
        class="inline-flex items-center space-x-2 px-3 py-1.5 rounded-full border shadow-sm text-sm font-medium transition-colors"
        :class="alertStore.isConnected 
          ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/30 dark:text-emerald-400 dark:border-emerald-800/50' 
          : 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/30 dark:text-rose-400 dark:border-rose-800/50'"
      >
        <span class="relative flex h-2.5 w-2.5">
          <span 
            v-if="alertStore.isConnected"
            class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"
          ></span>
          <span 
            class="relative inline-flex rounded-full h-2.5 w-2.5"
            :class="alertStore.isConnected ? 'bg-emerald-500' : 'bg-rose-500'"
          ></span>
        </span>
        <span>{{ alertStore.isConnected ? 'Live Sync' : 'Disconnected' }}</span>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 overflow-y-auto px-8 pb-8">
      <div class="grid w-full grid-cols-1 gap-4">
        
        <!-- Empty State -->
        <div v-if="alertStore.alerts.length === 0" class="text-center py-20 bg-white dark:bg-[#1C1C1C] rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
          <div class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 mb-4">
            <span class="text-slate-400">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <h3 class="text-sm font-semibold text-slate-900 dark:text-slate-100">All clear</h3>
          <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">No active alerts to monitor at the moment.</p>
        </div>

        <!-- Alert List with Transition -->
        <TransitionGroup name="list" tag="div" class="space-y-3 relative">
          <div 
            v-for="alert in alertStore.alerts" 
            :key="alert.id"
            class="bg-white dark:bg-[#1C1C1C] rounded-xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-shadow group flex items-start space-x-4 w-full list-item-animation"
          >
            <!-- Badge Severity Indicator -->
            <div class="flex-shrink-0 mt-0.5">
              <span class="flex h-10 w-10 items-center justify-center rounded-lg" :class="severityClasses(alert.level).container">
                <component :is="severityIcon(alert.level)" class="h-5 w-5" :class="severityClasses(alert.level).icon" />
              </span>
            </div>

            <!-- Content -->
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-4">
                <h3 class="text-base font-semibold truncate text-slate-900 dark:text-slate-100">
                  {{ alert.title || 'System Alert' }}
                  <span v-if="alert.isBackfill" class="ml-2 inline-flex items-center rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-800 ring-1 ring-inset ring-yellow-600/20 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20">Backfill</span>
                </h3>
                <time class="text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">{{ formatTime(alert.eventTime) }}</time>
              </div>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-300 leading-snug">
                {{ alert.description }}
              </p>
              
              <!-- Footer Meta -->
              <div class="mt-3 flex items-center space-x-4 text-xs font-medium text-slate-500 dark:text-slate-400">
                <div class="flex items-center space-x-1" v-if="alert.score !== undefined">
                  <span class="inline-flex h-1.5 w-1.5 rounded-full" :class="scoreIndicator(alert.score)"></span>
                  <span>DQ Score: {{ alert.score }}</span>
                </div>
                <div class="flex items-center space-x-1 p-1 px-2 bg-slate-100 dark:bg-slate-800 rounded">
                  <span>ID: {{ alert.id.slice(0, 8) }}</span>
                </div>
              </div>
            </div>
            
            <!-- Actions -->
            <div class="opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
               <button @click="dismissAlert(alert.id)" class="text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 px-3 py-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-sm font-medium transition-colors">
                  Dismiss
               </button>
            </div>
          </div>
        </TransitionGroup>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useAlertStore } from '@/stores/alerts';
import { Info, TriangleAlert, ShieldAlert } from 'lucide-vue-next';
import { AlertWebSocketClient } from '@/services/websocket';

const alertStore = useAlertStore();
let wsClient: AlertWebSocketClient | null = null;

onMounted(() => {
  // Mock API call to get initial unrecovered baseline alerts
  fetchInitialAlerts();
  
  // Set up WebSocket
  wsClient = new AlertWebSocketClient(
    (msg) => { alertStore.addAlert(msg); },
    (state) => { alertStore.updateConnectionState(state); }
  );
  wsClient.connect();
});

onUnmounted(() => {
  if (wsClient) wsClient.disconnect();
  alertStore.updateConnectionState(false);
});

const fetchInitialAlerts = async () => {
  try {
     // Ensure you have an api endpoint mapped. Mocking a fallback if server is down:
     // const res = await fetch('/api/alerts');
     // const data = await res.json();
     // alertStore.setAlerts(data.items || []);
     
     // Temporary mock data to view the styling immediately
     alertStore.setAlerts([
       { id: '1a2b3c4d', level: 'CRITICAL', title: 'High Pressure Event', description: 'Pipeline segment SEG-091 exceeded 1.5 MPa threshold.', eventTime: new Date().toISOString(), score: 98, isBackfill: false },
       { id: 'e5f6g7h8', level: 'WARNING', title: 'Offline Device Detected', description: 'Sensor NODE-04 is no longer reporting heartbeats.', eventTime: new Date().toISOString(), score: 45, isBackfill: true },
     ]);
  } catch(e) {
     console.error('Failed to fetch initial alerts', e);
  }
};

const dismissAlert = (id: string) => {
   alertStore.removeAlert(id);
   // Should call backend to acknowledge or suppress locally
};

const formatTime = (isoString?: string) => {
  if (!isoString) return 'Just now';
  const d = new Date(isoString);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

const severityClasses = (level: string) => {
  switch (level.toUpperCase()) {
    case 'CRITICAL':
    case 'FATAL': return { container: 'bg-rose-100 dark:bg-rose-500/20', icon: 'text-rose-600 dark:text-rose-400' };
    case 'WARNING':
    case 'WARN': return { container: 'bg-amber-100 dark:bg-amber-500/20', icon: 'text-amber-600 dark:text-amber-400' };
    default: return { container: 'bg-blue-100 dark:bg-blue-500/20', icon: 'text-blue-600 dark:text-blue-400' };
  }
};

const severityIcon = (level: string) => {
  switch (level.toUpperCase()) {
    case 'CRITICAL':
    case 'FATAL': return TriangleAlert;
    case 'WARNING':
    case 'WARN': return ShieldAlert;
    default: return Info;
  }
};

const scoreIndicator = (score: number) => {
  if (score >= 90) return 'bg-emerald-500';
  if (score >= 60) return 'bg-amber-500';
  return 'bg-rose-500';
}
</script>

<style scoped>
.list-move, 
.list-enter-active,
.list-leave-active {
  transition: all 0.4s ease-out;
}
.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(-20px) scale(0.98);
}
.list-leave-active {
  position: absolute;
}
.list-item-animation {
  transform-origin: top center;
}
</style>
