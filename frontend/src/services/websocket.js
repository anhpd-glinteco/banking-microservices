import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
    constructor() {
        this.client = null;
        this.connected = false;
        this.subscriptions = new Map();
    }

    connect(accountNumber, onBalanceUpdate) {
        // If already connected to same account, just update callback
        if (this.connected && this.subscriptions.has(accountNumber)) {
            console.log('WebSocket already connected to account:', accountNumber);
            return;
        }

        // If connected to different account, disconnect first
        if (this.connected) {
            console.log('Disconnecting from previous account');
            this.disconnect();
        }

        // Create STOMP client
        this.client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8083/ws'),
            debug: (str) => {
                console.log('STOMP: ' + str);
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        // On connect
        this.client.onConnect = () => {
            console.log('WebSocket Connected');
            this.connected = true;

            // Subscribe to account balance updates
            const subscription = this.client.subscribe(`/topic/account/${accountNumber}`, (message) => {
                try {
                    const data = JSON.parse(message.body);
                    console.log('Balance update received:', data);
                    
                    if (onBalanceUpdate && data) {
                        onBalanceUpdate(data);
                    }
                } catch (error) {
                    console.error('Error parsing WebSocket message:', error);
                }
            });

            this.subscriptions.set(accountNumber, subscription);
        };

        // On error
        this.client.onStompError = (frame) => {
            console.error('STOMP error:', frame);
        };

        // On disconnect
        this.client.onDisconnect = () => {
            console.log('WebSocket Disconnected');
            this.connected = false;
        };

        // Activate connection
        this.client.activate();
    }

    disconnect() {
        if (this.client && this.connected) {
            // Unsubscribe all
            this.subscriptions.forEach((subscription) => {
                subscription.unsubscribe();
            });
            this.subscriptions.clear();

            // Deactivate
            this.client.deactivate();
            this.connected = false;
            console.log('WebSocket disconnected');
        }
    }

    isConnected() {
        return this.connected;
    }
}

export default new WebSocketService();
