export interface InstrumentCatalogItem {
    instrumentId: number;
    exchangeCode: string;
    instrumentType: string;
    exchangeSymbol: string;
    internalSymbol: string;
    baseAsset: string;
    quoteAsset: string;
    status: string;
    tickSize: number | null;
    stepSize: number | null;
    minQuantity: number | null;
    source: string;
    syncedAt: string;
    createdAt: string;
    updatedAt: string;
}

export interface InstrumentCatalogSyncResponse {
    exchangeCodes: string[];
    rowsRead: number;
    rowsInserted: number;
    rowsUpdated: number;
    startedAt: string;
    finishedAt: string;
}
