package com.digicaps.ticker.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class RestApiController {

	@Value("${ticker.broadcast.date.format}")
	private String DATE_FORMAT; //날짜포맷 (yyyyMMddHHmmss)

	@GetMapping("/")
	public void logGet(){
		log.trace("trace message");
		log.debug("debug message");
		log.info("info message");
		log.warn("warn message");
		log.error("error message");
	}

	@PostMapping("/pcms/push")
	public Map<String, Object> tickerPush()
	{
		log.info("===== [TEST]tickerPush");
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("ResultCode", "0000");
		
		return resultMap;
	}
	
	@GetMapping("/pcms/status/{identifier}")
	public Map<String, Object> tickerPolling(@PathVariable("identifier") String identifier)
	{
		log.info("===== [TEST]tickerPolling");
		log.info("pathVariable identifier = {}", identifier);
		
		LocalDateTime nowDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		LocalDateTime nowDateTimeTo5miniteAdd = nowDateTime.plusMinutes(5L);
		String startDt = nowDateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
		String endDt = nowDateTimeTo5miniteAdd.format(DateTimeFormatter.ofPattern(DATE_FORMAT)); //startDt + 5분(임시)
		
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("identifier", identifier);
		resultMap.put("ResultCode", "0000");
		resultMap.put("broadcastDT", startDt);
		resultMap.put("broadcastET", endDt);
		resultMap.put("ErrorMsg", "");
		
		return resultMap;
	}
	
	@GetMapping("/pcms/statusretry/{identifier}")
	public Map<String, Object> tickerPollingRetry(@PathVariable("identifier") String identifier)
	{
		log.info("===== [TEST]tickerPollingRetry");
		log.info("pathVariable identifier = {}", identifier);
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("ResultCode", "2001");
		
		return resultMap;
	}
	
	@GetMapping("/delay")
	public String delay(@RequestParam int seconds) throws InterruptedException
	{
		log.info("delaying " + seconds +"seconds on " + Thread.currentThread());
		Thread.sleep(seconds * 1000L);
		return "delayed " + seconds + " seconds";
	}


	@GetMapping("/ex")
	public void logError(){
		try {
			throw new RuntimeException();
		}catch (Exception e) {
			log.error("templates/error", e);
		}
	}
}
