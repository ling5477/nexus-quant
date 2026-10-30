import {readFileSync, writeFileSync, mkdirSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {test, expect} from 'playwright/test';

const readyPath = process.env.NQ_FACTOR_E2E_READY;
if (!readyPath) throw new Error('Task-owned factor fixture required');
const fixture = JSON.parse(readFileSync(readyPath, 'utf8')) as {
    username: string; password: string; btcDatasetId: string; ethDatasetId: string; dataSource: string;
};
if (fixture.dataSource !== 'SYNTHETIC_CONTROLLED_JAVA_V2') throw new Error('Disclosed Java V2 fixture required');

for (const [symbol, count, datasetId] of [['BTC-USDT', 4320, fixture.btcDatasetId], ['ETH-USDT', 2160, fixture.ethDatasetId]] as const) {
test(`${symbol}: Java V2 download → offline Python CLI → authenticated NQ preview; read-only and digest rejection`, async ({page}, info) => {
    const evidence = info.outputPath('roundtrip'); mkdirSync(evidence, {recursive: true});
    const requests: string[] = [];
    page.on('request', request => {
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()))
            requests.push(`${request.method()} ${new URL(request.url()).pathname}`);
    });
    await page.goto('/login');
    await page.getByLabel('账号', {exact: true}).fill(fixture.username);
    await page.getByLabel('密码', {exact: true}).fill(fixture.password);
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/dashboard$/, {timeout: 30_000});
    await page.getByRole('button', {name: /^(确认阅读|Confirm read)$/}).click();
    await page.getByTestId('language-select').click();
    await page.getByText('简体中文', {exact: true}).last().click();
    await page.goto('/research');
    const panel = page.getByTestId('factor-research-panel');
    await expect(panel).toBeVisible();
    await panel.getByRole('button', {name: '选择数据集', exact: true}).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('数据集 ID', {exact: true}).fill(datasetId);
    await dialog.getByRole('button', {name: /查\s*询/}).click();
    await dialog.getByRole('button', {name: '选用', exact: true}).click();
    const pending = page.waitForEvent('download');
    await panel.getByRole('button', {name: '下载冻结研究输入', exact: true}).click();
    const downloaded = await pending;
    const input = path.join(evidence, 'frozen-input.json'); await downloaded.saveAs(input);
    const artifactFile = path.join(evidence, 'factor-artifact.json');
    const started = performance.now();
    execFileSync('python', ['-m', 'nq_research', '--frozen-input', input, '--factor-output', artifactFile,
        '--created-at', '2026-10-30T23:59:59Z', '--seed', '42', '--bootstrap-repetitions', '200'],
        {cwd: path.resolve('../research/py'), timeout: 60_000});
    const pythonElapsedMs = performance.now() - started;
    const artifact = JSON.parse(readFileSync(artifactFile, 'utf8'));
    expect(artifact.dataset.datasetId).toBe(datasetId);
    expect(artifact.barCount).toBe(count);
    expect(artifact.factors).toHaveLength(3);
    const response = page.waitForResponse(r => r.url().endsWith('/factor-research/preview'));
    await panel.getByLabel('本地因子 Artifact').setInputFiles(artifactFile);
    expect((await response).status()).toBe(200);
    await expect(panel.getByText('数据身份与 Artifact 摘要已核验', {exact: true})).toBeVisible();
    await expect(panel.getByText(artifact.dataset.datasetVersionId, {exact: true})).toBeVisible();
    await panel.getByText(/收益动量 · RETURN_MOMENTUM/).click();
    await expect(panel.getByRole('columnheader', {name: 'Pearson 相关系数', exact: true})).toBeVisible();
    await expect(panel.getByText('样本外验证', {exact: true})).toBeVisible();
    await page.screenshot({path: path.join(evidence, 'desktop-report.png'), fullPage: true});
    await page.setViewportSize({width: 390, height: 844});
    await expect(panel.getByText('数据身份与 Artifact 摘要已核验', {exact: true})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2)).toBeTruthy();
    await page.getByTestId('language-select').click();
    await page.getByText('English', {exact: true}).last().click();
    await expect(panel.getByText('Dataset identity and Artifact checksum verified', {exact: true})).toBeVisible();
    await page.screenshot({path: path.join(evidence, 'narrow-en-report.png'), fullPage: true});
    const otherLogin = await page.request.post('/api/auth/login', {data: {username: 'factor-other', password: fixture.password}});
    expect(otherLogin.status()).toBe(200);
    const otherToken = (await otherLogin.json()).accessToken as string;
    const crossOwner = await page.request.get(`/api/marketdata/datasets/${datasetId}/factor-research/input`,
        {headers: {Authorization: `Bearer ${otherToken}`}});
    expect(crossOwner.status()).toBe(404);
    artifact.dataset.contentSha256 = '0'.repeat(64);
    const bad = path.join(evidence, 'bad-checksum.json'); writeFileSync(bad, JSON.stringify(artifact));
    const denied = page.waitForResponse(r => r.url().endsWith('/factor-research/preview'));
    await panel.getByLabel('Local factor Artifact').setInputFiles(bad);
    expect((await denied).status()).toBe(400);
    await expect(panel.getByText('Dataset identity and Artifact checksum verified', {exact: true})).toHaveCount(0);
    const before = requests.length;
    await page.reload(); await expect(page.getByText('Offline Python factor research', {exact: true})).toBeVisible();
    await page.waitForLoadState('networkidle'); expect(requests.length).toBe(before);
    const forbidden = requests.filter(item => !item.includes('/auth/') && !item.includes('/preferences')
        && !item.endsWith('/factor-research/preview') && !item.includes('/disclaimer'));
    expect(forbidden).toEqual([]);
    writeFileSync(path.join(evidence, 'facts.json'), JSON.stringify({dataSource: fixture.dataSource,
        datasetId, datasetVersionId: artifact.dataset.datasetVersionId, crossOwnerExport: crossOwner.status(),
        bars: count, pythonElapsedMs, inputBytes: readFileSync(input).length, artifactBytes: readFileSync(artifactFile).length,
        artifactSha256: createHash('sha256').update(readFileSync(artifactFile)).digest('hex'), requests}, null, 2));
});
}
