import fs from 'fs';
import path from 'path';
import { config } from '../config';
import { ManifestSchema, ServiceManifest } from '../types/manifest';

export class ServiceScanner {
  private servicesMap: Map<string, ServiceManifest> = new Map();

  constructor() {
    this.scan();
  }

  public scan(): ServiceManifest[] {
    const discovered = new Map<string, ServiceManifest>();
    const rootDir = config.servicesDir;

    if (!fs.existsSync(rootDir)) {
      console.warn(`[ServiceScanner] Warning: Services directory not found at: ${rootDir}`);
      this.servicesMap = discovered;
      return [];
    }

    const findManifestFiles = (dir: string, depth = 0): string[] => {
      if (depth > 3) return [];
      const results: string[] = [];

      try {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
          const fullPath = path.join(dir, entry.name);
          if (entry.isDirectory()) {
            if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build') {
              results.push(...findManifestFiles(fullPath, depth + 1));
            }
          } else if (entry.isFile()) {
            if (entry.name === 'manifest.json' || entry.name.match(/^manifest\..*\.json$/i)) {
              results.push(fullPath);
            }
          }
        }
      } catch (err) {
        console.error(`[ServiceScanner] Error reading directory ${dir}:`, err);
      }
      return results;
    };

    const manifestFiles = findManifestFiles(rootDir);
    console.log(`[ServiceScanner] Found ${manifestFiles.length} potential manifest file(s).`);

    for (const filePath of manifestFiles) {
      try {
        const rawContent = fs.readFileSync(filePath, 'utf-8');
        const parsedJson = JSON.parse(rawContent);
        const validated = ManifestSchema.parse(parsedJson) as ServiceManifest;

        validated.manifestPath = filePath;

        if (discovered.has(validated.id)) {
          console.warn(`[ServiceScanner] Duplicate service id "${validated.id}" found in ${filePath}. Skipping.`);
          continue;
        }

        discovered.set(validated.id, validated);
        console.log(`[ServiceScanner] Registered tool: [${validated.id}] "${validated.name}" (${validated.type})`);
      } catch (error) {
        console.error(`[ServiceScanner] Failed to parse/validate manifest at ${filePath}:`, error);
      }
    }

    this.servicesMap = discovered;
    return Array.from(this.servicesMap.values());
  }

  public getAll(): ServiceManifest[] {
    return Array.from(this.servicesMap.values());
  }

  public get(id: string): ServiceManifest | undefined {
    return this.servicesMap.get(id);
  }
}

export const serviceScanner = new ServiceScanner();

