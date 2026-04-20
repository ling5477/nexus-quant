export interface MarketdataBar {
    symbol: string;
    interval: string;
    openTime: string;
    closeTime: string;
    openPrice: number;
    highPrice: number;
    lowPrice: number;
    closePrice: number;
    volume: number;
}

export interface MarketdataBarsQuery {
    exchangeCode: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
}
