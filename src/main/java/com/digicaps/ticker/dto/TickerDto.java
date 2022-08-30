package com.digicaps.ticker.dto;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection="ticker")
public class TickerDto {

    private int tk_id;
    private String username;
    private int age;
    private String hobby;
}
