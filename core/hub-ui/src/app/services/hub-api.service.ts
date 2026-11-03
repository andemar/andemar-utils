import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CliExecutionRequest, ServiceStatusSummary } from '../models/service.model';

export interface ServicesResponse {
  success: boolean;
  count: number;
  services: ServiceStatusSummary[];
}

export interface SingleServiceResponse {
  success: boolean;
  service: ServiceStatusSummary;
}

@Injectable({
  providedIn: 'root'
})
export class HubApiService {
  private http = inject(HttpClient);
  private readonly baseUrl = '/api/services';

  getServices(): Observable<ServicesResponse> {
    return this.http.get<ServicesResponse>(this.baseUrl);
  }

  getService(id: string): Observable<SingleServiceResponse> {
    return this.http.get<SingleServiceResponse>(`${this.baseUrl}/${id}`);
  }

  reloadServices(): Observable<ServicesResponse> {
    return this.http.post<ServicesResponse>(`${this.baseUrl}/reload`, {});
  }

  startService(id: string): Observable<SingleServiceResponse> {
    return this.http.post<SingleServiceResponse>(`${this.baseUrl}/${id}/start`, {});
  }

  stopService(id: string): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(`${this.baseUrl}/${id}/stop`, {});
  }

  executeCliJob(id: string, req: CliExecutionRequest): Observable<{ success: boolean; job: any }> {
    return this.http.post<{ success: boolean; job: any }>(`${this.baseUrl}/${id}/execute`, req);
  }
}

