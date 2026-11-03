import Docker from 'dockerode';
import stream from 'stream';
import { config, resolveHostBindMount } from '../config';
import {
  CliExecutionRequest,
  ContainerState,
  ServiceManifest,
  ServiceStatusSummary
} from '../types/manifest';
import { serviceScanner } from '../discovery/service-scanner';
import { logStreamer } from '../streaming/log-streamer';

export class DockerManager {
  private docker: Docker;
  private activeJobs: Map<string, { containerId: string; aborted?: boolean }> = new Map();

  constructor() {
    if (config.dockerSocket.startsWith('//./pipe/')) {
      this.docker = new Docker({ socketPath: config.dockerSocket });
    } else if (config.dockerSocket.startsWith('http://') || config.dockerSocket.startsWith('tcp://')) {
      const url = new URL(config.dockerSocket);
      this.docker = new Docker({ host: url.hostname, port: parseInt(url.port || '2375', 10) });
    } else {
      this.docker = new Docker({ socketPath: config.dockerSocket });
    }
  }

  public getDockerInstance(): Docker {
    return this.docker;
  }

  private getContainerName(serviceId: string, isEphemeral = false): string {
    if (isEphemeral) {
      return `hub-job-${serviceId}-${Date.now()}`;
    }
    return `hub-service-${serviceId}`;
  }

