package com.digicaps.ticker.controller;

import com.digicaps.ticker.service.TickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TickerController {

    private final TickerService tickerService;

    @GetMapping("api/ticker/find/{title}")
    public String findById(@PathVariable String title)
    {
        log.info("test");
        tickerService.getTickerList("test2");

        return null;
    }
}
