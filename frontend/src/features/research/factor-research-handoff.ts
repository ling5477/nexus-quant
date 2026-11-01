export interface FactorResearchHandoff {
    datasetId: string;
    datasetVersionId: string;
    inputName: string;
    directory: string;
    createdAt: string | null;
    outputName: string | null;
    command: string | null;
}
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const utc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;
const record = (value: unknown): Record<string, unknown> =>
    value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};

/** 只从本次原导出读取交接身份；不重算因子、认证统计或用当前时间替代未知冻结时点。 */
export async function factorResearchHandoff(blob: Blob, selectedId: string): Promise<FactorResearchHandoff> {
    if (blob.size > 8 * 1024 * 1024) throw new Error('FACTOR_INPUT_IDENTITY_INVALID');
    const input = record(JSON.parse(await blob.text()));
    const dataset = record(input.dataset), frozen = record(dataset.frozenInput);
    if (input.schemaVersion !== 'frozen-research-export.v1' || dataset.datasetId !== selectedId || !uuid.test(selectedId)
        || frozen.version !== 'HISTORICAL_RESEARCH_INPUT_V2' || typeof frozen.datasetVersionId !== 'string'
        || !uuid.test(frozen.datasetVersionId)) throw new Error('FACTOR_INPUT_IDENTITY_INVALID');
    const identity = `${selectedId.toLowerCase()}-${frozen.datasetVersionId.toLowerCase()}`;
    // Version UUID全局唯一；目录不再重复两份UUID，避免Windows常见解压路径超过长度限制。
    const inputName = `frozen-input-${identity}.json`, directory = `research-${frozen.datasetVersionId.toLowerCase()}`;
    const createdAt = typeof frozen.frozenAt === 'string' && utc.test(frozen.frozenAt) && Number.isFinite(Date.parse(frozen.frozenAt))
        && new Date(frozen.frozenAt).toISOString().slice(0, 19) === frozen.frozenAt.slice(0, 19)
        ? frozen.frozenAt : null;
    const outputName = createdAt ? `factor-w24-h6-s42-b200-${createdAt.replace(/[^0-9TZ]/g, '')}.json` : null;
    const output = `.\\${directory}\\${outputName}`;
    // 已有CLI允许覆盖文件；交接命令先拒绝同名输出，不能静默覆盖另一研究记录。
    const command = createdAt && outputName ? `if (Test-Path -LiteralPath "${output}") { throw "OUTPUT_ALREADY_EXISTS" } else {\n`
        + `.\\.venv\\Scripts\\python.exe -m nq_research --frozen-input ".\\${directory}\\${inputName}"`
        + ` --factor-output "${output}" --factor-window 24 --horizon-bars 6 --seed 42 --bootstrap-repetitions 200 --created-at "${createdAt}"\n}` : null;
    return {datasetId: selectedId, datasetVersionId: frozen.datasetVersionId, inputName, directory, createdAt, outputName, command};
}
