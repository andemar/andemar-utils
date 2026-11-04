import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { HubApiService } from '../../services/hub-api.service';
import { ServiceStatusSummary } from '../../models/service.model';

@Component({
  selector: 'app-web-frame',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="display: flex; flex-direction: column; height: 100vh; overflow: hidden; background: var(--bg-primary);">
      <!-- Top Bar Toolbar -->
      <header style="height: 56px; border-bottom: 1px solid var(--border-color); background: var(--bg-secondary); display: flex; align-items: center; justify-content: space-between; padding: 0 1.25rem;">
        <div style="display: flex; align-items: center; gap: 1rem;">
          <button (click)="goBack()" class="btn btn-secondary" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
            ← Catalog
          </button>
          @if (service()) {
            <div style="display: flex; align-items: center; gap: 0.5rem;">
              <h2 style="font-size: 1.05rem; font-weight: 600;">{{ service()!.manifest.name }}</h2>
              <span [class]="'badge badge-' + service()!.status">
                ● {{ service()!.status }}
              </span>
            </div>
          }
        </div>

        <div style="display: flex; align-items: center; gap: 0.75rem;">
          @if (service()?.status === 'running') {
            <button (click)="reloadIframe()" class="btn btn-secondary" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
              🔄 Reload View
            </button>
            <a [href]="externalUrl()" target="_blank" class="btn btn-secondary" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
              ↗ New Tab
            </a>
            <button (click)="stopContainer()" class="btn btn-danger" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
              ⏹ Stop Container
            </button>
          } @else {
            <button (click)="startContainer()" class="btn btn-success" style="padding: 0.35rem 0.75rem; font-size: 0.8rem;">
              ▶ Start Container
            </button>
          }
        </div>
      </header>

      <!-- Frame Workspace -->
      <main style="flex: 1; position: relative; width: 100%; height: calc(100vh - 56px);">
        @if (service()?.status === 'running' && safeUrl()) {
          <iframe
            #serviceFrame
            [src]="safeUrl()"
            style="width: 100%; height: 100%; border: none; background: #ffffff;"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture">
          </iframe>
        } @else {
          <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: var(--text-muted);">
            <div style="font-size: 3rem; margin-bottom: 1rem;">⏸</div>
            <h3 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 0.5rem; color: var(--text-main);">
              Container is currently {{ service()?.status || 'stopped' }}
            </h3>
            <p style="font-size: 0.9rem; margin-bottom: 1.5rem; max-width: 400px; text-align: center;">
              Launch this service container on demand to preserve host memory and CPU resources.
            </p>
            <button (click)="startContainer()" class="btn btn-primary">
              Launch {{ service()?.manifest?.name || 'Service' }}
            </button>
          </div>
        }
      </main>
    </div>
  `
})
export class WebFrameComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(HubApiService);
  private sanitizer = inject(DomSanitizer);

  serviceId = signal<string>('');
  service = signal<ServiceStatusSummary | null>(null);
  safeUrl = signal<SafeResourceUrl | null>(null);
  externalUrl = signal<string>('');
  private rawFrameUrl = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.serviceId.set(id);
    this.loadService();
  }

  loadService(): void {
    this.api.getService(this.serviceId()).subscribe({
      next: (res) => {
        this.service.set(res.service);

        // When a host port is mapped (e.g. port 3000 for AniTracker), embed the direct host URL.
        // This ensures SPAs (Next.js, Vite, React) can resolve their root assets (/_next/static, etc.) natively.
        // Otherwise, route through the backend dynamic reverse proxy (/proxy/:serviceId/).
        const host = window.location.hostname;
        const targetUrl = res.service.ports?.host
          ? `http://${host}:${res.service.ports.host}/`
          : `/proxy/${this.serviceId()}/`;

        this.rawFrameUrl = targetUrl;
        this.safeUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(targetUrl));
        this.externalUrl.set(res.service.directUrl || targetUrl);
      },
      error: (err) => console.error('Error fetching service:', err)
    });
  }

  reloadIframe(): void {
    if (!this.rawFrameUrl) return;
    const delimiter = this.rawFrameUrl.includes('?') ? '&' : '?';
    const refreshed = `${this.rawFrameUrl}${delimiter}_t=${Date.now()}`;
    this.safeUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(refreshed));
  }

  startContainer(): void {
    this.api.startService(this.serviceId()).subscribe({
      next: () => this.loadService(),
      error: (err) => alert(`Start failed: ${err.error?.error || err.message}`)
    });
  }

  stopContainer(): void {
    this.api.stopService(this.serviceId()).subscribe({
      next: () => this.loadService(),
      error: (err) => alert(`Stop failed: ${err.message}`)
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}

