package com.digicaps.ticker.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

@Slf4j
@RestController
public class TickerController 
{
	private static final String FILE_LOAD_DIR = "C:\\home\\skylife\\input";
	private static final String FILE_SAVE_DIR = "C:\\home\\skylife\\output";
	private WatchKey watchKey;

	@PostConstruct
	public void watchInit() throws IOException
	{
		//watchService 생성
		WatchService watchService = FileSystems.getDefault().newWatchService();

		log.info("Static Directroy = {}", FILE_LOAD_DIR);
//		log.info("property Directroy = {}", testDir);
		//경로 생성
		Path path = Paths.get(FILE_LOAD_DIR);

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
					log.info("path = {}", paths.toAbsolutePath()); //C:\...\...\test.txt
					
					if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						log.info("created something in directory");

						// 파일처리
						try {
							String[] resultArr = fnFileRead(paths.getFileName().toString());
							fnFileWrite(paths.getFileName().toString(), resultArr);
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
						log.info("watchService.close()");
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
		log.info("========== fnFileCut() ==========");
		String[] resultArr = new String[1000];
		String filePath = FILE_LOAD_DIR + "\\" + fileName; //파일경로
		log.info("filePath = {}", filePath);

		FileInputStream input = new FileInputStream(filePath); //파일 입력스트림 생성
		InputStreamReader reader = new InputStreamReader(input, "euc-kr");
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
		// 1.상기 3개정보를 내부 API로 보내줘야 됨. (김정현부장님 검토중)
		// 2.모든 채널에 재난정보 자막이 나갔다면 "코마"에 결과 전달
		// 3.완료된 정보 파일로 떨궈줘야 됨.

		return resultArr;
	}

	/**
	 * 파일 저장
	 */
	public void fnFileWrite(String fileName, String[] dataArr) throws IOException
	{
		log.info("========== fnFileWrite() ==========");
		if (dataArr == null) return;

		// 신규 파일 생성
		File newFile = new File(FILE_SAVE_DIR + "\\" + "RSLT_" + fileName);
		log.info("fileInfo = {}", newFile.getPath());

		FileOutputStream fileWriter = new FileOutputStream(newFile, false); //파일 출력스트림 생성
		OutputStreamWriter writer = new OutputStreamWriter(fileWriter, StandardCharsets.UTF_8);
		BufferedWriter bufferedWriter = new BufferedWriter(writer); //출력 버퍼 생성

		bufferedWriter.write(dataArr[0]); //저장

		bufferedWriter.flush();
		bufferedWriter.close();
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
