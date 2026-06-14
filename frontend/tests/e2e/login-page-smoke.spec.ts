import {expect, test} from 'playwright/test';

test.describe('login page smoke', () => {
    test('渲染 v2 双区登录入口并保持凭证输入为空', async ({page}) => {
        await page.goto('/login');

        // Why: 登录页是未认证用户可见入口,smoke 只验证展示与表单安全边界,
        // 不伪造登录态、不绕过鉴权、不依赖后端返回。

        // 左区叙事:产品名 + 定位 + 能力。
        await expect(page.getByRole('heading', {name: 'NexusQuant'})).toBeVisible();
        await expect(page.getByText('量化交易基础设施控制台')).toBeVisible();

        const capabilities = page.getByRole('list', {name: 'NexusQuant 能力'});
        await expect(capabilities.getByText('策略研究与回测', {exact: true})).toBeVisible();
        await expect(capabilities.getByText('模拟交易(Paper)', {exact: true})).toBeVisible();
        await expect(capabilities.getByText('风控前置拦截', {exact: true})).toBeVisible();
        await expect(capabilities.getByText('全链路审计追踪', {exact: true})).toBeVisible();

        // 右区认证卡片:标题 + 空凭证 + 安全边界提示。
        const signIn = page.getByRole('region', {name: '登录控制台'});
        await expect(signIn.getByText('登录控制台', {exact: true})).toBeVisible();
        await expect(signIn.getByLabel('账号')).toHaveValue('');
        await expect(signIn.getByLabel('密码')).toHaveValue('');
        await expect(signIn.getByText('本控制台默认不启用 LIVE 交易')).toBeVisible();

        // 主视觉不得再出现 Gate 名称 / 里程碑 / DEV/PAPER/LOCAL 交付语义(B0.1 要求)。
        await expect(page.getByText('GateJ completed')).toHaveCount(0);
        await expect(page.getByText('Next: GateK-PLAN')).toHaveCount(0);
        await expect(page.getByText('DEV / PAPER / LOCAL controlled access')).toHaveCount(0);
    });
});
