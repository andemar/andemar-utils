import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';

export interface LogMessage {
  type: 'log' | 'status' | 'clear';
  data?: string;
  status?: string;
  details?: any;
}

@Injectable({
  providedIn: 'root'
})
export class LogStreamService {
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<LogMessage>();

  public connect(serviceId: string): Observable<LogMessage> {
    this.disconnect();

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    // If running in development with Angular on 4200, proxy or direct to 4000
    const host = window.location.port === '4200' ? `${window.location.hostname}:4000` : window.location.host;
    const url = `${protocol}//${host}/ws/logs/${serviceId}`;

    console.log(`[LogStreamService] Connecting to ${url}`);
    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      console.log(`[LogStreamService] Connected to log stream for ${serviceId}`);
    };

    this.socket.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data);
        this.messageSubject.next(parsed);
      } catch (err) {
        this.messageSubject.next({ type: 'log', data: event.data });
      }
    };

    this.socket.onerror = (error) => {
      console.error('[LogStreamService] WebSocket error:', error);
    };

    this.socket.onclose = () => {
      console.log('[LogStreamService] WebSocket connection closed.');
    };

    return this.messageSubject.asObservable();
  }

  public disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }
}

