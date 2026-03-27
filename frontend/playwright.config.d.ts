/**
 * Playwright 只负责启动前端并验证关键导航。
 * Why:
 * 后端仍由现有 Spring Boot 应用提供，冒烟用例通过真实登录接口确认
 * token、guard 和菜单跳转链路没有断裂。
 */
declare const _default: import("playwright/test").PlaywrightTestConfig<{}, {}>;
export default _default;
