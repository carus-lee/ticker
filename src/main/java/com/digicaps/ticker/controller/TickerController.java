package com.digicaps.ticker.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Slf4j
@RestController
public class TickerController 
{
	private static final String USER_DIR = "C:\\home\\skylife\\input";
	private WatchKey watchKey;

	@PostConstruct
	public void init() throws IOException
	{
		//watchService 생성
		WatchService watchService = FileSystems.getDefault().newWatchService();

		log.info("디렉토리 경로 = {}", USER_DIR);
		//경로 생성
		Path path = Paths.get(USER_DIR);

		//해당 디렉토리 경로에 와치서비스와 이벤트 등록
		path.register(watchService, 
				StandardWatchEventKinds.ENTRY_CREATE, 
				StandardWatchEventKinds.ENTRY_DELETE, 
				StandardWatchEventKinds.ENTRY_MODIFY, 
				StandardWatchEventKinds.OVERFLOW);
		
		Thread thread = new Thread(()-> {
			while(true)
			{
				try {
					watchKey = watchService.take();//이벤트가 오길 대기(Blocking)
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				List<WatchEvent<?>> events = watchKey.pollEvents(); //이벤트들을 가져옴
				for(WatchEvent<?> event : events)
				{
					//이벤트 종류
					WatchEvent.Kind<?> kind = event.kind();
					//경로
					Path paths = (Path)event.context();
					log.info("path = {}", paths.toAbsolutePath()); //C:\...\...\test.txt
					
					if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						log.info("created something in directory");

						// 파일처리 함수 호출
						try {
							log.info("fnFileCut() call");
							fnFileCut(paths.getFileName().toString());
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


				if(!watchKey.reset()) {
					try {
						log.info("watchService.close()");
						watchService.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				log.info("watchService.close()");

			} // end while
		}); // end thread
		
		thread.start();
	}

	public void fnFileCut(String fileName) throws IOException
	{
		log.info("========== fnFileCut() =============");
		// 파일 입력스트림 생성
		String fileFullPath = USER_DIR + "\\" + fileName;
		log.info("fileFullPath = {}", fileFullPath);
		FileReader fileReader;
		try {
			fileReader = new FileReader(fileFullPath);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

		// 입력 버퍼 생성
		BufferedReader bufferedReader = new BufferedReader(fileReader);

		// 읽기 수행
		String line ;

		// 파일 내 문자열을 1줄씩 읽기
		while( (line = bufferedReader.readLine()) != null )
		{
			log.info("line = {}", line);
			String[] cutStrArr = line.split("\\^", 20);
			log.info("cutStrArr.length = {}", cutStrArr.length);
			log.info("===================");
			for(String str : cutStrArr)
			{
				log.info("  split str = {}", str);
			}
			log.info("total cutStr = {}", cutStrArr);
		}
	}

	@GetMapping("/test")
	public String test() {
		return "hello";
	}
}
