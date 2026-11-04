import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { HubApiService } from '../../services/hub-api.service';
import { ApiEndpoint, ServiceStatusSummary } from '../../models/service.model';

@Component({
  selector: 'app-api-runner',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="max-width: 1200px; margin: 0 auto; padding: 1.5rem;">
      <!-- Navigation Header -->
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
        <div style="display: flex; align-items: center; gap: 1rem;">
          <button (click)="goBack()" class="btn btn-secondary">
            ← Back to Catalog
          </button>
          @if (service()) {
            <div>
              <h2 style="font-size: 1.35rem; font-weight: 700;">{{ service()!.manifest.name }}</h2>
              <div style="display: flex; gap: 0.5rem; align-items: center; margin-top: 0.2rem;">
                <span [class]="'badge badge-' + service()!.status">● {{ service()!.status }}</span>
                <span style="font-size: 0.8rem; color: var(--text-dim);">Port: {{ service()!.manifest.ports?.container }}</span>
              </div>
            </div>
          }
        </div>

        <div>
          @if (service()?.status === 'running') {
            <button (click)="stopService()" class="btn btn-danger">
              ⏹ Stop Container
            </button>
          } @else {
            <button (click)="startService()" class="btn btn-success" [disabled]="loading()">
              ▶ Start API Container
            </button>
          }
        </div>
      </div>

      <!-- Endpoint Selector & Runner -->
      <div style="display: grid; grid-template-columns: 340px 1fr; gap: 1.5rem; align-items: start;">
        <!-- Left: Endpoints & Options -->
        <div class="hub-card">
          <h3 style="font-size: 1rem; font-weight: 600; margin-bottom: 1rem;">Available Endpoints</h3>

          <div style="display: flex; flex-direction: column; gap: 0.5rem; margin-bottom: 1.5rem;">
            @for (ep of endpoints(); track ep.id) {
              <button
                (click)="selectEndpoint(ep)"
                [style.background]="selectedEndpoint()?.id === ep.id ? 'rgba(56, 189, 248, 0.15)' : 'var(--bg-secondary)'"
                [style.border-color]="selectedEndpoint()?.id === ep.id ? 'var(--accent-cyan)' : 'var(--border-color)'"
                style="text-align: left; padding: 0.75rem; border: 1px solid; border-radius: 8px; cursor: pointer; color: var(--text-main);">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem;">
                  <span style="font-size: 0.75rem; font-weight: 700; color: #38bdf8;">{{ ep.method }}</span>
                  <span style="font-size: 0.75rem; color: var(--text-dim);">{{ ep.path }}</span>
                </div>
                <div style="font-size: 0.8rem; color: var(--text-muted);">{{ ep.description }}</div>
              </button>
            }
          </div>

          @if (selectedEndpoint()) {
            <form (ngSubmit)="submitRequest()">
              <!-- File Input if consumes multipart -->
              @if (selectedEndpoint()?.fileField) {
                <div class="form-group">
                  <label class="form-label">Upload Document ({{ selectedEndpoint()!.fileField }})</label>
                  <input
                    type="file"
                    (change)="onFileSelected($event)"
                    class="form-control"
                    accept=".pdf,.epub,.txt" />
                  @if (selectedFile) {
                    <small style="color: var(--accent-emerald); font-size: 0.75rem; display: block; margin-top: 0.25rem;">
                      Selected: {{ selectedFile.name }} ({{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB)
                    </small>
                  }
                </div>
              }

              <!-- Query Parameters -->
              @for (param of selectedEndpoint()?.parameters || []; track param.name) {
                <div class="form-group">
                  <label class="form-label">{{ param.label || param.name }}</label>
                  @if (param.type === 'select') {
                    <select [(ngModel)]="params[param.name]" [name]="param.name" class="form-control">
                      @for (opt of param.options || []; track opt) {
                        <option [value]="opt">{{ opt }}</option>
                      }
                    </select>
                  } @else {
                    <input type="text" [(ngModel)]="params[param.name]" [name]="param.name" class="form-control" />
                  }
                </div>
              }

              <button
                type="submit"
                class="btn btn-primary"
                style="width: 100%; padding: 0.75rem; margin-top: 1rem;"
                [disabled]="loading() || service()?.status !== 'running'">
                {{ loading() ? 'Processing...' : 'Send Request 🚀' }}
              </button>
            </form>
          }
        </div>

        <!-- Right: Results & Output Viewer -->
        <div class="hub-card">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 style="font-size: 1rem; font-weight: 600;">Response Output</h3>
            @if (downloadUrl()) {
              <a [href]="downloadUrl()" [download]="downloadFilename()" class="btn btn-success" style="font-size: 0.8rem; padding: 0.4rem 0.8rem;">
                ⬇ Download Result File
              </a>
            }
          </div>

          @if (loading()) {
            <div style="padding: 4rem 1rem; text-align: center; color: var(--text-muted);">
              <div style="font-size: 1.5rem; animation: pulse 1s infinite; margin-bottom: 0.5rem;">⚙️</div>
              <p>Executing request through orchestrator reverse proxy...</p>
            </div>
          } @else if (jsonResponse()) {
            <pre style="background: var(--bg-primary); padding: 1rem; border-radius: 8px; font-size: 0.85rem; overflow-x: auto; color: #38bdf8; border: 1px solid var(--border-color); max-height: 500px;">{{ jsonResponse() }}</pre>
          } @else if (downloadUrl()) {
            <div style="background: rgba(16, 185, 129, 0.1); border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 8px; padding: 2rem; text-align: center;">
              <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🎉</div>
              <h4 style="font-size: 1.1rem; font-weight: 600; color: #34d399; margin-bottom: 0.5rem;">File Generated Successfully!</h4>
              <p style="font-size: 0.875rem; color: var(--text-muted); margin-bottom: 1.5rem;">
                The binary file has been processed and ready for download: <strong>{{ downloadFilename() }}</strong>
              </p>
              <a [href]="downloadUrl()" [download]="downloadFilename()" class="btn btn-success">
                Save {{ downloadFilename() }}
              </a>
            </div>
          } @else {
            <div style="padding: 4rem 1rem; text-align: center; color: var(--text-dim);">
              <p>Select an endpoint, specify options or attach a file, then click "Send Request".</p>
            </div>
          }
        </div>
      </div>
    </div>
  `
})
export class ApiRunnerComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(HubApiService);
  private http = inject(HttpClient);

  serviceId = signal<string>('');
  service = signal<ServiceStatusSummary | null>(null);
  endpoints = signal<ApiEndpoint[]>([]);
  selectedEndpoint = signal<ApiEndpoint | null>(null);

  params: Record<string, any> = {};
  selectedFile: File | null = null;
  loading = signal<boolean>(false);
  jsonResponse = signal<string | null>(null);
  downloadUrl = signal<string | null>(null);
  downloadFilename = signal<string>('output.epub');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.serviceId.set(id);
    this.loadService();
  }

  loadService(): void {
    this.api.getService(this.serviceId()).subscribe({
      next: (res) => {
        this.service.set(res.service);
        const eps = res.service.manifest.api?.endpoints || [];
        this.endpoints.set(eps);
        if (eps.length > 0 && !this.selectedEndpoint()) {
          this.selectEndpoint(eps[0]);
        }
      },
      error: (err) => console.error('Error loading service:', err)
    });
  }

  selectEndpoint(ep: ApiEndpoint): void {
    this.selectedEndpoint.set(ep);
    this.params = {};
    if (ep.parameters) {
      for (const p of ep.parameters) {
        if (p.default !== undefined) {
          this.params[p.name] = p.default;
        }
      }
    }
  }

  onFileSelected(event: any): void {
    const file = event.target.files?.[0];
    if (file) {
      this.selectedFile = file;
    }
  }

  submitRequest(): void {
    const ep = this.selectedEndpoint();
    if (!ep) return;

    this.loading.set(true);
    this.jsonResponse.set(null);
    this.downloadUrl.set(null);

    // Build URL through reverse proxy
    let targetUrl = `/proxy/${this.serviceId()}${ep.path}`;

    // Add query parameters
    const queryParams: string[] = [];
    for (const [k, v] of Object.entries(this.params)) {
      if (v !== undefined && v !== null && v !== '') {
        queryParams.push(`${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
      }
    }
    if (queryParams.length > 0) {
      targetUrl += `?${queryParams.join('&')}`;
    }

    if (ep.consumes === 'multipart/form-data') {
      const formData = new FormData();
      if (this.selectedFile && ep.fileField) {
        formData.append(ep.fileField, this.selectedFile);
      }

      this.http.post(targetUrl, formData, { observe: 'response', responseType: 'blob' }).subscribe({
        next: (response: HttpResponse<Blob>) => {
          this.loading.set(false);
          const contentType = response.headers.get('content-type') || '';

          if (contentType.includes('application/json')) {
            response.body?.text().then((txt) => {
              try {
                this.jsonResponse.set(JSON.stringify(JSON.parse(txt), null, 2));
              } catch {
                this.jsonResponse.set(txt);
              }
            });
          } else {
            // Binary response (EPUB, zip, etc.)
            const blob = response.body!;
            const objectUrl = URL.createObjectURL(blob);
            this.downloadUrl.set(objectUrl);
            const originalName = this.selectedFile?.name.replace(/\.[^/.]+$/, '') || 'document';
            this.downloadFilename.set(`${originalName}.cleaned.epub`);
          }
        },
        error: (err) => {
          this.loading.set(false);
          alert(`API Request failed: ${err.message}`);
        }
      });
    } else {
      // Standard JSON/REST request
      this.http.request(ep.method, targetUrl, { body: this.params }).subscribe({
        next: (data) => {
          this.loading.set(false);
          this.jsonResponse.set(JSON.stringify(data, null, 2));
        },
        error: (err) => {
          this.loading.set(false);
          alert(`API Request failed: ${err.message}`);
        }
      });
    }
  }

  startService(): void {
    this.loading.set(true);
    this.api.startService(this.serviceId()).subscribe({
      next: () => {
        this.loading.set(false);
        this.loadService();
      },
      error: (err) => {
        this.loading.set(false);
        alert(`Failed to start: ${err.error?.error || err.message}`);
      }
    });
  }

  stopService(): void {
    this.api.stopService(this.serviceId()).subscribe({
      next: () => this.loadService(),
      error: (err) => alert(`Failed to stop: ${err.message}`)
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}

