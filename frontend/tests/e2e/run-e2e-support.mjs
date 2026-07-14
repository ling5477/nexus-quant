import http from 'node:http';
import net from 'node:net';

export const E2E_LOOPBACK_HOST = '127.0.0.1';

export function endpointToBaseURL(endpoint) {
    return `http://${endpoint.host}:${endpoint.port}`;
}

export function parseE2EBaseURL(value) {
    if (typeof value !== 'string') {
        throw new TypeError('E2E_BASE_URL must be a string');
    }

    const match = /^http:\/\/127\.0\.0\.1:(\d{1,5})\/?$/.exec(value);
    if (!match) {
        throw new Error('E2E_BASE_URL must be an explicit http://127.0.0.1:<port>/ endpoint');
    }

    const parsed = new URL(value);
    const port = Number(match[1]);
    if (
        parsed.protocol !== 'http:' ||
        parsed.hostname !== E2E_LOOPBACK_HOST ||
        parsed.username !== '' ||
        parsed.password !== '' ||
        parsed.pathname !== '/' ||
        parsed.search !== '' ||
        parsed.hash !== '' ||
        !Number.isInteger(port) ||
        port < 1 ||
        port > 65_535
    ) {
        throw new Error('E2E_BASE_URL is not a valid loopback endpoint');
    }

    return {host: E2E_LOOPBACK_HOST, port};
}

export function allocateLoopbackEndpoint() {
    return new Promise((resolve, reject) => {
        const server = net.createServer();

        server.once('error', reject);
        server.listen({host: E2E_LOOPBACK_HOST, port: 0, exclusive: true}, () => {
            const address = server.address();
            if (!address || typeof address === 'string') {
                server.close();
                reject(new Error('Unable to determine the selected E2E port'));
                return;
            }

            const endpoint = {host: E2E_LOOPBACK_HOST, port: address.port};
            server.close((error) => {
                if (error) {
                    reject(error);
                    return;
                }
                resolve(endpoint);
            });
        });
    });
}

export async function selectE2EEndpoint(baseURLOverride) {
    if (baseURLOverride !== undefined) {
        return parseE2EBaseURL(baseURLOverride);
    }
    return allocateLoopbackEndpoint();
}

export function createViteArgs(endpoint) {
    return [
        './node_modules/vite/bin/vite.js',
        '--host',
        endpoint.host,
        '--port',
        String(endpoint.port),
        '--strictPort',
    ];
}

export async function createRunnerConfiguration({baseURLOverride} = {}) {
    const endpoint = await selectE2EEndpoint(baseURLOverride);
    const baseURL = endpointToBaseURL(endpoint);
    return {
        endpoint,
        baseURL,
        playwrightBaseURL: baseURL,
        viteArgs: createViteArgs(endpoint),
    };
}

export function waitForServer(
    url,
    {timeoutMs = 120_000, intervalMs = 500, requestTimeoutMs = 2_000, signal} = {},
) {
    if (![timeoutMs, intervalMs, requestTimeoutMs].every((value) => Number.isFinite(value) && value > 0)) {
        throw new TypeError('Server wait timing values must be positive finite numbers');
    }

    return new Promise((resolve, reject) => {
        let settled = false;
        let request;
        let retryTimer;

        const timeoutTimer = setTimeout(() => {
            finish(new Error(`Timed out waiting for ${url}`));
        }, timeoutMs);

        const onAbort = () => {
            finish(new Error(`Stopped waiting for ${url}`));
        };

        const cleanup = () => {
            clearTimeout(timeoutTimer);
            clearTimeout(retryTimer);
            signal?.removeEventListener('abort', onAbort);
            request?.destroy();
        };

        const finish = (error) => {
            if (settled) {
                return;
            }
            settled = true;
            cleanup();
            if (error) {
                reject(error);
            } else {
                resolve();
            }
        };

        const probe = () => {
            if (settled) {
                return;
            }

            request = http.get(url, (response) => {
                request = undefined;
                response.resume();
                finish();
            });
            request.once('error', () => {
                request = undefined;
                if (!settled) {
                    retryTimer = setTimeout(probe, intervalMs);
                }
            });
            request.setTimeout(requestTimeoutMs, () => {
                request?.destroy();
            });
        };

        if (signal?.aborted) {
            finish(new Error(`Stopped waiting for ${url}`));
            return;
        }
        signal?.addEventListener('abort', onAbort, {once: true});
        probe();
    });
}

export function waitForServerOrChildExit(url, child, options = {}) {
    return new Promise((resolve, reject) => {
        const controller = new AbortController();
        let settled = false;

        const cleanup = () => {
            child.off('error', onError);
            child.off('exit', onExit);
        };

        const finish = (error) => {
            if (settled) {
                return;
            }
            settled = true;
            cleanup();
            controller.abort();
            if (error) {
                reject(error);
            } else {
                resolve();
            }
        };

        const onError = (error) => {
            finish(new Error(`Vite failed to start for ${url}: ${error.message}`, {cause: error}));
        };

        const onExit = (code, signal) => {
            finish(
                new Error(
                    `Vite exited before ${url} was ready (code=${code ?? 'null'}, signal=${signal ?? 'none'})`,
                ),
            );
        };

        child.once('error', onError);
        child.once('exit', onExit);

        if (child.exitCode !== null || child.signalCode !== null) {
            onExit(child.exitCode, child.signalCode);
            return;
        }

        waitForServer(url, {...options, signal: controller.signal}).then(
            () => finish(),
            (error) => {
                if (!settled) {
                    finish(error);
                }
            },
        );
    });
}

function hasExited(child) {
    return child.exitCode !== null || child.signalCode !== null;
}

function waitForChildExit(child, timeoutMs) {
    if (hasExited(child)) {
        return Promise.resolve(true);
    }

    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => finish(false), timeoutMs);

        const cleanup = () => {
            clearTimeout(timeout);
            child.off('error', onError);
            child.off('exit', onExit);
        };

        const finish = (exited) => {
            cleanup();
            resolve(exited);
        };

        const onError = (error) => {
            cleanup();
            reject(error);
        };

        const onExit = () => finish(true);
        child.once('error', onError);
        child.once('exit', onExit);
    });
}

export async function terminateChild(child, {graceMs = 5_000, forceWaitMs = 5_000} = {}) {
    if (hasExited(child)) {
        return;
    }

    const termSent = child.kill('SIGTERM');
    if (!termSent && !hasExited(child)) {
        throw new Error('Failed to send SIGTERM to Vite');
    }
    if (await waitForChildExit(child, graceMs)) {
        return;
    }

    const forceSent = child.kill('SIGKILL');
    if (!forceSent && !hasExited(child)) {
        throw new Error('Failed to force-terminate Vite');
    }
    if (!(await waitForChildExit(child, forceWaitMs))) {
        throw new Error('Vite did not exit within the bounded cleanup timeout');
    }
}
