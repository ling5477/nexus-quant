package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.port.PublicMarketSnapshotReadPort;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 现有认证读取范围内的公开市场事实，仅供行情展示。 */
@RestController
public class PublicMarketSnapshotController {
    private final PublicMarketSnapshotReadPort snapshots;

    public PublicMarketSnapshotController(PublicMarketSnapshotReadPort snapshots) {
        this.snapshots = snapshots;
    }

    @GetMapping("/api/marketdata/public-snapshot")
    public ResponseEntity<PublicMarketSnapshot> read(
            @RequestParam String exchangeCode, @RequestParam String marketType,
            @RequestParam String symbol, @RequestParam String interval) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(snapshots.read(exchangeCode, marketType, symbol, interval));
        } catch (PublicMarketSnapshotReadPort.UnsupportedSelection ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PUBLIC_MARKET_SNAPSHOT_UNSUPPORTED");
        }
    }
}
