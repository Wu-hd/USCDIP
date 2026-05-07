import { Client } from '@stomp/stompjs';
import { getAccessToken } from '@/services/api';

export class AlertWebSocketClient {
  private client: Client | null = null;
  private lastAckSeq: number = 0;
  private messageIds = new Set<string>();

  constructor(
    private readonly onMessage: (msg: any) => void,
    private readonly onStateChange: (connected: boolean) => void
  ) {}

  public connect() {
    const token = getAccessToken();
    
    // In actual env, token may be sent via headers or connection url parameters
    // depending on the backend config. Here we use headers.
    
    const wsUrl = `ws://${window.location.host}/ws/push`;
    
    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = (frame) => {
      console.log('Connected to WebSocket:', frame);
      this.onStateChange(true);
      
      this.client?.subscribe('/user/queue/alerts', (message) => {
        if (message.body) {
          try {
            const data = JSON.parse(message.body);
            // Deduplicate by message id or trace id
            const dedupeKey = data.id || data.traceId;
            if (dedupeKey && this.messageIds.has(dedupeKey)) {
              return; // Already processed
            }
            if (dedupeKey) {
              this.messageIds.add(dedupeKey);
            }
            
            // Track last ack seq to send back
            if (data.seq !== undefined) {
               this.lastAckSeq = Math.max(this.lastAckSeq, data.seq);
               this.sendAck();
            }
            
            this.onMessage(data);
          } catch (e) {
            console.error('Failed to parse websocket message', e);
          }
        }
      });
    };

    this.client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    this.client.onWebSocketClose = () => {
      this.onStateChange(false);
    };

    this.client.activate();
  }

  private sendAck() {
    if (this.client && this.client.connected) {
       this.client.publish({
          destination: '/app/alerts/ack',
          body: JSON.stringify({ lastAckSeq: this.lastAckSeq })
       });
    }
  }

  public disconnect() {
    if (this.client) {
      this.client.deactivate();
    }
  }
}
