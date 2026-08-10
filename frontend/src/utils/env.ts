export interface AppEnv {
    appTitle: string;
    envLabel: string;
    apiBaseUrl: string;
}

/**
 * 环境变量统一收口，避免页面和请求层散落读取 `import.meta.env`。
 * Why:
 * 前端需要稳定的 base URL、环境标识和应用标题，后续部署才能在不改业务代码的前提下切换环境。
 * production/freeze 构建不能把本地环境标识当作默认可见文案，否则未注入 env 时会把阶段信息带进验收包。
 */
export const appEnv: AppEnv = {
    appTitle: import.meta.env.VITE_APP_TITLE?.trim() || 'NexusQuant Console',
    envLabel: import.meta.env.VITE_APP_ENV_LABEL?.trim() || (import.meta.env.DEV ? 'DEV' : 'PAPER'),
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL?.trim() || '/api',
};
