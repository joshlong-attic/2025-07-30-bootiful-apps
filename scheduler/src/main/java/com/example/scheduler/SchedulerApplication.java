package com.example.scheduler;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SpringBootApplication
public class SchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchedulerApplication.class, args);
	}

	@Bean
	MethodToolCallbackProvider methodToolCallbackProvider (DogAdoptionScheduler scheduler){
		return MethodToolCallbackProvider
				.builder()
				.toolObjects( scheduler)
				.build() ;
	}
}

@Service
class DogAdoptionScheduler {

	@Tool(description = "schedule an appointment to adopt a dog from a Pooch Palace location")
	String schedule(@ToolParam(description = "the id of the dog") int dogId, @ToolParam(description = "the name of the dog") String dogName) {
		var i = Instant
				.now()
				.plus(3, ChronoUnit.DAYS)
				.toString();
		System.out.println("scheduling adoption for dog " + dogName + "/" + dogId + " @ " + i);
		return i;
	}

}