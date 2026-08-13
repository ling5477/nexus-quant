import {execFile} from 'node:child_process';
import {promisify} from 'node:util';

const execFileAsync = promisify(execFile);

export const POSITIVE_MARKETDATA_FIXTURE_SOURCE = 'E2E_POSITIVE_FIXTURE';
const POSITIVE_MARKETDATA_FIXTURE_JOB_ID = '00000000-0000-4000-8000-000000002001';
const POSITIVE_MARKETDATA_FIXTURE_RUN_ID = '00000000-0000-4000-8000-000000002002';
export const POSITIVE_MARKETDATA_FIXTURE_QUERY = {
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    interval: '1m',
    startTime: '2025-01-01T00:00:00Z',
    endTime: '2025-01-01T00:05:59Z',
    page: 0,
    size: 100,
} as const;

function formatLocalDateTimeInput(isoTime: string): string {
    const date = new Date(isoTime);
    const pad = (value: number) => String(value).padStart(2, '0');

    return [
        `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
        `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`,
    ].join(' ');
}

// Why: Ant Design DatePicker submits local wall-clock values. Deriving the input text from the
// fixed UTC fixture window keeps the browser request aligned with the DB rows across time zones.
export const POSITIVE_MARKETDATA_FIXTURE_INPUT = {
    startTime: formatLocalDateTimeInput(POSITIVE_MARKETDATA_FIXTURE_QUERY.startTime),
    endTime: formatLocalDateTimeInput(POSITIVE_MARKETDATA_FIXTURE_QUERY.endTime),
} as const;

export const POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS = 6;

const fixtureBars = [
    ['2025-01-01T00:00:00Z', '2025-01-01T00:00:59Z', '93500.00000000', '93680.00000000', '93420.00000000', '93610.00000000', '12.50000000', '1170125.00000000', 125],
    ['2025-01-01T00:01:00Z', '2025-01-01T00:01:59Z', '93610.00000000', '93720.00000000', '93540.00000000', '93680.00000000', '10.75000000', '1007010.00000000', 111],
    ['2025-01-01T00:02:00Z', '2025-01-01T00:02:59Z', '93680.00000000', '93840.00000000', '93620.00000000', '93790.00000000', '14.25000000', '1336517.50000000', 143],
    ['2025-01-01T00:03:00Z', '2025-01-01T00:03:59Z', '93790.00000000', '93810.00000000', '93650.00000000', '93720.00000000', '9.80000000', '918456.00000000', 98],
    ['2025-01-01T00:04:00Z', '2025-01-01T00:04:59Z', '93720.00000000', '93920.00000000', '93690.00000000', '93880.00000000', '16.10000000', '1513168.00000000', 162],
    ['2025-01-01T00:05:00Z', '2025-01-01T00:05:59Z', '93880.00000000', '94010.00000000', '93800.00000000', '93950.00000000', '11.30000000', '1061635.00000000', 119],
] as const;

interface PsqlConfig {
    executable: string;
    host: string;
    port: string;
    database: string;
    user: string;
    password: string;
}

function dbConfig(): PsqlConfig {
    return {
        executable: process.env.E2E_PSQL_BIN ?? 'psql',
        host: process.env.E2E_DB_HOST ?? process.env.NQ_DB_HOST ?? '127.0.0.1',
        port: process.env.E2E_DB_PORT ?? process.env.NQ_DB_PORT ?? '5432',
        database: process.env.E2E_DB_NAME ?? process.env.NQ_DB_NAME ?? 'nexus_quant',
        user: process.env.E2E_DB_USER ?? process.env.NQ_DB_USER ?? 'postgres',
        password: process.env.E2E_DB_PASSWORD ?? process.env.NQ_DB_PASSWORD ?? '123456',
    };
}

function fixtureWhereClause(): string {
    return `
        exchange_code = '${POSITIVE_MARKETDATA_FIXTURE_QUERY.exchangeCode}'
        AND market_type = '${POSITIVE_MARKETDATA_FIXTURE_QUERY.marketType}'
        AND symbol = '${POSITIVE_MARKETDATA_FIXTURE_QUERY.symbol}'
        AND "interval" = '${POSITIVE_MARKETDATA_FIXTURE_QUERY.interval}'
        AND source = '${POSITIVE_MARKETDATA_FIXTURE_SOURCE}'
        AND open_time >= TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.startTime}'
        AND close_time <= TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.endTime}'
    `;
}

function fixtureMarkerJson(index: number): string {
    return JSON.stringify({
        task: 'NQ-GATEM-2I-MARKETDATA-POSITIVE-BARS-FIXTURE-SMOKE',
        fake: true,
        source: POSITIVE_MARKETDATA_FIXTURE_SOURCE,
        row: index + 1,
    }).replaceAll("'", "''");
}

function insertRowsSql(): string {
    return fixtureBars.map((bar, index) => {
        const [openTime, closeTime, openPrice, highPrice, lowPrice, closePrice, volume, quoteVolume, tradeCount] = bar;
        return `(
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.exchangeCode}',
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.marketType}',
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.symbol}',
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.interval}',
            TIMESTAMPTZ '${openTime}',
            TIMESTAMPTZ '${closeTime}',
            ${openPrice},
            ${highPrice},
            ${lowPrice},
            ${closePrice},
            ${volume},
            ${quoteVolume},
            ${tradeCount},
            '${POSITIVE_MARKETDATA_FIXTURE_SOURCE}',
            'OK',
            '${fixtureMarkerJson(index)}'::jsonb,
            TIMESTAMPTZ '2025-01-01T00:06:00Z'
        )`;
    }).join(',\n');
}

function parseLastInteger(stdout: string): number {
    const lines = stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    for (let index = lines.length - 1; index >= 0; index -= 1) {
        if (/^\d+$/.test(lines[index])) {
            return Number(lines[index]);
        }
    }
    throw new Error(`psql did not return a numeric fixture count; stdout=${lines.join(' | ')}`);
}

function sanitizeOutput(value: unknown, config: PsqlConfig): string {
    const text = value instanceof Error ? value.message : String(value);
    return text
        .replaceAll(config.password, '[redacted-db-password]')
        .replace(/(password=)[^ \r\n]+/gi, '$1[redacted]');
}

async function runFixtureSql(sql: string): Promise<number> {
    const config = dbConfig();
    try {
        const {stdout} = await execFileAsync(
            config.executable,
            [
                '-X',
                '--quiet',
                '--tuples-only',
                '--no-align',
                '--set',
                'ON_ERROR_STOP=1',
                '--host',
                config.host,
                '--port',
                config.port,
                '--username',
                config.user,
                '--dbname',
                config.database,
                '--command',
                sql,
            ],
            {
                timeout: 30_000,
                env: {
                    ...process.env,
                    PGCONNECT_TIMEOUT: '5',
                    PGPASSWORD: config.password,
                },
            },
        );
        return parseLastInteger(stdout);
    } catch (error) {
        throw new Error(
            `MarketData positive fixture DB helper failed via ${config.executable} `
            + `(${config.host}:${config.port}/${config.database}, user=${config.user}): `
            + sanitizeOutput(error, config),
        );
    }
}

/**
 * Writes the GateM-2I positive bars fixture into local test DB only.
 *
 * Why:
 * The production fixture ingest endpoint only has legacy no-dash symbols. This helper prepares
 * canonical UI-scope `BTC-USDT` fake bars and a newer synthetic successful ingestion fact without
 * adding backend API, migration, provider calls, startup seed, or long-lived local data. The
 * deterministic job/run IDs isolate source health from unrelated E2E failures in the same DB.
 */
export async function preparePositiveMarketdataBarsFixture(): Promise<number> {
    const sql = `
        BEGIN;
        DELETE FROM marketdata_ingestion_runs
        WHERE run_id = '${POSITIVE_MARKETDATA_FIXTURE_RUN_ID}'::uuid;
        DELETE FROM marketdata_ingestion_jobs
        WHERE job_id = '${POSITIVE_MARKETDATA_FIXTURE_JOB_ID}'::uuid;
        DELETE FROM marketdata_bars
        WHERE ${fixtureWhereClause()};
        INSERT INTO marketdata_bars (
            exchange_code,
            market_type,
            symbol,
            "interval",
            open_time,
            close_time,
            open_price,
            high_price,
            low_price,
            close_price,
            volume,
            quote_volume,
            trade_count,
            source,
            quality_status,
            raw_payload_json,
            ingested_at
        ) VALUES
        ${insertRowsSql()};
        INSERT INTO marketdata_ingestion_jobs (
            job_id, exchange_code, market_type, symbol, "interval", start_time, end_time,
            status, source, created_by, created_at, updated_at, request_json
        ) VALUES (
            '${POSITIVE_MARKETDATA_FIXTURE_JOB_ID}'::uuid,
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.exchangeCode}',
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.marketType}',
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.symbol}',
            '${POSITIVE_MARKETDATA_FIXTURE_QUERY.interval}',
            TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.startTime}',
            TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.endTime}',
            'SUCCEEDED',
            '${POSITIVE_MARKETDATA_FIXTURE_SOURCE}',
            'marketdata-positive-bars-fixture-smoke',
            TIMESTAMPTZ '2099-01-01T00:00:00Z',
            TIMESTAMPTZ '2099-01-01T00:00:01Z',
            '{"fake":true,"source":"${POSITIVE_MARKETDATA_FIXTURE_SOURCE}"}'::jsonb
        );
        INSERT INTO marketdata_ingestion_runs (
            run_id, job_id, status, started_at, finished_at, requested_start_time,
            requested_end_time, actual_start_time, actual_end_time, fetched_bars,
            inserted_bars, updated_bars, skipped_bars, raw_summary_json, created_at
        ) VALUES (
            '${POSITIVE_MARKETDATA_FIXTURE_RUN_ID}'::uuid,
            '${POSITIVE_MARKETDATA_FIXTURE_JOB_ID}'::uuid,
            'SUCCEEDED',
            TIMESTAMPTZ '2099-01-01T00:00:00Z',
            TIMESTAMPTZ '2099-01-01T00:00:01Z',
            TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.startTime}',
            TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.endTime}',
            TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.startTime}',
            TIMESTAMPTZ '${POSITIVE_MARKETDATA_FIXTURE_QUERY.endTime}',
            ${POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS},
            ${POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS},
            0,
            0,
            '{"fake":true,"source":"${POSITIVE_MARKETDATA_FIXTURE_SOURCE}"}'::jsonb,
            TIMESTAMPTZ '2099-01-01T00:00:01Z'
        );
        COMMIT;
        SELECT COUNT(*)::int
        FROM marketdata_bars
        WHERE ${fixtureWhereClause()};
    `;
    return runFixtureSql(sql);
}

/**
 * Removes only the GateM-2I fake fixture scope from local test DB.
 *
 * Why:
 * The smoke must leave unrelated local facts untouched. Bar deletion is source/scope/window bounded;
 * ingestion cleanup uses only the two deterministic fixture IDs.
 */
export async function cleanupPositiveMarketdataBarsFixture(): Promise<number> {
    const sql = `
        BEGIN;
        DELETE FROM marketdata_ingestion_runs
        WHERE run_id = '${POSITIVE_MARKETDATA_FIXTURE_RUN_ID}'::uuid;
        DELETE FROM marketdata_ingestion_jobs
        WHERE job_id = '${POSITIVE_MARKETDATA_FIXTURE_JOB_ID}'::uuid;
        DELETE FROM marketdata_bars
        WHERE ${fixtureWhereClause()};
        COMMIT;
        SELECT (
            (SELECT COUNT(*) FROM marketdata_bars WHERE ${fixtureWhereClause()})
            + (SELECT COUNT(*) FROM marketdata_ingestion_jobs
               WHERE job_id = '${POSITIVE_MARKETDATA_FIXTURE_JOB_ID}'::uuid)
            + (SELECT COUNT(*) FROM marketdata_ingestion_runs
               WHERE run_id = '${POSITIVE_MARKETDATA_FIXTURE_RUN_ID}'::uuid)
        )::int;
    `;
    return runFixtureSql(sql);
}
