package com.example.assistant;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SpringBootApplication
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }

    @Bean
    PromptChatMemoryAdvisor promptChatMemoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();
        var mwa = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
        return PromptChatMemoryAdvisor
                .builder(mwa)
                .build();
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return new QuestionAnswerAdvisor(vectorStore);
    }

    @Bean
    JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    McpSyncClient mcpSyncClient(@Value("${scheduler.uri}") String schedulerUri) {
        var mcp = McpClient
                .sync(HttpClientSseClientTransport
                        .builder(schedulerUri)
                        .build())
                .build();
        mcp.initialize();
        return mcp;
    }

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            JdbcClient db,
            VectorStore vs,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            PromptChatMemoryAdvisor promptChatMemoryAdvisor,
            DogRepository dogRepository,
            McpSyncClient mcpSyncClient
//            DogAdoptionScheduler scheduler
    ) {

        if (db.sql("select count(*) as c from vector_store")
                .query((rs, _) -> rs.getInt("c")).single() == 0) {
            dogRepository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name: %s, description: %s".formatted(dog.id(), dog.name(), dog.description()));
                vs.add(List.of(dogument));
            });
        }

        var system = """
                You are an AI powered assistant to help people adopt a dog from the adoption\s
                agency named Pooch Palace with locations in Antwerp, Seoul, Tokyo, Singapore, Paris,\s
                Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                will be presented below. If there is no information, then return a polite response suggesting we\s
                don't have any dogs available.
                """;
        return builder
                .defaultAdvisors(promptChatMemoryAdvisor, questionAnswerAdvisor)
//                .defaultTools(scheduler)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpSyncClient))
                .defaultSystem(system)
                .build();
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

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

record Dog(@Id int id, String name, String owner, String description) {
}

@Controller
@ResponseBody
class AssistantController {

    private final ChatClient chatClient;

    AssistantController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/dogs/{user}/assistant")
    String assistant(@PathVariable String user, @RequestParam String question) {
        return this.chatClient
                .prompt(question)
                .call()
                .content();
    }
}