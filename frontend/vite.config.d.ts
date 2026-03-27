/**
 * Vite 配置统一收口 API base 与本地代理目标。
 * Why:
 * GateG-1 需要让前端开发服务器直接复用现有 `/api/**` 后端接口，
 * 避免页面内散落绝对地址或重新发明第二套联调方式。
 */
declare const _default: import("vite").UserConfigFnObject;
export default _default;
