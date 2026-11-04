import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HubApiService } from '../../services/hub-api.service';
import { ServiceStatusSummary } from '../../models/service.model';

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="max-width: 1300px; margin: 0 auto; padding: 2rem 1.5rem;">
      <!-- Header -->
      <header style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2.5rem; flex-wrap: wrap; gap: 1rem;">
        <div>
          <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.35rem;">
            <div style="width: 38px; height: 38px; border-radius: 8px; background: linear-gradient(135deg, #38bdf8, #6366f1); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 1.25rem;">
              ⚡
            </div>
            <h1 style="font-size: 1.75rem; font-weight: 700; letter-spacing: -0.02em;">Application Hub</h1>
          </div>
          <p style="color: var(--text-muted); font-size: 0.95rem;">
            Orchestrated microservices, ML training workloads, and local tools on demand.
          </p>
        </div>

        <div style="display: flex; align-items: center; gap: 0.75rem;">
          <button (click)="reloadCatalog()" class="btn btn-secondary" [disabled]="loading()">
            <span>🔄</span> {{ loading() ? 'Scanning...' : 'Reload Catalog' }}
          </button>
        </div>
      </header>

      <!-- Filter Tabs -->
      <div style="display: flex; gap: 0.5rem; margin-bottom: 1.75rem; border-bottom: 1px solid var(--border-color); padding-bottom: 0.75rem;">
        <button 
          (click)="selectedType.set('all')"
          [style.background]="selectedType() === 'all' ? '#2563eb' : 'transparent'"
          class="btn" style="border-radius: 6px; padding: 0.4rem 0.85rem; font-size: 0.825rem;">
          All Services ({{ services().length }})
        </button>
        <button 
          (click)="selectedType.set('web-ui')"
          [style.background]="selectedType() === 'web-ui' ? '#2563eb' : 'transparent'"
          class="btn" style="border-radius: 6px; padding: 0.4rem 0.85rem; font-size: 0.825rem;">
          Web UIs
        </button>
        <button 
          (click)="selectedType.set('cli-job')"
          [style.background]="selectedType() === 'cli-job' ? '#2563eb' : 'transparent'"
          class="btn" style="border-radius: 6px; padding: 0.4rem 0.85rem; font-size: 0.825rem;">
          CLI & ML Jobs
        </button>
        <button 
          (click)="selectedType.set('rest-api')"
          [style.background]="selectedType() === 'rest-api' ? '#2563eb' : 'transparent'"
          class="btn" style="border-radius: 6px; padding: 0.4rem 0.85rem; font-size: 0.825rem;">
          REST APIs
        </button>
      </div>

      <!-- Services Grid -->
      @if (filteredServices().length === 0) {
        <div style="text-align: center; padding: 4rem 1rem; color: var(--text-dim); background: var(--bg-secondary); border-radius: 12px; border: 1px dashed var(--border-color);">
          <p style="font-size: 1.1rem; margin-bottom: 0.5rem;">No services discovered.</p>
          <p style="font-size: 0.875rem;">Ensure services under <code>/services</code> have valid <code>manifest.json</code> definitions.</p>
        </div>
      } @else {
        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 1.5rem;">
          @for (service of filteredServices(); track service.manifest.id) {
            <div class="hub-card">
              <!-- Card Header -->
              <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                  <div style="width: 44px; height: 44px; border-radius: 10px; background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.2); display: flex; align-items: center; justify-content: center; font-size: 1.25rem;">
                    @switch (service.manifest.type) {
                      @case ('cli-job') { 💻 }
                      @case ('web-ui') { 🌐 }
                      @case ('rest-api') { ⚡ }
                    }
                  </div>
                  <div>
                    <h3 style="font-size: 1.1rem; font-weight: 600;">{{ service.manifest.name }}</h3>
                    <div style="display: flex; gap: 0.35rem; margin-top: 0.2rem; flex-wrap: wrap;">
                      <span class="badge badge-type">{{ service.manifest.type }}</span>
                      @if (service.manifest.hardware?.gpu) {
                        <span class="badge badge-gpu">NVIDIA GPU</span>
                      }
                      @if (service.manifest.ports?.container) {
                        <span class="badge badge-type">:{{ service.manifest.ports?.container }}</span>
                      }
                    </div>
                  </div>
                </div>

                <!-- Status Badge -->
                <span [class]="'badge badge-' + service.status">
                  ● {{ service.status }}
                </span>
              </div>

              <!-- Description -->
              <p style="color: var(--text-muted); font-size: 0.875rem; flex-grow: 1; margin-bottom: 1.25rem; line-height: 1.45;">
                {{ service.manifest.description }}
              </p>

              <!-- Image & Directory Details -->
              <div style="background: rgba(15, 23, 42, 0.6); padding: 0.6rem 0.8rem; border-radius: 6px; font-size: 0.75rem; color: var(--text-dim); margin-bottom: 1.25rem;">
                <div><strong style="color: var(--text-muted);">Image:</strong> {{ service.manifest.imageName }}</div>
                @if (service.manifest.dockerfileDir) {
                  <div><strong style="color: var(--text-muted);">Path:</strong> {{ service.manifest.dockerfileDir }}</div>
                }
              </div>

              <!-- Card Action Footer -->
              <div style="display: flex; gap: 0.75rem; border-top: 1px solid var(--border-color); padding-top: 1rem;">
                @if (service.manifest.type === 'cli-job') {
                  <button (click)="openTerminal(service.manifest.id)" class="btn btn-primary" style="flex: 1;">
                    <span>▶</span> Run & Stream Logs
                  </button>
                  @if (service.status === 'executing') {
                    <button (click)="stopService(service.manifest.id)" class="btn btn-danger">
                      Abort
                    </button>
                  }
                } @else if (service.manifest.type === 'web-ui') {
                  @if (service.status === 'running') {
                    <button (click)="openWeb(service.manifest.id)" class="btn btn-success" style="flex: 1;">
                      Open Web UI ↗
                    </button>
                    <button (click)="stopService(service.manifest.id)" class="btn btn-danger">
                      Stop
                    </button>
                  } @else {
                    <button (click)="startAndOpenWeb(service.manifest.id)" class="btn btn-primary" style="flex: 1;" [disabled]="service.status === 'starting'">
                      {{ service.status === 'starting' ? 'Starting...' : 'Launch Container' }}
                    </button>
                  }
                } @else if (service.manifest.type === 'rest-api') {
                  @if (service.status === 'running') {
                    <button (click)="openApi(service.manifest.id)" class="btn btn-success" style="flex: 1;">
                      API Runner ↗
                    </button>
                    <button (click)="stopService(service.manifest.id)" class="btn btn-danger">
                      Stop
                    </button>
                  } @else {
                    <button (click)="startAndOpenApi(service.manifest.id)" class="btn btn-primary" style="flex: 1;" [disabled]="service.status === 'starting'">
                      {{ service.status === 'starting' ? 'Starting...' : 'Launch API' }}
                    </button>
                  }
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class CatalogComponent implements OnInit {
  private api = inject(HubApiService);
  private router = inject(Router);

  services = signal<ServiceStatusSummary[]>([]);
  loading = signal<boolean>(false);
  selectedType = signal<string>('all');

  filteredServices = () => {
    const type = this.selectedType();
    if (type === 'all') return this.services();
    return this.services().filter((s) => s.manifest.type === type);
  };

  ngOnInit(): void {
    this.fetchServices();
  }

  fetchServices(): void {
    this.loading.set(true);
    this.api.getServices().subscribe({
      next: (res) => {
        this.services.set(res.services);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load services', err);
        this.loading.set(false);
      }
    });
  }

  reloadCatalog(): void {
    this.loading.set(true);
    this.api.reloadServices().subscribe({
      next: (res) => {
        this.services.set(res.services);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openTerminal(id: string): void {
    this.router.navigate(['/service', id, 'terminal']);
  }

  openWeb(id: string): void {
    this.router.navigate(['/service', id, 'web']);
  }

  startAndOpenWeb(id: string): void {
    this.api.startService(id).subscribe({
      next: () => {
        this.router.navigate(['/service', id, 'web']);
      },
      error: (err) => alert(`Failed to start service: ${err.error?.error || err.message}`)
    });
  }

  openApi(id: string): void {
    this.router.navigate(['/service', id, 'api']);
  }

  startAndOpenApi(id: string): void {
    this.api.startService(id).subscribe({
      next: () => {
        this.router.navigate(['/service', id, 'api']);
      },
      error: (err) => alert(`Failed to start API service: ${err.error?.error || err.message}`)
    });
  }

  stopService(id: string): void {
    this.api.stopService(id).subscribe({
      next: () => this.fetchServices(),
      error: (err) => alert(`Failed to stop service: ${err.message}`)
    });
  }
}

