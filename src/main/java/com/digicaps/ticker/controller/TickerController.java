package com.digicaps.ticker.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.digicaps.ticker.vo.PollingVo;
import com.digicaps.ticker.vo.PushVo;
import com.google.gson.JsonObject;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class TickerController 
{
	@Value("${ticker.input.dir}")
	private String FILE_LOAD_DIR; //파일조회 디렉토리
	@Value("${ticker.output.dir}")
	private String FILE_SAVE_DIR; //파일저장 디렉토리
	@Value("${ticker.input.charset}")
	private String FILE_READ_CHARSET; //파일조회 인코딩
	@Value("${api.baseUrl}")
	private String BASE_URL;
	@Value("${api.pushUrl}")
	private String PUSH_URL;
	@Value("${api.pollingUrl}")
	private String POLLING_URL;
	@Value("${api.pollingRetryUrl}")
	private String POLLING_RETRY_URL;
	@Value("${api.retry.delayTime}")
	private int retryDelayTime;

	private WatchKey watchKey;

	@PostConstruct
	public void watchInit() throws IOException
	{
		WatchService watchService = FileSystems.getDefault().newWatchService(); //watchService 생성
		Path path = Paths.get(FILE_LOAD_DIR); //경로 생성
		log.info("fileLoad Directroy = {}, fileSave Directroy = {}", FILE_LOAD_DIR, FILE_SAVE_DIR);

		//해당 디렉토리 경로에 와치서비스와 이벤트 등록 (프로퍼티로 이벤트 등록)
		path.register(watchService,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.OVERFLOW);

		Thread thread = new Thread(()-> {
			while(true)
			{
				log.info("================= watchService Thread START =================");
				try {
					watchKey = watchService.take();//이벤트 대기(Blocking)
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				List<WatchEvent<?>> events = watchKey.pollEvents(); //이벤트들을 가져옴
				for(WatchEvent<?> event : events)
				{
					WatchEvent.Kind<?> kind = event.kind(); //이벤트 종류
					Path paths = (Path)event.context();	//경로

					if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						log.info("created something in directory");

						/*
						 * 재난자막 파일처리
						 */
						try 
						{
							String[] resultArr = fnFileRead(paths.getFileName().toString()); //재난자막 파일조회
							String pushResult = fnTickerPush(resultArr[14], resultArr[9], resultArr[3]); //[API]재난자막 push(식별자/메시지/반복횟수)
							PollingVo pollingResult = null;
							if ("0000".equals(pushResult)) {
								log.info("[Push success] pushResult= {}", pushResult);
								while(true)
								{
									pollingResult = fnTickerPolling(resultArr[14]); //[API]재난자막 송출확인
									if (pollingResult != null && "0000".equals(pollingResult.getResultCode())) {
										log.info("[Polling success]");
										fnFileSave(this, resultArr, pollingResult.broadcastDT, pollingResult.broadcastET); //송출결과 파일저장
										break;
									}
									Thread.sleep(retryDelayTime);
								}
							}else {
								log.info("[Push fail] pushResult = {}", pushResult);
							}

//							if (pollingResult != null && "0000".equals(pollingResult.getResultCode())) {
//								log.info("Polling success");
//								fnFileSave(resultArr, pollingResult.broadcastDT, pollingResult.broadcastET); //송출결과 파일저장
//							}else if(pollingResult != null && "2001".equals(pollingResult.getResultCode())) {
//								// retry
//								log.info("== Polling retry..");
//								for (int i = 0; i < retryCnt; i++) {
//									log.info("Polling retryCnt = {}", i+1);
//									pollingResult = fnTickerPolling(resultArr[14]); //[API]재난자막 송출확인
//									if ("0000".equals(pollingResult.getResultCode())) {
//										log.info("Polling success");
//										fnFileSave(resultArr, pollingResult.broadcastDT, pollingResult.broadcastET); //송출결과 파일저장
//										break;
//									}
//								}
//							}else {}
						}catch (IOException e) {
							log.error(e.getMessage());
							e.printStackTrace();
						}catch (InterruptedException e) {
							log.error(e.getMessage());
							e.printStackTrace();
						}

					}else if(kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
						log.info("delete something in directory");
					}else if(kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
						log.info("modified something in directory");
					}else if(kind.equals(StandardWatchEventKinds.OVERFLOW)) {
						log.info("overflow");
					}
				} // end for

				// reset
				if(!watchKey.reset()) {
					try {
						watchService.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				log.info("================= watchService Thread END =================");

			} // end while
		}); // end thread
		
		thread.start();
	}

	/**
	 * 파일 읽기
	 */
	public String[] fnFileRead(String fileName) throws IOException
	{
		log.info("===== fnFileRead() =====");
		File file = new File(FILE_LOAD_DIR, fileName);
		log.info("filePath = {}, fileName = {}", file.getPath(), file.getName());

		FileInputStream input = new FileInputStream(file); //파일 입력스트림 생성
		InputStreamReader reader = new InputStreamReader(input, FILE_READ_CHARSET);
		BufferedReader bufferedReader = new BufferedReader(reader); // 입력 버퍼 생성

		String line;
		StringBuffer buf = new StringBuffer();
		int lineCnt = 0;
		while( (line = bufferedReader.readLine()) != null )
		{
			if(lineCnt == 0) {
				int index = line.indexOf("$");
				log.info("index = {}", index);
				String tempLine = line.substring(index, line.length() - index);
				buf.append(tempLine);
			}else {
				buf.append(line);
			}
			++lineCnt;
		}

		String allStr = buf.toString().replaceAll("\r\n", "");
		log.info("allStr = {}", allStr);
		String[] cutStrArr = allStr.split("\\^"); //[14]:식별자, [9]:메시지내용, [3]반복횟수
		log.info("cutStrArr.length = {}", cutStrArr.length);
		printArray(cutStrArr);
		log.info("identifier = {}, message = {}, repeatCount = {}", cutStrArr[14], cutStrArr[9], cutStrArr[3]);
		
		bufferedReader.close();

		return cutStrArr;
	}

	/**
	 * 파일 저장
	 */
	public static void fnFileSave(TickerController tickerController, String[] resultArr, String broadcastDT, String broadcastET) throws IOException
	{
		log.info("===== fnFileSave() =====");
		if (resultArr == null) return;
		String identifier = resultArr[14];

		JsonObject jsonObject = new JsonObject();
		BufferedWriter bufferedWriter = null;
		log.info("identifier = {}, broadcastDT = {}, broadcastET = {}", identifier, broadcastDT, broadcastET);
		
		// JSON 생성
		jsonObject.addProperty("identifier", identifier); //식별자
		jsonObject.addProperty("broadcastDT", broadcastDT); //송출시작시각
		jsonObject.addProperty("broadcastET", broadcastET); //송출종료시각
		
		try
		{
			// 신규 파일 생성 (파일명규칙 ==> RSLT_식별자.json)
			File newFile = new File(tickerController.FILE_SAVE_DIR, "RSLT_"+ identifier +".json");
			log.debug("fileName = {}", newFile.getName());

			// JSON (성공)
			jsonObject.addProperty("ResultCode", "success");
			jsonObject.addProperty("ErrorMsg", "");

			FileOutputStream fileWriter = new FileOutputStream(newFile, false); //파일 출력스트림 생성
			OutputStreamWriter writer = new OutputStreamWriter(fileWriter, StandardCharsets.UTF_8);
			bufferedWriter = new BufferedWriter(writer); //출력 버퍼 생성
			bufferedWriter.write(jsonObject.toString()); //저장
		}
		catch (Exception e)
		{
			// JSON (에러)
			jsonObject.addProperty("ResultCode", "fail");
			jsonObject.addProperty("ErrorMsg", e.getMessage());

			e.printStackTrace();
		}
		finally
		{
			if (bufferedWriter != null) {
				bufferedWriter.flush();
				bufferedWriter.close();
			}
		}
	}

	/**
	 * 재난자막정보 Push 
	 */
	@SneakyThrows
	public String fnTickerPush(String SubIdenti, String SubRepeti, String SubText) throws RuntimeException
	{
		log.info("===== fnTickerPush() =====");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("SubIdenti", SubIdenti);
		params.put("SubRepeti", SubRepeti);
		params.put("SubText", SubText);
		log.info("params.toString() = {}", params.toString());

		WebClient client = WebClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultUriVariables(params)
				.build();

		Mono<ResponseEntity<PushVo>> responseEntityMono = client.post()
				.uri(PUSH_URL)
				.retrieve()
				.toEntity(PushVo.class);

		log.info("responseEntityMono = {}", responseEntityMono);

		return "0000";
	}

	/**
	 * 재난자막정보 송출 확인
	 */
	public PollingVo fnTickerPolling(String identifier)
	{
		log.info("===== fnTickerPolling() =====");
		Map<String, Object> params = new HashMap<>();
		params.put("identifier", identifier);
		
		WebClient client = WebClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultUriVariables(params)
				.build();
		
		ResponseEntity<PollingVo> response = client.get()
				.uri(POLLING_URL + identifier)
//				.uri(POLLING_RETRY_URL + identifier)
				.retrieve()
				.toEntity(PollingVo.class)
				.block();
		log.info("response = {}", response.getBody().toString());
		
		return response.getBody();
	}
		
	public void printArray(String[] array) {
		if (array == null)
			return;

		for(int i = 0; i < array.length; i++) {
			log.info(" str[{}] = {}", i, array[i]);
		}
	}
}
