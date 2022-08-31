package com.digicaps.ticker.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Slf4j
@RestController
public class TickerController 
{
//	private static final String projPath = "System.getProperty(userDir)";
//	private static String USER_DIR = "C:/home/skylife/input";
	private static String USER_DIR = "C:\\home\\skylife\\input";
	private WatchKey watchKey;
	
	@PostConstruct
	public void init() throws IOException
	{
		//watchService 생성
		WatchService watchService = FileSystems.getDefault().newWatchService();
		log.info("watchService 생성");

		//경로 생성
		Path path = Paths.get(USER_DIR);
		log.info("경로 생성 = {}", path);

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
					watchKey = watchService.take(); //이벤트가 오길 대기(Blocking)
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				List<WatchEvent<?>> events = watchKey.pollEvents(); //이벤트들을 가져옴
				for(WatchEvent<?> event : events) { 
					//이벤트 종류
					WatchEvent.Kind<?> kind = event.kind();
					//경로
					Path paths = (Path)event.context();
					System.out.println(paths.toAbsolutePath());//C:\...\...\test.txt
					
					if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						System.out.println("created something in directory");
					}else if(kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
						System.out.println("delete something in directory"); 
					}else if(kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
						System.out.println("modified something in directory");
					}else if(kind.equals(StandardWatchEventKinds.OVERFLOW)) {
						System.out.println("overflow");
					}else {
						System.out.println("hello world");
					}
				} // end for
				
				if(!watchKey.reset()) {
					try {
						watchService.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} // end while
		}); // end thread
		
		thread.start();
	}    

	@GetMapping("/")
	public String test() {
		System.out.println(USER_DIR);
		return "hello";
	}
}
