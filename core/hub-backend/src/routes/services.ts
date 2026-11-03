import { Router, Request, Response } from 'express';
import { serviceScanner } from '../discovery/service-scanner';
import { dockerManager } from '../docker/docker-manager';
import { CliExecutionRequest } from '../types/manifest';

const router = Router();

// GET /api/services - List all discovered tools and current Docker status
router.get('/', async (req: Request, res: Response) => {
  try {
    const statuses = await dockerManager.getServiceStatuses();
    res.json({
      success: true,
      count: statuses.length,
      services: statuses
    });
  } catch (error: any) {
    console.error('[API] Error listing services:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// POST /api/services/reload - Re-scan manifests on demand
router.post('/reload', async (req: Request, res: Response) => {
  try {
    const discovered = serviceScanner.scan();
    const statuses = await dockerManager.getServiceStatuses();
    res.json({
      success: true,
      message: `Scanned ${discovered.length} service manifest(s).`,
      count: discovered.length,
      services: statuses
    });
  } catch (error: any) {
    console.error('[API] Error reloading services:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// GET /api/services/:id - Get a specific service details
router.get('/:id', async (req: Request, res: Response) => {
  try {
    const serviceId = req.params.id;
    const statuses = await dockerManager.getServiceStatuses();
    const found = statuses.find((s) => s.manifest.id === serviceId);

    if (!found) {
      res.status(404).json({ success: false, error: `Service "${serviceId}" not found.` });
      return;
    }

    res.json({ success: true, service: found });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

// POST /api/services/:id/start - Start a web-ui or rest-api container
router.post('/:id/start', async (req: Request, res: Response) => {
  const serviceId = req.params.id;
  try {
    const result = await dockerManager.startService(serviceId);
    res.json({
      success: true,
      message: `Service "${serviceId}" started.`,
      service: result
    });
  } catch (error: any) {
    console.error(`[API] Failed to start service ${serviceId}:`, error);
    res.status(400).json({ success: false, error: error.message });
  }
});

// POST /api/services/:id/stop - Stop a running service container
router.post('/:id/stop', async (req: Request, res: Response) => {
  const serviceId = req.params.id;
  try {
    await dockerManager.stopService(serviceId);
    res.json({
      success: true,
      message: `Service "${serviceId}" stopped.`
    });
  } catch (error: any) {
    console.error(`[API] Failed to stop service ${serviceId}:`, error);
    res.status(400).json({ success: false, error: error.message });
  }
});

// POST /api/services/:id/execute - Launch an ephemeral cli-job container
router.post('/:id/execute', async (req: Request, res: Response) => {
  const serviceId = req.params.id;
  const executionReq: CliExecutionRequest = req.body || {};

  try {
    const result = await dockerManager.executeCliJob(serviceId, executionReq);
    res.json({
      success: true,
      message: `CLI Job "${serviceId}" initiated.`,
      job: result
    });
  } catch (error: any) {
    console.error(`[API] Failed to execute job ${serviceId}:`, error);
    res.status(400).json({ success: false, error: error.message });
  }
});

export default router;

