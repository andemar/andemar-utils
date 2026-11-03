import { z } from 'zod';

export type ServiceType = 'cli-job' | 'web-ui' | 'rest-api';

export interface VolumeMountDeclaration {
  name: string;
  hostPath: string;
  containerPath: string;
  mode?: 'ro' | 'rw';
  description?: string;
}

export interface EnvVariableDeclaration {
  key: string;
  default?: string;
  description?: string;
  editable?: boolean;
}

export interface ParameterDeclaration {
  name: string;
  label?: string;
  type: 'string' | 'number' | 'boolean' | 'select';
  options?: string[];
  default?: string | number | boolean;
  required?: boolean;
  description?: string;
}

export interface ApiEndpointDeclaration {
  id: string;
  path: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  description?: string;
  consumes?: string;
  produces?: string;
  fileField?: string;
  parameters?: ParameterDeclaration[];
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
  volumes?: VolumeMountDeclaration[];
  env?: EnvVariableDeclaration[];
  parameters?: ParameterDeclaration[];
  api?: {
    healthCheckPath?: string;
    endpoints?: ApiEndpointDeclaration[];
  };
  manifestPath?: string;
}

export type ContainerState = 'stopped' | 'starting' | 'running' | 'executing' | 'error';

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

export const ManifestSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  description: z.string().default(''),
  icon: z.string().default('terminal'),
  type: z.enum(['cli-job', 'web-ui', 'rest-api']),
  imageName: z.string().min(1),
  dockerfileDir: z.string().optional(),
  hardware: z
    .object({
      gpu: z.boolean().optional()
    })
    .optional(),
  ports: z
    .object({
      container: z.number(),
      host: z.number().optional()
    })
    .optional(),
  defaultCommand: z.array(z.string()).optional(),
  volumes: z
    .array(
      z.object({
        name: z.string(),
        hostPath: z.string(),
        containerPath: z.string(),
        mode: z.enum(['ro', 'rw']).optional(),
        description: z.string().optional()
      })
    )
    .optional(),
  env: z
    .array(
      z.object({
        key: z.string(),
        default: z.string().optional(),
        description: z.string().optional(),
        editable: z.boolean().optional()
      })
    )
    .optional(),
  parameters: z
    .array(
      z.object({
        name: z.string(),
        label: z.string().optional(),
        type: z.enum(['string', 'number', 'boolean', 'select']),
        options: z.array(z.string()).optional(),
        default: z.union([z.string(), z.number(), z.boolean()]).optional(),
        required: z.boolean().optional(),
        description: z.string().optional()
      })
    )
    .optional(),
  api: z
    .object({
      healthCheckPath: z.string().optional(),
      endpoints: z
        .array(
          z.object({
            id: z.string(),
            path: z.string(),
            method: z.enum(['GET', 'POST', 'PUT', 'DELETE']),
            description: z.string().optional(),
            consumes: z.string().optional(),
            produces: z.string().optional(),
            fileField: z.string().optional(),
            parameters: z
              .array(
                z.object({
                  name: z.string(),
                  label: z.string().optional(),
                  type: z.enum(['string', 'number', 'boolean', 'select']),
                  options: z.array(z.string()).optional(),
                  default: z.union([z.string(), z.number(), z.boolean()]).optional(),
                  required: z.boolean().optional(),
                  description: z.string().optional()
                })
              )
              .optional()
          })
        )
        .optional()
    })
    .optional()
});

