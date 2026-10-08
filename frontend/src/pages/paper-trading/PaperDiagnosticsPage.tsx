import {PaperAnalysisBoundary} from './components/PaperAnalysisBoundary';

/** 历史诊断未读取 canonical 事实，不能据此归因为无订单或无成交。 */
export function PaperDiagnosticsPage() {
    return <PaperAnalysisBoundary kind="diagnostics"/>;
}
