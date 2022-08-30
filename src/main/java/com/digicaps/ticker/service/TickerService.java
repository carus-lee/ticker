package com.digicaps.ticker.service;

import com.digicaps.ticker.dto.TickerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class TickerService {
    private final MongoTemplate mongoTemplate;
    private final TickerDto tickerDto = new TickerDto();

    public List<String> getTickerList(String collection){
        log.info("TickerService");
        log.info("ticker = {}", mongoTemplate.getCollection(collection).find());

        return null;
    }
}
