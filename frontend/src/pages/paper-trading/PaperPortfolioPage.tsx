import {PaperAnalysisBoundary} from './components/PaperAnalysisBoundary';

/** 组合读模型无法隔离两类运行时，不展示混合权益与错误的零计数。 */
export function PaperPortfolioPage() {
    return <PaperAnalysisBoundary kind="portfolio"/>;
}
