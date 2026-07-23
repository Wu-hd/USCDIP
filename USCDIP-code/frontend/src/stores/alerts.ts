import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAlertStore = defineStore('alerts', () => {
  const alerts = ref<any[]>([]);
  const isConnected = ref(false);

  const setAlerts = (newAlerts: any[]) => {
    alerts.value = [...newAlerts];
  };

  const addAlert = (alert: any) => {
    alerts.value.unshift(alert);
  };

  const removeAlert = (alertId: string) => {
    alerts.value = alerts.value.filter(a => a.id !== alertId);
  };

  const updateConnectionState = (state: boolean) => {
    isConnected.value = state;
  };

  return {
    alerts,
    isConnected,
    setAlerts,
    addAlert,
    removeAlert,
    updateConnectionState
  };
});
