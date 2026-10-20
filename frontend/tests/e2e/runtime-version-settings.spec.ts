import {expect, test} from 'playwright/test';
import {loginForReadOnlyChecks} from './read-only-console-support';

// 登录只用本任务身份，避免trace保存密码或认证头；截图由界面验证单独采集。
test.use({trace: 'off'});

test('正式制品版本与未知包版本分离，更新步骤使用宿主安装器且不执行变更', async ({page}) => {
    const texts = (await loginForReadOnlyChecks(page)).systemVersion;
    const responsePromise = page.waitForResponse(
        (response) =>
            response.url().endsWith('/api/system/version') && response.request().method() === 'GET',
    );
    let writes = 0;
    page.on('request', (request) => {
        if (request.url().includes('/api/') && !['GET', 'HEAD'].includes(request.method()))
            writes++;
    });
    await page.goto('/settings');
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const payload = await response.json();
    expect(payload.serviceVersion).toBe('0.1.0-SNAPSHOT');
    expect(payload.installedPackageVersion).toBeNull();
    expect(payload.packageIdentityStatus).toBe('UNAVAILABLE');
    expect(payload.automaticUpdate).toBe(false);
    await expect(page.getByText('0.1.0-SNAPSHOT', {exact: true})).toBeVisible();
    await expect(page.getByText(texts.noPackage)).toBeVisible();
    await page.getByRole('button', {name: texts.steps, exact: true}).click();
    const steps = page.getByRole('dialog', {name: texts.steps, exact: true});
    await expect(steps).toContainText('-Action check-update');
    await expect(steps).toContainText('-Action update');
    await expect(steps).toContainText('-TrustedManifestSha256');
    await expect(steps).toContainText('UPDATE');
    expect(await steps.innerText()).not.toContain('-ConfirmUpdate');
    await steps.getByRole('tab', {name: 'POSIX', exact: true}).click();
    await expect(steps).toContainText('NQ_INSTALL_ROOT=');
    await expect(steps).toContainText('check-update --package');
    await expect(steps).toContainText('--manifest-sha256');
    await steps.getByRole('button', {name: /^(关闭|Close)$/}).click();
    await expect(steps).not.toBeVisible();
    expect(writes).toBe(0);
});

test('版本读取失败fixture不回退成根VERSION或虚报无更新', async ({page}) => {
    const texts = (await loginForReadOnlyChecks(page)).systemVersion;
    await page.route('**/api/system/version', (route) =>
        route.fulfill({
            status: 503,
            json: {code: 'SOURCE_UNAVAILABLE', message: 'isolated failure fixture'},
        }),
    );
    await page.goto('/settings');
    await expect(page.getByText(texts.readFailed)).toBeVisible();
    await expect(page.getByText('1.0.0', {exact: true})).toHaveCount(0);
    await expect(page.getByText('0.1.0-SNAPSHOT', {exact: true})).toHaveCount(0);
});

test('安装器声明fixture与服务版本分别呈现，完整源摘要只在身份详情展示', async ({page}) => {
    const texts = (await loginForReadOnlyChecks(page)).systemVersion;
    await page.route('**/api/system/version', (route) =>
        route.fulfill({
            status: 200,
            json: {
                serviceVersion: '0.1.0-SNAPSHOT',
                serviceArtifact: 'nq-app',
                installedPackageVersion: '1.0.0',
                sourceCommit: 'a'.repeat(40),
                releaseSourceHash: 'b'.repeat(64),
                packageManifestSha256: 'c'.repeat(64),
                packagedSchemaVersion: '9',
                packageIdentityStatus: 'INSTALLER_DECLARED',
                automaticUpdate: false,
                updateExecutor: 'HOST_INSTALLER',
                reasons: [],
            },
        }),
    );
    await page.goto('/settings');
    await expect(page.getByText('1.0.0', {exact: true})).toBeVisible();
    await expect(page.getByText('0.1.0-SNAPSHOT', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: texts.identity}).click();
    await expect(page.getByText('a'.repeat(40), {exact: true})).toBeVisible();
    await expect(page.getByText(texts.statuses.INSTALLER_DECLARED)).toBeVisible();
});
