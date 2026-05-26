import http from 'node:http';
import {spawn} from 'node:child_process';

const host = '127.0.0.1';
const port = 5179;
const baseURL = process.env.E2E_BASE_URL ?? `http://${host}:${port}`;
const viteArgs = ['./node_modules/vite/bin/vite.js', '--host', host, '--port', String(port)];
const playwrightArgs = ['./node_modules/playwright/cli.js', 'test', ...process.argv.slice(2)];

function waitForServer(url, timeoutMs) {
    const startedAt = Date.now();

    return new Promise((resolve, reject) => {
        const probe = () => {
            const request = http.get(url, (response) => {
                response.resume();
                resolve();
            });

            request.on('error', () => {
                if (Date.now() - startedAt > timeoutMs) {
                    reject(new Error(`Timed out waiting for ${url}`));
                    return;
                }
                setTimeout(probe, 500);
            });

            request.setTimeout(2_000, () => {
                request.destroy();
            });
        };

        probe();
    });
}

function run(command, args, options) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, options);
        child.on('error', reject);
        child.on('exit', (code, signal) => {
            resolve({code: code ?? 1, signal});
        });
    });
}

const vite = spawn(process.execPath, viteArgs, {
    cwd: process.cwd(),
    env: process.env,
    stdio: 'inherit',
});

let exitCode = 1;

try {
    await waitForServer(baseURL, 120_000);

    const result = await run(process.execPath, playwrightArgs, {
        cwd: process.cwd(),
        env: {
            ...process.env,
            E2E_BASE_URL: baseURL,
            E2E_EXTERNAL_DEV_SERVER: 'true',
        },
        stdio: 'inherit',
    });
    exitCode = result.code;
} finally {
    if (!vite.killed) {
        vite.kill();
    }
}

process.exit(exitCode);
