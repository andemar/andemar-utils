import path from 'path';
import fs from 'fs';
import dotenv from 'dotenv';

dotenv.config();

export interface AppConfig {
  port: number;
  servicesDir: string;
  dockerSocket: string;
  dockerNetwork: string;
  hostWorkspaceDir: string;
  isHostWindows: boolean;
}

const resolveDefaultServicesDir = (): string => {
  if (process.env.SERVICES_DIR) {
    return path.resolve(process.env.SERVICES_DIR);
  }
  // If running inside container where /services is mounted:
  if (fs.existsSync('/services')) {
    return '/services';
  }
  // If running in development (core/hub-backend):
  const repoServices = path.resolve(__dirname, '../../../services');
  if (fs.existsSync(repoServices)) {
    return repoServices;
  }
  return path.resolve(process.cwd(), 'services');
};

const resolveDockerSocket = (): string => {
  if (process.env.DOCKER_SOCKET) {
    return process.env.DOCKER_SOCKET;
  }
  if (process.platform === 'win32' && !fs.existsSync('/var/run/docker.sock')) {
    return '//./pipe/docker_engine';
  }
  return '/var/run/docker.sock';
};

const resolveHostWorkspaceDir = (): string => {
  if (process.env.HOST_WORKSPACE_DIR) {
    return process.env.HOST_WORKSPACE_DIR;
  }
  // Default to repo root if running natively
  return path.resolve(__dirname, '../../../');
};

export const config: AppConfig = {
  port: parseInt(process.env.PORT || '4000', 10),
  servicesDir: resolveDefaultServicesDir(),
  dockerSocket: resolveDockerSocket(),
  dockerNetwork: process.env.DOCKER_NETWORK || 'hub-network',
  hostWorkspaceDir: resolveHostWorkspaceDir(),
  isHostWindows: process.platform === 'win32' || (process.env.HOST_OS || '').toLowerCase().includes('win')
};

/**
 * Resolves a host-relative path declared in a manifest (e.g. "./services/spam-service/dataset")
 * into the absolute path required by the Docker daemon for volume bind mounts.
 */
export function resolveHostBindMount(manifestPath: string, relativeOrAbsolutePath: string): string {
  // If path is a Windows absolute path (e.g. D:\... or C:/...):
  if (/^[a-zA-Z]:[\\/]/.test(relativeOrAbsolutePath)) {
    return relativeOrAbsolutePath;
  }

  // If path is already an absolute host path:
  if (path.isAbsolute(relativeOrAbsolutePath) && !relativeOrAbsolutePath.startsWith('/services')) {
    return relativeOrAbsolutePath;
  }

  // Normalize path relative to repo root
  let normalizedRel = relativeOrAbsolutePath.replace(/^\.\//, '');
  if (normalizedRel.startsWith('services/')) {
    // Relative to workspace
  } else {
    // Relative to the manifest directory
    const manifestDir = path.dirname(manifestPath);
    const resolvedInContainer = path.resolve(manifestDir, relativeOrAbsolutePath);
    const relFromServices = path.relative(config.servicesDir, resolvedInContainer);
    normalizedRel = path.join('services', relFromServices);
  }

  // Ensure host path uses correct host separator
  const hostBase = config.hostWorkspaceDir.replace(/\/+$/, '').replace(/\\+$/, '');
  const finalHostPath = path.join(hostBase, normalizedRel);

  // Return formatted for Docker daemon
  return finalHostPath;
}

