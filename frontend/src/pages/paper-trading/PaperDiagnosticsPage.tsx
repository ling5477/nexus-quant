import {PaperAnalysisWorkspace} from './components/PaperAnalysisWorkspace';

/** 按明确来源保留独立SIM与历史Paper业务，避免混合账户和事实口径。 */
export function PaperDiagnosticsPage() {
    return <PaperAnalysisWorkspace kind="diagnostics" />;
}
