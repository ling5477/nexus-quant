import type {ReactNode} from 'react';

import './ExceptionView.css';

export type ExceptionTone = 'neutral' | 'info' | 'warning' | 'danger';

const TONE_VAR: Record<ExceptionTone, string> = {
    neutral: 'var(--nq-text-tertiary)',
    info: 'var(--nq-info)',
    warning: 'var(--nq-warning)',
    danger: 'var(--nq-danger)',
};

export interface ExceptionMetaItem {
    label: string;
    /** 值;request id / 时间戳等标识用 mono 渲染由调用方控制。 */
    value: ReactNode;
}

export interface ExceptionViewProps {
    tone: ExceptionTone;
    /** 顶部可选的状态码,例如 404 / 403 / 500。 */
    code?: string;
    /** 简短代号标签(例如 会话已过期 / 无访问权限),给状态点旁的小字。 */
    kicker: string;
    title: string;
    /** 发生了什么(中性陈述,不卖惨、不道歉)。 */
    description: ReactNode;
    /** 下一步怎么做(可包含列表)。 */
    nextSteps?: ReactNode;
    /** 事实区:request id、时间、缺失角色等。 */
    meta?: ExceptionMetaItem[];
    /** 操作区:返回入口 / 重试 / 重新登录等按钮。 */
    actions: ReactNode;
}

/**
 * ExceptionView — 登录之外四类异常页 + 404 的统一表现层(v2)。
 *
 * 职责:用一致的居中卡片承载"发生了什么 + 怎么解决 + 关键事实 + 入口",
 * 替代 AntD 默认 403/404 模板。颜色只读 var(--nq-*),不私配 hex。
 * 关键约束:纯表现组件,不读路由 / 不发请求 / 不改鉴权;具体文案与动作由各异常页注入。
 */
export function ExceptionView({tone, code, kicker, title, description, nextSteps, meta, actions}: ExceptionViewProps) {
    const color = TONE_VAR[tone];

    return (
        <section className="nq-exception" role="alert" aria-label={title}>
            <div className="nq-exception__head">
                <span className="nq-exception__dot" style={{background: color}} aria-hidden="true"/>
                <span className="nq-exception__kicker" style={{color}}>
                    {kicker}
                </span>
                {code && (
                    <span className="nq-exception__code nq-num" aria-hidden="true">
                        {code}
                    </span>
                )}
            </div>

            <h1 className="nq-exception__title">{title}</h1>
            <div className="nq-exception__desc">{description}</div>

            {nextSteps && <div className="nq-exception__next">{nextSteps}</div>}

            {meta && meta.length > 0 && (
                <dl className="nq-exception__meta">
                    {meta.map((item) => (
                        <div className="nq-exception__meta-row" key={item.label}>
                            <dt className="nq-exception__meta-label">{item.label}</dt>
                            <dd className="nq-exception__meta-value nq-num">{item.value}</dd>
                        </div>
                    ))}
                </dl>
            )}

            <div className="nq-exception__actions">{actions}</div>
        </section>
    );
}
