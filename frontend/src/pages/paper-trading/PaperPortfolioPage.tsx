import {PaperAnalysisWorkspace} from './components/PaperAnalysisWorkspace';

/** 独立SIM按单实例查看；历史样本组合单独解释，不合并为顶部账户或可用资金。 */
export function PaperPortfolioPage() {
    return <PaperAnalysisWorkspace kind="portfolio" />;
}
