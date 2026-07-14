import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import http from 'node:http';
import net from 'node:net';
import test from 'node:test';

import {
    E2E_LOOPBACK_HOST,
    allocateLoopbackEndpoint,
    createRunnerConfiguration,
    createViteArgs,
    endpointToBaseURL,
    parseE2EBaseURL,
    waitForServer,
    waitForServerOrChildExit,
} from './run-e2e-support.mjs';

test('dynamic endpoint allocation returns a legal loopback port', async () => {
    const endpoint = await allocateLoopbackEndpoint();
    assert.equal(endpoint.host, E2E_LOOPBACK_HOST);
    assert.ok(Number.isInteger(endpoint.port));
    assert.ok(endpoint.port >= 1 && endpoint.port <= 65_535);

    const listener = net.createServer();
    await new Promise((resolve, reject) => {
        listener.once('error', reject);
        listener.listen(endpoint.port, endpoint.host, resolve);
    });
    await new Promise((resolve) => listener.close(resolve));
});

test('explicit loopback base URL is parsed and canonicalized', () => {
    const endpoint = parseE2EBaseURL('http://127.0.0.1:41001/');
    assert.deepEqual(endpoint, {host: E2E_LOOPBACK_HOST, port: 41_001});
    assert.equal(endpointToBaseURL(endpoint), 'http://127.0.0.1:41001');
});

test('non-loopback hosts are rejected', () => {
    for (const value of [
        'http://localhost:41001',
        'http://0.0.0.0:41001',
        'http://192.0.2.1:41001',
        'https://127.0.0.1:41001',
    ]) {
        assert.throws(() => parseE2EBaseURL(value));
    }
});

test('credential-bearing URLs are rejected', () => {
    assert.throws(() => parseE2EBaseURL('http://user:password@127.0.0.1:41001'));
});

test('missing, out-of-range, and decorated ports are rejected', () => {
    for (const value of [
        'http://127.0.0.1',
        'http://127.0.0.1:0',
        'http://127.0.0.1:65536',
        'http://127.0.0.1:41001/path',
        'http://127.0.0.1:41001/?query=1',
        'http://127.0.0.1:41001/#fragment',
    ]) {
        assert.throws(() => parseE2EBaseURL(value));
    }
});

test('Vite args use the selected port', () => {
    const args = createViteArgs({host: E2E_LOOPBACK_HOST, port: 41_002});
    assert.equal(args[args.indexOf('--host') + 1], E2E_LOOPBACK_HOST);
    assert.equal(args[args.indexOf('--port') + 1], '41002');
});

test('Vite args enable strict port mode', () => {
    const args = createViteArgs({host: E2E_LOOPBACK_HOST, port: 41_003});
    assert.ok(args.includes('--strictPort'));
});

test('waitForServer resolves when a local server becomes ready', async (t) => {
    const server = http.createServer((_request, response) => response.end('ready'));
    await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen({host: E2E_LOOPBACK_HOST, port: 0}, resolve);
    });
    t.after(() => new Promise((resolve) => server.close(resolve)));

    const address = server.address();
    assert.ok(address && typeof address !== 'string');
    await waitForServer(`http://${E2E_LOOPBACK_HOST}:${address.port}`, {
        timeoutMs: 1_000,
        intervalMs: 20,
        requestTimeoutMs: 100,
    });
});

test('Vite exit before readiness rejects without waiting for the full timeout', async () => {
    const child = new EventEmitter();
    child.exitCode = null;
    child.signalCode = null;
    const startedAt = Date.now();

    const waiting = waitForServerOrChildExit('http://127.0.0.1:1', child, {
        timeoutMs: 5_000,
        intervalMs: 100,
        requestTimeoutMs: 100,
    });
    setTimeout(() => {
        child.exitCode = 7;
        child.emit('exit', 7, null);
    }, 25);

    await assert.rejects(waiting, /code=7/);
    assert.ok(Date.now() - startedAt < 1_000);
});

test('runner configuration uses one endpoint for Vite and Playwright', async () => {
    const configuration = await createRunnerConfiguration({
        baseURLOverride: 'http://127.0.0.1:41004',
    });
    assert.equal(configuration.baseURL, 'http://127.0.0.1:41004');
    assert.equal(configuration.playwrightBaseURL, configuration.baseURL);
    assert.equal(
        configuration.viteArgs[configuration.viteArgs.indexOf('--port') + 1],
        String(configuration.endpoint.port),
    );
});
