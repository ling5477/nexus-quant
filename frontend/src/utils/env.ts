export interface AppEnv {
    appTitle: string;
    envLabel: string;
    apiBaseUrl: string;
}

/**
 * 环境变量统一收口，避免页面和请求层散落读取 `import.meta.env`。
 * Why:
 * GateG-1 需要稳定的 base URL、环境标识和应用标题，
 * 后续批次才能在不改业务代码的前提下切换联调环境。
 */
export const appEnv: AppEnv = {
    appTitle: import.meta.env.VITE_APP_TITLE?.trim() || 'NexusQuant Console',
    envLabel: import.meta.env.VITE_APP_ENV_LABEL?.trim() || 'LOCAL',
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL?.trim() || '/api',
};
