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
        version: '1.0.2', status: 'CANDIDATE', releaseDate: null, releaseUrl: null,
        features: [
            text('有界历史数据接入、质量检查和冻结输入贯通正式回测。', 'Bounded historical ingestion, quality checks and frozen inputs feed formal backtests.'),
            text('保留 SMA，新增收盘确认的现货通道突破策略。', 'Retain SMA and add a close-confirmed spot channel breakout strategy.'),
            text('持久化策略实验、尝试历史、显式重试、取消和候选比较。', 'Persistent strategy experiments, attempt history, explicit retry, cancellation and comparison.'),
            text('时间顺序 Walk-Forward、独立 FLAT 测试、最终留出和成本压力重算。', 'Temporal Walk-Forward, independent FLAT tests, final holdout and execution-based cost stress.'),
            text('只读关联研究评价、正式发布与 Continuous SIM，保留不可比原因。', 'Read-only research, publication and Continuous SIM comparison with explicit comparability limits.'),
        ],
        improvements: [], bugFixes: [],
        breakingChanges: [text('V47→V49 正向迁移；恢复旧应用必须恢复对应数据库备份。', 'Forward V47 to V49 migration; restoring the old application requires its database backup.')],
        knownIssues: [
            text('候选未发布；Windows 11 amd64 Self-Hosted 正式包安装、更新和恢复尚未验证。', 'Unreleased candidate; Windows 11 amd64 Self-Hosted package installation, upgrade and recovery are not yet qualified.'),
            text('短样本及已观察留出不证明长期稳健性；LIVE、真实交易所写入和自动策略发布关闭。', 'Short samples and previously observed holdout do not prove long-term robustness; LIVE, real exchange writes and automatic publication remain disabled.'),
        ],
    },
    {
        version: '1.0.1', status: 'RELEASED', releaseDate: '2026-10-25',
        releaseUrl: 'https://github.com/ling5477/nexus-quant/releases/tag/v1.0.1',
        features: [
            text('正式发行 Self-Hosted：研究、回测、发布、Continuous SIM 与运行监控。', 'Self-Hosted release: research, backtesting, publication, Continuous SIM and runtime monitoring.'),
            text('独立 Public Demo 开发能力包含注册和平台管理，未包含在正式发行中。', 'Independent Public Demo development includes registration and platform administration and is excluded from this release.'),
        ],
        improvements: [text('租户隔离与任务资源边界；Demo 幂等模拟资金仅属独立开发能力。', 'Tenant isolation and task resource limits; idempotent Demo funding remains an independent development capability.')],
        bugFixes: [
            text('Canonical SIM 旧日报拒绝返回虚假零值，沿用既有 canonical 分析。', 'Legacy daily reports reject false zero values for Canonical SIM and use existing canonical analysis.'),
            text('独立 Demo 开发中修复管理员读取策略、权益快照与运行集成问题。', 'Independent Demo development fixes administrator strategy reads, equity snapshots and runtime integration.'),
        ],
        breakingChanges: [text('升级会正向迁移数据库；恢复旧版本必须使用更新事务对应备份。', 'Upgrade applies forward database migrations; restoring the previous version requires the update transaction backup.')],
        knownIssues: [
            text('正式发行仅含 Self-Hosted；v1.0.0 升级需独立可信实例授权的 Legacy Upgrade Bridge，普通 update 不适用。', 'The release includes Self-Hosted only; v1.0.0 upgrades require the independently authorized Legacy Upgrade Bridge, not ordinary update.'),
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
