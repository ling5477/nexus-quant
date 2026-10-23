import {expect, test, type Page} from 'playwright/test';
import {createHash} from 'node:crypto';

// 本文件的 API 与清单均为显式界面夹具，不承担安装器、数据库或真实发行资格证明。
test.use({trace: 'off'});
const runtime = {
    serviceVersion: '0.1.0-SNAPSHOT', serviceArtifact: 'nq-app',
    installedPackageVersion: '1.0.1', sourceCommit: 'a'.repeat(40),
    releaseSourceHash: 'b'.repeat(64), packageManifestSha256: 'c'.repeat(64),
    packagedSchemaVersion: '47', packageIdentityStatus: 'INSTALLER_DECLARED',
    automaticUpdate: false, updateExecutor: 'HOST_INSTALLER', reasons: [],
};

async function fixture(page: Page, locale = 'zh-CN', version: object = runtime) {
    await page.addInitScript(({locale}) => {
        localStorage.setItem('nq.locale', locale);
        localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'release-ui-fixture', tokenType: 'Bearer', expiresAt: '2999-01-01T00:00:00Z',
            username: 'release-ui', roles: ['ADMIN'], mustChangePassword: false,
        }));
    }, {locale});
    await page.route(/^https?:\/\/[^/]+\/api\//, (route) => route.fulfill({status: 200, json: []}));
    await page.route('**/api/distribution', (route) => route.fulfill({json: {distribution: 'NQ_SELF_HOSTED'}}));
    await page.route('**/api/auth/me', (route) => route.fulfill({json: {
        userId: 7, username: 'release-ui', roles: ['ADMIN'], authenticated: true,
        mustChangePassword: false, defaultExchangeAccountId: null,
    }}));
    await page.route('**/api/user/preferences', (route) => route.fulfill({json: {
        version: 0, locale, hideLoginDisclosures: true, watchlist: [], notificationsReadAt: null,
        defaultMarket: {exchangeCode: 'OKX', marketType: 'SPOT', symbol: 'BTC-USDT'},
    }}));
    await page.route('**/api/system/version', (route) => route.fulfill({json: version}));
}

for (const locale of ['zh-CN', 'en-US']) {
    test(`${locale}: 首次提示按安装版本只读一次，设置可重看历史，候选不等于可更新`, async ({page}) => {
        await fixture(page, locale);
        let writes = 0;
        page.on('request', (request) => {
            if (request.url().includes('/api/') && !['GET', 'HEAD'].includes(request.method())) writes++;
        });
        await page.goto('/settings?section=version');
        const notice = page.getByRole('dialog', {name: `${locale === 'zh-CN' ? '本次更新' : 'What’s New'} · 1.0.1`});
        await expect(notice).toBeVisible();
        await expect(notice).toContainText(locale === 'zh-CN' ? '候选（未发布）' : 'Candidate (unpublished)');
        await expect(notice).toContainText(locale === 'zh-CN' ? 'Public Demo 仅通过本地容器验证' : 'Public Demo has local container verification only');
        await notice.getByRole('button', {name: locale === 'zh-CN' ? '已阅读' : 'Mark as read', exact: true}).click();
        await page.reload();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await page.getByRole('button', {name: locale === 'zh-CN' ? /版本历史/ : /Version history/}).click();
        await page.getByRole('button', {name: locale === 'zh-CN' ? /1\.0\.0 · 已发布/ : /1\.0\.0 · Released/}).click();
        await expect(page.getByRole('link', {name: 'https://github.com/ling5477/nexus-quant/releases/tag/v1.0.0'})).toBeVisible();
        await expect(page.getByText('2026-10-20', {exact: true})).toBeVisible();
        await expect(page.getByRole('button', {name: /^(确认并生成更新请求|Confirm update)$/})).toHaveCount(0);
        expect(writes).toBe(0);
        await page.screenshot({path: test.info().outputPath(`history-${locale}.png`), fullPage: true, animations: 'disabled'});
        await page.route('**/api/auth/me', (route) => route.fulfill({json: {
            userId: 8, username: 'release-ui', roles: ['ADMIN'], authenticated: true, mustChangePassword: false,
        }}));
        await page.reload();
        await expect(notice).toBeVisible();
        await notice.getByRole('button', {name: locale === 'zh-CN' ? '已阅读' : 'Mark as read', exact: true}).click();
        await page.route('**/api/system/version', (route) => route.fulfill({json: {...runtime, installedPackageVersion: '1.0.0'}}));
        await page.reload();
        await expect(page.getByRole('dialog', {name: `${locale === 'zh-CN' ? '本次更新' : 'What’s New'} · 1.0.0`})).toBeVisible();
    });
}

test('未知安装及读取失败不显示升级后提示', async ({page}) => {
    await fixture(page, 'zh-CN', {...runtime, packageIdentityStatus: 'UNAVAILABLE', installedPackageVersion: null});
    await page.goto('/settings?section=version');
    await expect(page.getByText('当前服务没有安装器包身份，不能据此判定产品包版本或有无更新。')).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.route('**/api/system/version', (route) => route.fulfill({status: 503, json: {code: 'SOURCE_UNAVAILABLE'}}));
    await page.reload();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByText('当前版本信息读取失败')).toBeVisible();
});

