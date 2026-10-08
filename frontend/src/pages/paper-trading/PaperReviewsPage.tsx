import {PaperAnalysisBoundary} from './components/PaperAnalysisBoundary';

/** 复盘依赖旧诊断与评估，来源未分离前不能生成整改建议或评级。 */
export function PaperReviewsPage() {
    return <PaperAnalysisBoundary kind="reviews"/>;
}
