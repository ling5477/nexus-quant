import {apiClient} from '@/api/client';
import {parseLegacyPaperAnalysis, type LegacyPaperAnalysis} from '../legacy-analysis';

/** 仅请求正式历史SIM快照；账户与私有费用范围由服务端认证身份决定，不接受页面传owner。 */
export async function readLegacyPaperAnalysis(signal?: AbortSignal): Promise<LegacyPaperAnalysis> {
    const {data} = await apiClient.get<string>('/paper-trading/legacy-analysis', {
        signal,
        transformResponse: [
            (text: unknown, _headers, status) => {
                // 成功数据在HTTP层之外校验，保留原金额词法值；错误体不能被业务结构校验误报成会话取消。
                if (
                    typeof text !== 'string' ||
                    (status !== undefined && status >= 200 && status < 300)
                )
                    return text;
                try {
                    return JSON.parse(text) as unknown;
                } catch {
                    // 非JSON网关错误仍保留HTTP状态，由统一错误出口解释，不能在转换阶段丢失请求身份。
                    return text;
                }
            },
        ],
    });
    return parseLegacyPaperAnalysis(data);
}
