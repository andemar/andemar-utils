export type ServiceType = 'cli-job' | 'web-ui' | 'rest-api';
export type ContainerState = 'stopped' | 'starting' | 'running' | 'executing' | 'error';

export interface VolumeMount {
  name: string;
  hostPath: string;
  containerPath: string;
  mode?: 'ro' | 'rw';
  description?: string;
}

export interface ParameterDef {
  name: string;
  label?: string;
  type: 'string' | 'number' | 'boolean' | 'select';
  options?: string[];
  default?: string | number | boolean;
  required?: boolean;
  description?: string;
}

export interface ApiEndpoint {
  id: string;
  path: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  description?: string;
  consumes?: string;
  produces?: string;
  fileField?: string;
  parameters?: ParameterDef[];
}

export interface ServiceManifest {
  id: string;
  name: string;
  description: string;
  icon: string;
  type: ServiceType;
  imageName: string;
  dockerfileDir?: string;
  hardware?: {
    gpu?: boolean;
  };
  ports?: {
    container: number;
    host?: number;
  };
  defaultCommand?: string[];
  volumes?: VolumeMount[];
  parameters?: ParameterDef[];
  api?: {
    healthCheckPath?: string;
    endpoints?: ApiEndpoint[];
  };
}

export interface ServiceStatusSummary {
  manifest: ServiceManifest;
  status: ContainerState;
  containerId?: string;
  containerName?: string;
  startedAt?: string;
  ports?: {
    container: number;
    host?: number;
  };
  proxyPath?: string;
  directUrl?: string;
}

export interface CliExecutionRequest {
  command?: string[];
  parameters?: Record<string, string | number | boolean>;
  envOverrides?: Record<string, string>;
  volumeOverrides?: Record<string, string>;
}

