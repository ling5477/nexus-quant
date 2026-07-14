import {spawn} from 'node:child_process';
import {
    createRunnerConfiguration,
    terminateChild,
    waitForServerOrChildExit,
} from './run-e2e-support.mjs';

function run(command, args, options, onSpawn) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, options);
        onSpawn?.(child);
        child.on('error', reject);
        child.on('exit', (code, signal) => {
            resolve({code: code ?? 1, signal});
        });
    });
}

const configuration = await createRunnerConfiguration({
    baseURLOverride: process.env.E2E_BASE_URL,
});
const {baseURL, endpoint, playwrightBaseURL, viteArgs} = configuration;
const playwrightArgs = ['./node_modules/playwright/cli.js', 'test', ...process.argv.slice(2)];

console.info(`Selected E2E Vite endpoint: ${baseURL}`);
console.info('Vite strict port mode: enabled');

const vite = spawn(process.execPath, viteArgs, {
    cwd: process.cwd(),
    env: process.env,
    stdio: 'inherit',
});

const runningChildren = new Set([vite]);
let receivedSignal;
const handleSignal = (signal) => {
    if (receivedSignal) {
        return;
    }
    receivedSignal = signal;
    for (const child of runningChildren) {
        if (child.exitCode === null && child.signalCode === null) {
            child.kill('SIGTERM');
        }
    }
};
process.on('SIGINT', handleSignal);
process.on('SIGTERM', handleSignal);

let exitCode = 1;
let primaryError;

try {
    await waitForServerOrChildExit(baseURL, vite, {timeoutMs: 120_000});

    const result = await run(process.execPath, playwrightArgs, {
        cwd: process.cwd(),
        env: {
            ...process.env,
            E2E_BASE_URL: playwrightBaseURL,
            E2E_EXTERNAL_DEV_SERVER: 'true',
        },
        stdio: 'inherit',
    }, (child) => {
        runningChildren.add(child);
        child.once('exit', () => runningChildren.delete(child));
    });
    exitCode = result.code;
} catch (error) {
    primaryError = error;
} finally {
    try {
        await terminateChild(vite);
    } catch (cleanupError) {
        primaryError = primaryError
            ? new AggregateError([primaryError, cleanupError], 'E2E runner and Vite cleanup both failed')
            : cleanupError;
    }
    runningChildren.delete(vite);
    process.off('SIGINT', handleSignal);
    process.off('SIGTERM', handleSignal);
}

if (receivedSignal) {
    exitCode = receivedSignal === 'SIGINT' ? 130 : 143;
}
if (primaryError) {
    console.error(
        `E2E runner failed for ${endpoint.host}:${endpoint.port}: ${primaryError.message}`,
    );
    exitCode = 1;
}

process.exitCode = exitCode;