  public async getServiceStatuses(): Promise<ServiceStatusSummary[]> {
    const services = serviceScanner.getAll();
    let containers: Docker.ContainerInfo[] = [];

    try {
      containers = await this.docker.listContainers({ all: true });
    } catch (error) {
      console.error('[DockerManager] Failed to list containers from Docker daemon:', error);
    }

    return services.map((manifest) => {
      // Find corresponding container
      const activeJob = this.activeJobs.get(manifest.id);
      let matchedContainer = containers.find(
        (c) =>
          c.Labels &&
          (c.Labels['andemar.hub.service-id'] === manifest.id ||
            c.Names.some((n) => n.replace(/^\//, '') === this.getContainerName(manifest.id)))
      );

      if (activeJob) {
        matchedContainer = containers.find((c) => c.Id === activeJob.containerId) || matchedContainer;
      }

      let status: ContainerState = 'stopped';
      let startedAt: string | undefined;

      if (matchedContainer) {
        if (matchedContainer.State === 'running') {
          status = manifest.type === 'cli-job' ? 'executing' : 'running';
        } else if (matchedContainer.State === 'created' || matchedContainer.State === 'restarting') {
          status = 'starting';
        } else {
          status = 'stopped';
        }
      }

      let proxyPath: string | undefined;
      let directUrl: string | undefined;

      if (manifest.type === 'web-ui' || manifest.type === 'rest-api') {
        proxyPath = `/proxy/${manifest.id}`;
        if (manifest.ports?.host) {
          directUrl = `http://localhost:${manifest.ports.host}`;
        }
      }

      return {
        manifest,
        status,
        containerId: matchedContainer?.Id,
        containerName: matchedContainer?.Names?.[0]?.replace(/^\//, ''),
        startedAt,
        ports: manifest.ports,
        proxyPath,
        directUrl
      };
    });
  }

  public async startService(serviceId: string): Promise<ServiceStatusSummary> {
    const manifest = serviceScanner.get(serviceId);
    if (!manifest) {
      throw new Error(`Service "${serviceId}" not found in catalog.`);
    }

    if (manifest.type === 'cli-job') {
      throw new Error(`Service "${serviceId}" is a cli-job. Use the execute endpoint.`);
    }

    const containerName = this.getContainerName(serviceId);
    let container: Docker.Container | undefined;

    // Check if container already exists
    const containers = await this.docker.listContainers({ all: true });
    const existing = containers.find((c) =>
      c.Names.some((n) => n.replace(/^\//, '') === containerName)
    );

    if (existing) {
      container = this.docker.getContainer(existing.Id);
      if (existing.State === 'running') {
        console.log(`[DockerManager] Service ${serviceId} is already running.`);
        const statuses = await this.getServiceStatuses();
        return statuses.find((s) => s.manifest.id === serviceId)!;
      }
      console.log(`[DockerManager] Restarting existing container for ${serviceId}...`);
      await container.start();
    } else {
      console.log(`[DockerManager] Creating and starting new container for ${serviceId}...`);

      const portBindings: Record<string, Array<{ HostPort: string }>> = {};
      const exposedPorts: Record<string, {}> = {};

      if (manifest.ports?.container) {
        const cPort = `${manifest.ports.container}/tcp`;
        exposedPorts[cPort] = {};
        if (manifest.ports.host) {
          portBindings[cPort] = [{ HostPort: `${manifest.ports.host}` }];
        }
      }

      const binds: string[] = [];
      if (manifest.volumes && manifest.manifestPath) {
        for (const vol of manifest.volumes) {
          const hostPath = resolveHostBindMount(manifest.manifestPath, vol.hostPath);
          const mode = vol.mode || 'rw';
          binds.push(`${hostPath}:${vol.containerPath}:${mode}`);
        }
      }

      const envList: string[] = [];
      if (manifest.env) {
        for (const envItem of manifest.env) {
          if (envItem.default !== undefined) {
            envList.push(`${envItem.key}=${envItem.default}`);
          }
        }
      }

      // Check if image exists
      await this.ensureImageExists(manifest.imageName);

      container = await this.docker.createContainer({
        Image: manifest.imageName,
        name: containerName,
        Env: envList,
        ExposedPorts: exposedPorts,
        Labels: {
          'andemar.hub.managed': 'true',
          'andemar.hub.service-id': serviceId
        },
        HostConfig: {
          PortBindings: portBindings,
          Binds: binds,
          RestartPolicy: { Name: 'no' }
        }
      });

      // Connect to hub-network if available
      try {
        const network = this.docker.getNetwork(config.dockerNetwork);
        await network.connect({
          Container: container.id,
          EndpointConfig: {
            Aliases: [containerName, serviceId]
          }
        });
      } catch (err: any) {
        console.warn(`[DockerManager] Could not attach container to network ${config.dockerNetwork}: ${err.message}`);
      }

      await container.start();
    }

    // Attach log stream to logStreamer
    this.attachLogs(container, serviceId);

    logStreamer.broadcastStatus(serviceId, 'running', { containerName });

    const statuses = await this.getServiceStatuses();
    return statuses.find((s) => s.manifest.id === serviceId)!;
  }

  public async stopService(serviceId: string): Promise<void> {
    const containerName = this.getContainerName(serviceId);
    const containers = await this.docker.listContainers({ all: true });

    // Look for active job or persistent container
    const activeJob = this.activeJobs.get(serviceId);
    let target = containers.find((c) =>
      c.Names.some((n) => n.replace(/^\//, '') === containerName)
    );

    if (activeJob) {
      target = containers.find((c) => c.Id === activeJob.containerId) || target;
      activeJob.aborted = true;
    }

    if (!target) {
      console.log(`[DockerManager] No running container found for service ${serviceId}.`);
      return;
    }

    const container = this.docker.getContainer(target.Id);

    if (target.State === 'running') {
      try {
        console.log(`[DockerManager] Stopping container ${target.Id} (${containerName})...`);
        await container.stop({ t: 5 });
      } catch (err: any) {
        console.warn(`[DockerManager] Error stopping container: ${err.message}`);
        await container.kill().catch(() => {});
      }
    }

    // Remove if ephemeral or user initiated full stop
    try {
      await container.remove({ force: true, v: true });
    } catch (err: any) {
      console.warn(`[DockerManager] Remove container notice: ${err.message}`);
    }

    this.activeJobs.delete(serviceId);
    logStreamer.broadcastStatus(serviceId, 'stopped');
  }

  public async executeCliJob(
    serviceId: string,
    req: CliExecutionRequest
  ): Promise<{ jobId: string; status: string }> {
    const manifest = serviceScanner.get(serviceId);
    if (!manifest) {
      throw new Error(`Service "${serviceId}" not found in catalog.`);
    }

    if (manifest.type !== 'cli-job') {
      throw new Error(`Service "${serviceId}" is not a cli-job.`);
    }

    if (this.activeJobs.has(serviceId)) {
      throw new Error(`Service "${serviceId}" is already running a job.`);
    }

    logStreamer.clearBuffer(serviceId);
    logStreamer.broadcastLog(serviceId, `\x1b[1;36m[Hub Orchestrator] Initializing job for ${manifest.name}...\x1b[0m\r\n`);

    await this.ensureImageExists(manifest.imageName);

    // Assemble entrypoint command and arguments
    const cmd: string[] = [];
    if (manifest.defaultCommand && manifest.defaultCommand.length > 0) {
      cmd.push(...manifest.defaultCommand);
    }
    if (req.command && req.command.length > 0) {
      cmd.push(...req.command);
    }

    // Parse parameters
    if (req.parameters) {
      for (const [flag, value] of Object.entries(req.parameters)) {
        if (value === undefined || value === null || value === '') continue;
        if (typeof value === 'boolean') {
          if (value) cmd.push(flag);
        } else {
          cmd.push(flag, String(value));
        }
      }
    }

    // Prepare volume binds
    const binds: string[] = [];
    if (manifest.volumes && manifest.manifestPath) {
      for (const vol of manifest.volumes) {
        const hostPath = resolveHostBindMount(manifest.manifestPath, vol.hostPath);
        const mode = vol.mode || 'rw';
        binds.push(`${hostPath}:${vol.containerPath}:${mode}`);
      }
    }

    // Prepare environment variables
    const envList: string[] = [];
    if (manifest.env) {
      for (const envItem of manifest.env) {
        envList.push(`${envItem.key}=${envItem.default ?? ''}`);
      }
    }
    if (req.envOverrides) {
      for (const [k, v] of Object.entries(req.envOverrides)) {
        envList.push(`${k}=${v}`);
      }
    }

    // Hardware GPU configuration
    const hostConfig: Docker.HostConfig = {
      Binds: binds,
      AutoRemove: true
    };

    if (manifest.hardware?.gpu) {
      logStreamer.broadcastLog(serviceId, `\x1b[1;33m[Hub Orchestrator] Requesting NVIDIA GPU devices (--gpus all)...\x1b[0m\r\n`);
      hostConfig.DeviceRequests = [
        {
          Driver: 'nvidia',
          Count: -1,
          Capabilities: [['gpu', 'compute', 'utility']]
        }
      ];
    }

    const containerName = this.getContainerName(serviceId, true);
    logStreamer.broadcastLog(
      serviceId,
      `\x1b[90mCommand: ${cmd.join(' ')}\x1b[0m\r\n`
    );

    const container = await this.docker.createContainer({
      Image: manifest.imageName,
      name: containerName,
      Cmd: cmd.length > 0 ? cmd : undefined,
      Env: envList,
      Tty: false,
      Labels: {
        'andemar.hub.managed': 'true',
        'andemar.hub.service-id': serviceId,
        'andemar.hub.job': 'true'
      },
      HostConfig: hostConfig
    });

    this.activeJobs.set(serviceId, { containerId: container.id });
    logStreamer.broadcastStatus(serviceId, 'executing', { containerId: container.id, containerName });

    // Attach stream before starting
    const logStream = await container.attach({
      stream: true,
      stdout: true,
      stderr: true
    });

    const stdoutStream = new stream.PassThrough();
    const stderrStream = new stream.PassThrough();

    this.docker.modem.demuxStream(logStream, stdoutStream, stderrStream);

    stdoutStream.on('data', (chunk: Buffer) => {
      logStreamer.broadcastLog(serviceId, chunk.toString('utf-8'));
    });

    stderrStream.on('data', (chunk: Buffer) => {
      logStreamer.broadcastLog(serviceId, `\x1b[31m${chunk.toString('utf-8')}\x1b[0m`);
    });

    // Start container execution
    await container.start();

    // Async execution monitor
    container.wait(async (err, data) => {
      this.activeJobs.delete(serviceId);
      const exitCode = data ? data.StatusCode : (err ? 1 : 0);
      if (exitCode === 0) {
        logStreamer.broadcastLog(
          serviceId,
          `\r\n\x1b[1;32m[Hub Orchestrator] Job completed successfully (Exit code: 0).\x1b[0m\r\n`
        );
      } else {
        logStreamer.broadcastLog(
          serviceId,
          `\r\n\x1b[1;31m[Hub Orchestrator] Job finished with non-zero exit code: ${exitCode}.\x1b[0m\r\n`
        );
      }
      logStreamer.broadcastStatus(serviceId, 'stopped', { exitCode });
    });

    return { jobId: container.id, status: 'executing' };
  }

  private async attachLogs(container: Docker.Container, serviceId: string): Promise<void> {
    try {
      const logStream = await container.logs({
        follow: true,
        stdout: true,
        stderr: true,
        tail: 100
      });

      const stdoutStream = new stream.PassThrough();
      const stderrStream = new stream.PassThrough();

      this.docker.modem.demuxStream(logStream, stdoutStream, stderrStream);

      stdoutStream.on('data', (chunk: Buffer) => {
        logStreamer.broadcastLog(serviceId, chunk.toString('utf-8'));
      });

      stderrStream.on('data', (chunk: Buffer) => {
        logStreamer.broadcastLog(serviceId, `\x1b[31m${chunk.toString('utf-8')}\x1b[0m`);
      });
    } catch (err: any) {
      console.warn(`[DockerManager] Notice attaching logs to ${serviceId}: ${err.message}`);
    }
  }

  private async ensureImageExists(imageName: string): Promise<void> {
    try {
      const image = this.docker.getImage(imageName);
      await image.inspect();
    } catch (err) {
      console.warn(`[DockerManager] Image "${imageName}" not found locally. It must be built via Dockerfile before launch.`);
      throw new Error(`Docker image "${imageName}" is not available locally. Please build it first.`);
    }
  }
}

export const dockerManager = new DockerManager();