test('阅读记录写入失败有明确反馈且允许本次关闭', async ({page}) => {
    await fixture(page);
    await page.addInitScript(() => {
        const original = Storage.prototype.setItem;
        Storage.prototype.setItem = function(key, value) {
            if (key.startsWith('nq-whats-new-v1:')) throw new DOMException('fixture blocked storage');
            return original.call(this, key, value);
        };
    });
    await page.goto('/settings?section=version');
    const notice = page.getByRole('dialog');
    await notice.getByRole('button', {name: '已阅读', exact: true}).click();
    await expect(notice).toContainText('浏览器无法保存阅读记录，下次登录可能再次提示。');
    await notice.getByRole('button', {name: '本次关闭', exact: true}).last().click();
    await expect(notice).toHaveCount(0);
    await page.reload();
    await expect(notice).toBeVisible();
});

test('可信检查回执后展示更新内容、取消不生成更新、确认仍交给宿主且失败不报成功', async ({page}) => {
    await fixture(page, 'zh-CN', {...runtime, installedPackageVersion: '1.0.0'});
    await page.goto('/settings?section=version');
    await page.getByRole('dialog').getByRole('button', {name: '已阅读', exact: true}).click();
    const manifest = Object.entries({
        FORMAT: '1', VERSION: '1.0.1', SOURCE_SHA: 'd'.repeat(40), RELEASE_SOURCE_HASH: 'e'.repeat(64),
        SCHEMA_VERSION: '47', ARCH: 'amd64', ARCHIVE: 'images-amd64.tar', ARCHIVE_SHA256: 'f'.repeat(64),
        BACKEND_IMAGE: 'sha256:'+'1'.repeat(64), FRONTEND_IMAGE: 'sha256:'+'2'.repeat(64), POSTGRES_IMAGE: 'sha256:'+'3'.repeat(64),
        BACKEND_CONFIG_DIGEST: 'sha256:'+'4'.repeat(64), FRONTEND_CONFIG_DIGEST: 'sha256:'+'5'.repeat(64), POSTGRES_CONFIG_DIGEST: 'sha256:'+'6'.repeat(64),
        COMPOSE_SHA256: '7'.repeat(64), RUNTIME_SHA256: '8'.repeat(64), PS_INSTALLER_SHA256: '9'.repeat(64), SH_INSTALLER_SHA256: '0'.repeat(64),
    }).map(([k,v]) => `${k}=${v}`).join('\n')+'\n';

    await page.getByRole('textbox', {name: /目标安装环境/}).fill('nq-ui-fixture');
    await page.getByRole('textbox', {name: /独立可信的目标清单 SHA256/}).fill(createHash('sha256').update(manifest).digest('hex'));
    await page.getByLabel('目标包 package.env', {exact: true}).setInputFiles({name:'package-amd64.env',mimeType:'text/plain',buffer:Buffer.from(manifest)});
    await page.getByRole('button', {name:'生成稳定检查请求', exact:true}).click();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('nq-update-handoff-v1:7'))).not.toBeNull();
    const state = await page.evaluate(() => JSON.parse(localStorage.getItem('nq-update-handoff-v1:7')!));
    const checked = {...state.check, installationId: '1a1a1a1a-1111-4111-8111-111111111111', status: 'CHECKED', updateAvailable: 'true', transactionId: 'NONE', resultCode: 'UPDATE_ELIGIBLE', completedAt: '2026-10-23T01:00:00Z', installedVersion:'1.0.0', installedManifestSha256:'c'.repeat(64),healthStatus:'NOT_CHECKED'};
    await page.getByLabel('导入原安装器回执', {exact:true}).setInputFiles({name:'check.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(checked))});
    await expect(page.getByText('Self-Hosted 与 Public Demo 分离发行；Demo 注册、邮箱验证和租户初始化。')).toBeVisible();
    await page.getByRole('button', {name:'确认并生成更新请求', exact:true}).click();
    await page.getByRole('dialog').getByRole('button', {name:/取\s*消/}).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(await page.evaluate(() => JSON.parse(localStorage.getItem('nq-update-handoff-v1:7')!).update)).toBeUndefined();
    await page.getByRole('button', {name:'确认并生成更新请求', exact:true}).click();
    await page.getByRole('dialog').getByRole('button', {name:'确认并生成更新请求', exact:true}).click();
    await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('nq-update-handoff-v1:7')!).update?.action)).toBe('UPDATE');
    const confirmed = await page.evaluate(() => JSON.parse(localStorage.getItem('nq-update-handoff-v1:7')!));
    expect(confirmed.update.action).toBe('UPDATE');
    const failed = {...confirmed.update, status:'FAILED', updateAvailable:'true', transactionId:'20261023T010000000Z-1234abcd', resultCode:'UPDATE_FAILED_ROLLED_BACK', completedAt:'2026-10-23T01:01:00Z', installedVersion:'1.0.0',installedManifestSha256:'c'.repeat(64),healthStatus:'VERIFIED'};
    await page.getByLabel('导入原安装器回执', {exact:true}).setInputFiles({name:'failed.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(failed))});
    await expect(page.getByText('UPDATE_FAILED_ROLLED_BACK', {exact:false})).toBeVisible();
    await expect(page.getByText('执行回执、当前运行版本与健康一致')).toHaveCount(0);
    await expect(page.getByText('nexusquant.ps1 -Action update', {exact:false})).toBeVisible();
});
