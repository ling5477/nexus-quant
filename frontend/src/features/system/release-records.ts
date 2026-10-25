import type {RuntimeVersionSnapshot} from './api/version';

type Localized = { 'zh-CN': string; 'en-US': string };
export interface ReleaseRecord {
    version: string;
    status: 'RELEASED' | 'CANDIDATE';
    releaseDate: string | null;
    features: Localized[];
    improvements: Localized[];
    bugFixes: Localized[];
    breakingChanges: Localized[];
    knownIssues: Localized[];
    releaseUrl: string | null;
}
const text = (zh: string, en: string): Localized => ({'zh-CN': zh, 'en-US': en});

/** 说明只描述产品变化，不参与候选发现、可信校验或安装授权。 */
export const releaseRecords: ReleaseRecord[] = [
    {
        version: '1.0.1', status: 'CANDIDATE', releaseDate: null, releaseUrl: null,
        features: [
            text('Self-Hosted 与 Public Demo 分离发行；Demo 注册、邮箱验证和租户初始化。', 'Separate Self-Hosted and Public Demo distributions; Demo registration, email verification and tenant initialization.'),
            text('平台管理员可只读查看租户绩效、禁用或重新启用用户并撤销会话。', 'Platform administrators can read tenant performance, disable or enable users and revoke sessions.'),
        ],
        improvements: [text('租户隔离、10,000 USDT Demo 模拟资金幂等发放和任务资源边界。', 'Tenant isolation, idempotent 10,000 USDT Demo simulation funding and task resource limits.')],
        bugFixes: [
            text('Canonical SIM 旧日报拒绝返回虚假零值，沿用既有 canonical 分析。', 'Legacy daily reports reject false zero values for Canonical SIM and use existing canonical analysis.'),
            text('修复 Demo 管理员读取策略、权益快照与双发行物运行集成问题。', 'Fix Demo administrator read policies, equity snapshots and dual-distribution runtime integration.'),
        ],
        breakingChanges: [text('升级会正向迁移数据库；恢复旧版本必须使用更新事务对应备份。', 'Upgrade applies forward database migrations; restoring the previous version requires the update transaction backup.')],
        knownIssues: [
            text('候选尚未发布；Self-Hosted 正式包升级与更新体验仍待候选资格验证。', 'Candidate is unpublished; formal Self-Hosted package upgrade and this update experience still require candidate qualification.'),
            text('Public Demo 仅通过本地容器验证；真实邮件、TLS、容量和行情再展示许可尚未取得公网部署资格。', 'Public Demo has local container verification only; real email, TLS, capacity and market-data redistribution permission are not qualified for public deployment.'),
            text('LIVE 禁用；缺可信权益、手续费或行情事件时点时不补造事实。', 'LIVE is disabled; unavailable equity, fee and market event facts are not fabricated.'),
        ],
    },
    {
        version: '1.0.0', status: 'RELEASED', releaseDate: '2026-10-20',
        releaseUrl: 'https://github.com/ling5477/nexus-quant/releases/tag/v1.0.0',
        features: [text('OKX Spot 公开行情、研究、回测、发布与 Canonical SIM。', 'OKX Spot public market data, research, backtesting, publication and Canonical SIM.')],
        improvements: [text('Docker / PostgreSQL 16 安装器、备份恢复、可信清单检查、显式更新和回滚。', 'Docker / PostgreSQL 16 installers, backup and restore, trusted manifest checks, explicit updates and rollback.')],
        bugFixes: [text('首次改密、认证代际持久化和旧令牌失效保护。', 'Initial password change, durable authentication generations and old-token revocation protections.')],
        breakingChanges: [],
        knownIssues: [text('Windows 11 amd64 隔离安装已验证；其他平台未取得正式资格。LIVE 禁用，外部数据限制保留。', 'Isolated Windows 11 amd64 installation is verified; other platforms are not formally qualified. LIVE is disabled and external data limitations remain.')],
    },
];

export function installedRelease(runtime?: RuntimeVersionSnapshot): ReleaseRecord | undefined {
    if (runtime?.packageIdentityStatus !== 'INSTALLER_DECLARED') return undefined;
    return releaseRecords.find((record) => record.version === runtime.installedPackageVersion);
}

/** 包摘要区分同一浏览器所访问的安装身份；只有真实用户和服务提供的安装版本可建立阅读标记。 */
export function whatsNewKey(userId: number | undefined, runtime?: RuntimeVersionSnapshot): string | null {
    if (!userId || userId <= 0 || !installedRelease(runtime) || !/^[a-f0-9]{64}$/.test(runtime?.packageManifestSha256 ?? '')) return null;
    return `nq-whats-new-v1:${userId}:${runtime!.packageManifestSha256}:${runtime!.installedPackageVersion}`;
}
