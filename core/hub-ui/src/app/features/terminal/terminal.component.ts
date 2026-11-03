import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  ElementRef,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';
import { Subscription } from 'rxjs';
import { HubApiService } from '../../services/hub-api.service';
import { LogStreamService } from '../../services/log-stream.service';
import { ServiceStatusSummary } from '../../models/service.model';

@Component({
  selector: 'app-terminal-runner',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="max-width: 1400px; margin: 0 auto; padding: 1.5rem;">
      <!-- Navigation Bar -->
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
                @if (service()!.manifest.hardware?.gpu) {
                  <span class="badge badge-gpu">NVIDIA CUDA GPU</span>
                }
                <span style="font-size: 0.8rem; color: var(--text-dim);">Image: {{ service()!.manifest.imageName }}</span>
              </div>
            </div>
          }
        </div>

        <div style="display: flex; gap: 0.5rem;">
          <button (click)="clearTerminal()" class="btn btn-secondary">
            Clear Terminal
          </button>
          @if (isRunning()) {
            <button (click)="abortJob()" class="btn btn-danger">
              ⏹ Abort Job
            </button>
          }
        </div>
      </div>

      <!-- Main Layout: Sidebar Configuration & Main Terminal -->
      <div style="display: grid; grid-template-columns: 360px 1fr; gap: 1.5rem; align-items: start;">
        <!-- Left Column: Parameters Configuration Form -->
        <div class="hub-card">
          <h3 style="font-size: 1.05rem; font-weight: 600; margin-bottom: 0.5rem; color: var(--text-main);">
            Run Parameters
          </h3>
          <p style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 1.25rem;">
            Configure CLI arguments and execution flags before spawning the container.
          </p>

          <form (ngSubmit)="runJob()">
            @for (param of service()?.manifest?.parameters || []; track param.name) {
              <div class="form-group">
                <label class="form-label">
                  {{ param.label || param.name }}
                  @if (param.required) { <span style="color: var(--accent-rose)">*</span> }
                </label>

                @if (param.type === 'select') {
                  <select
                    [(ngModel)]="formValues[param.name]"
                    [name]="param.name"
                    [disabled]="isRunning()"
                    class="form-control">
                    @for (opt of param.options || []; track opt) {
                      <option [value]="opt">{{ opt }}</option>
                    }
                  </select>
                } @else if (param.type === 'number') {
                  <input
                    type="number"
                    [(ngModel)]="formValues[param.name]"
                    [name]="param.name"
                    [disabled]="isRunning()"
                    class="form-control" />
                } @else if (param.type === 'boolean') {
                  <input
                    type="checkbox"
                    [(ngModel)]="formValues[param.name]"
                    [name]="param.name"
                    [disabled]="isRunning()" />
                } @else {
                  <input
                    type="text"
                    [(ngModel)]="formValues[param.name]"
                    [name]="param.name"
                    [disabled]="isRunning()"
                    class="form-control" />
                }

                @if (param.description) {
                  <small style="font-size: 0.75rem; color: var(--text-dim); display: block; margin-top: 0.25rem;">
                    {{ param.description }}
                  </small>
                }
              </div>
            }

            <!-- Mounted Volumes Info -->
            @if (service()?.manifest?.volumes?.length) {
              <div style="margin-top: 1.5rem; border-top: 1px solid var(--border-color); padding-top: 1rem;">
                <label class="form-label" style="margin-bottom: 0.5rem;">Mounted Host Paths</label>
                @for (vol of service()!.manifest.volumes!; track vol.name) {
                  <div style="font-size: 0.75rem; background: var(--bg-primary); padding: 0.4rem 0.6rem; border-radius: 4px; margin-bottom: 0.35rem; color: var(--text-dim);">
                    <strong style="color: var(--text-muted);">{{ vol.name }}:</strong> {{ vol.containerPath }} ({{ vol.mode || 'rw' }})
                  </div>
                }
              </div>
            }

            <div style="margin-top: 1.5rem;">
              <button
                type="submit"
                class="btn btn-primary"
                style="width: 100%; padding: 0.75rem;"
                [disabled]="isRunning()">
                {{ isRunning() ? 'Job In Progress...' : '▶ Launch Container Job' }}
              </button>
            </div>
          </form>
        </div>

        <!-- Right Column: xterm.js Terminal Output -->
        <div class="hub-card" style="padding: 1rem;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; font-size: 0.85rem; color: var(--text-muted);">
            <div style="display: flex; align-items: center; gap: 0.5rem;">
              <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%;" [style.background]="isRunning() ? '#34d399' : '#64748b'"></span>
              <span>Live Console Stream (stdout / stderr)</span>
            </div>
            <span style="font-size: 0.75rem; color: var(--text-dim);">xterm.js engine</span>
          </div>

          <div #terminalContainer class="terminal-wrapper"></div>
        </div>
      </div>
    </div>
  `
})
export class TerminalRunnerComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('terminalContainer') terminalRef!: ElementRef<HTMLDivElement>;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(HubApiService);
  private logService = inject(LogStreamService);

  serviceId = signal<string>('');
  service = signal<ServiceStatusSummary | null>(null);
  isRunning = signal<boolean>(false);
  formValues: Record<string, any> = {};

  private terminal!: Terminal;
  private fitAddon!: FitAddon;
  private logSub?: Subscription;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.serviceId.set(id);
    this.loadService();
  }

  ngAfterViewInit(): void {
    this.initTerminal();
    this.connectLogStream();
  }

  ngOnDestroy(): void {
    this.logSub?.unsubscribe();
    this.logService.disconnect();
    this.terminal?.dispose();
  }

  private initTerminal(): void {
    this.terminal = new Terminal({
      cursorBlink: true,
      fontFamily: 'JetBrains Mono, Menlo, Monaco, Courier, monospace',
      fontSize: 13,
      lineHeight: 1.25,
      theme: {
        background: '#090d16',
        foreground: '#e2e8f0',
        cursor: '#38bdf8',
        selectionBackground: 'rgba(56, 189, 248, 0.3)',
        black: '#0f172a',
        red: '#f43f5e',
        green: '#10b981',
        yellow: '#f59e0b',
        blue: '#3b82f6',
        magenta: '#a855f7',
        cyan: '#06b6d4',
        white: '#f8fafc'
      }
    });

    this.fitAddon = new FitAddon();
    this.terminal.loadAddon(this.fitAddon);
    this.terminal.open(this.terminalRef.nativeElement);
    this.fitAddon.fit();

    window.addEventListener('resize', () => {
      this.fitAddon?.fit();
    });

    this.terminal.writeln('\x1b[90mWaiting for job execution...\x1b[0m\r\n');
  }

  private connectLogStream(): void {
    this.logSub = this.logService.connect(this.serviceId()).subscribe({
      next: (msg) => {
        if (msg.type === 'log' && msg.data) {
          this.terminal.write(msg.data);
        } else if (msg.type === 'clear') {
          this.terminal.clear();
        } else if (msg.type === 'status') {
          if (msg.status === 'executing' || msg.status === 'running') {
            this.isRunning.set(true);
          } else {
            this.isRunning.set(false);
          }
        }
      }
    });
  }

  loadService(): void {
    this.api.getService(this.serviceId()).subscribe({
      next: (res) => {
        this.service.set(res.service);
        this.isRunning.set(res.service.status === 'executing');

        // Populate default values
        if (res.service.manifest.parameters) {
          for (const p of res.service.manifest.parameters) {
            if (this.formValues[p.name] === undefined && p.default !== undefined) {
              this.formValues[p.name] = p.default;
            }
          }
        }
      },
      error: (err) => console.error('Error fetching service:', err)
    });
  }

  runJob(): void {
    this.isRunning.set(true);
    this.api.executeCliJob(this.serviceId(), { parameters: this.formValues }).subscribe({
      next: () => {
        console.log('Job dispatched.');
      },
      error: (err) => {
        this.isRunning.set(false);
        this.terminal.writeln(`\r\n\x1b[31m[Hub Error] Launch failed: ${err.error?.error || err.message}\x1b[0m\r\n`);
      }
    });
  }

  abortJob(): void {
    this.api.stopService(this.serviceId()).subscribe({
      next: () => {
        this.isRunning.set(false);
        this.terminal.writeln('\r\n\x1b[33m[Hub Orchestrator] Job aborted by user.\x1b[0m\r\n');
      },
      error: (err) => alert(`Abort failed: ${err.message}`)
    });
  }

  clearTerminal(): void {
    this.terminal.clear();
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}

