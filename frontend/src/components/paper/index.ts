/**
 * Paper Trading 控制台面板组件出口。
 *
 * 这些面板自包含既有 paper-trading hooks（复用 React Query 缓存，不重复请求），
 * 复用 NQ Design System v1 组件渲染，专供 Paper Trading 运行控制台使用。
 */
export {NqAlertPanel} from '@/components/paper/NqAlertPanel';
export {NqRecoveryPanel} from '@/components/paper/NqRecoveryPanel';
export {NqHeartbeatPanel} from '@/components/paper/NqHeartbeatPanel';
export {NqScheduleFirePanel} from '@/components/paper/NqScheduleFirePanel';
export {NqStabilityCheckPanel} from '@/components/paper/NqStabilityCheckPanel';
