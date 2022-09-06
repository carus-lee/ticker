package com.digicaps.ticker.controller;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
public class TickerController 
{
	@Value("${ticker.input.dir}")
	private String FILE_LOAD_DIR; //파일조회 디렉토리
	@Value("${ticker.output.dir}")
	private String FILE_SAVE_DIR; //파일저장 디렉토리
	@Value("${ticker.broadcast.date.format}")
	private String DATE_FORMAT; // 날짜포맷 (yyyyMMddHHmmss)
	@Value("${ticker.input.charset}")
	private String FILE_READ_CHARSET;

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

						// 파일처리
						try {
							String[] resultArr = fnFileRead(paths.getFileName().toString());
							fnFileSave(resultArr);
						}catch (IOException e) {
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
		log.info("========== fnFileRead() ==========");
		String[] resultArr = new String[3];
		File file = new File(FILE_LOAD_DIR, fileName);
		log.info("filePath = {}, fileName = {}", file.getPath(), file.getName());

		FileInputStream input = new FileInputStream(file); //파일 입력스트림 생성
		InputStreamReader reader = new InputStreamReader(input, FILE_READ_CHARSET);
		BufferedReader bufferedReader = new BufferedReader(reader); // 입력 버퍼 생성

		String line;
		while( (line = bufferedReader.readLine()) != null )
		{ // 파일 내 문자열을 1줄씩 읽기
			String[] cutStrArr = line.split("\\^");
			log.info("cutStrArr.length = {}", cutStrArr.length);
			printArray(cutStrArr);

			resultArr[0] = cutStrArr[14]; //식별자
			resultArr[1] = cutStrArr[9]; //메시지 내용
			resultArr[2] = cutStrArr[3]; //반복횟수
			log.info("resultArr = {}", resultArr[0]);
		}

		bufferedReader.close();

		// TODO
		// 1.상기정보(식별자/메시지/반복횟수)를 내부API로 전송. (김정현부장님 검토중)
		return resultArr;
	}

	/**
	 * 파일 저장
	 */
	public void fnFileSave(String[] dataArr) throws IOException
	{
		log.info("========== fnFileSave() ==========");
		if (dataArr == null) return;

		JsonObject jsonObject = new JsonObject();
		BufferedWriter bufferedWriter = null;
		LocalDateTime nowDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		String startDt = "";
		String endDt = "";

		try
		{
			// 신규 파일 생성 (파일명규칙 ==> RSLT_식별자.json)
			// dataArr = 0:식별자, 1:메시지 내용, 2:반복횟수
//			File newFile = new File(FILE_SAVE_DIR + "\\" + "RSLT_" + dataArr[0] + ".json");
			File newFile = new File(FILE_SAVE_DIR, "RSLT_"+ dataArr[0] +".json");
			log.debug("fileName = {}", newFile.getName());

			LocalDateTime nowDateTimeTo5miniteAdd = nowDateTime.plusMinutes(5L);
			startDt = nowDateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
			endDt = nowDateTimeTo5miniteAdd.format(DateTimeFormatter.ofPattern(DATE_FORMAT)); //startDt + 5분(임시)
			log.debug("nowDateTime = {}", nowDateTime);
			log.debug("nowDateTimeAdd5minute = {}", nowDateTimeTo5miniteAdd);
			log.info("startDT = {}, endDt = {}", startDt, endDt);

			// JSON 생성
			jsonObject.addProperty("indentifier", dataArr[0]); //식별자
			jsonObject.addProperty("broadcastDT", startDt); //송출시작시각
			jsonObject.addProperty("broadcastET", endDt);   //송출종료시각
			jsonObject.addProperty("ResultCode", "success");
			jsonObject.addProperty("ErrorMsg", "");

			FileOutputStream fileWriter = new FileOutputStream(newFile, false); //파일 출력스트림 생성
			OutputStreamWriter writer = new OutputStreamWriter(fileWriter, StandardCharsets.UTF_8);
			bufferedWriter = new BufferedWriter(writer); //출력 버퍼 생성
			bufferedWriter.write(jsonObject.toString()); //저장

		}
		catch (Exception e)
		{
			// JSON 생성
			jsonObject.addProperty("indentifier", dataArr[0]); //식별자
			jsonObject.addProperty("broadcastDT", startDt); //송출시작시각
			jsonObject.addProperty("broadcastET", endDt);   //송출종료시각
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

	public void printArray(String[] array) {
		if (array == null)
			return;

		for(int i = 0; i < array.length; i++) {
			log.info(" str[{}] = {}", i, array[i]);
		}
	}

	@GetMapping("/test")
	public String test() {
		return "hello";
	}
}
