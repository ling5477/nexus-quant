import {expect, test} from 'playwright/test';

test.describe('login page smoke', () => {
    test('渲染专业控制台入口并保持凭证输入为空', async ({page}) => {
        await page.goto('/login');

        // Why: 登录页是未认证用户可见入口，smoke 只验证展示与表单安全边界，
        // 不伪造登录态、不绕过鉴权、不依赖后端返回。
        await expect(page.getByRole('heading', {name: 'NexusQuant'})).toBeVisible();
        await expect(page.getByText('Quant Trading Infrastructure Console')).toBeVisible();
        const workflows = page.getByRole('navigation', {name: 'NexusQuant console workflows'});
        await expect(workflows.getByText('Strategy Research', {exact: true})).toBeVisible();
        await expect(workflows.getByText('Backtest', {exact: true})).toBeVisible();
        await expect(workflows.getByText('Paper Trading', {exact: true})).toBeVisible();
        await expect(workflows.getByText('Risk Control', {exact: true})).toBeVisible();
        await expect(workflows.getByText('Audit Trail', {exact: true})).toBeVisible();

        const posture = page.getByLabel('System posture');
        await expect(posture.getByText('GateJ completed', {exact: true})).toBeVisible();
        await expect(posture.getByText('Next: GateK-PLAN', {exact: true})).toBeVisible();
        await expect(posture.getByText('LIVE DISABLED', {exact: true})).toBeVisible();

        const signIn = page.getByLabel('Console sign in');
        await expect(signIn.getByText('Sign in to Console', {exact: true})).toBeVisible();
        await expect(signIn.getByText('DEV / PAPER / LOCAL controlled access', {exact: true})).toBeVisible();

        await expect(signIn.getByLabel('Username')).toHaveValue('');
        await expect(signIn.getByLabel('Password')).toHaveValue('');
        await expect(signIn.getByText('This console does not enable LIVE trading by default.')).toBeVisible();
    });
});
