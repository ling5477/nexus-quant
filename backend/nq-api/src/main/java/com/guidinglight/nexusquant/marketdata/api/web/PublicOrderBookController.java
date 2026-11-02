package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.marketdata.application.service.PublicOrderBookService;
import com.guidinglight.nexusquant.marketdata.domain.PublicOrderBookSnapshot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JWT保护的公开现货读取；默认返回未开启，失败或过期不替换成示例数据。 */
@RestController
@RequestMapping("/api/marketdata/depth")
public class PublicOrderBookController {
    private final PublicOrderBookService books;

    public PublicOrderBookController(PublicOrderBookService books) {
        this.books = books;
    }

    @GetMapping
    public PublicOrderBookSnapshot read(
            @RequestParam String exchangeCode,
            @RequestParam String marketType,
            @RequestParam String symbol) {
        if (!"OKX".equals(exchangeCode)
                || !"SPOT".equals(marketType)
                || symbol == null
                || !PublicOrderBookService.SYMBOLS.contains(symbol))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "PUBLIC_ORDER_BOOK_UNSUPPORTED");
        return books.read(exchangeCode, marketType, symbol);
    }
}
