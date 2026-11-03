import { Routes } from '@angular/router';
import { CatalogComponent } from './features/catalog/catalog.component';
import { WebFrameComponent } from './features/web-frame/web-frame.component';
import { TerminalRunnerComponent } from './features/terminal/terminal.component';
import { ApiRunnerComponent } from './features/api-runner/api-runner.component';

export const routes: Routes = [
  { path: '', component: CatalogComponent },
  { path: 'service/:id/web', component: WebFrameComponent },
  { path: 'service/:id/terminal', component: TerminalRunnerComponent },
  { path: 'service/:id/api', component: ApiRunnerComponent },
  { path: '**', redirectTo: '' }
];

