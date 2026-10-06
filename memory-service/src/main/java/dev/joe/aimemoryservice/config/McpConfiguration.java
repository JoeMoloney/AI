package dev.joe.aimemoryservice.config;

import dev.joe.aimemoryservice.mcp.MemoryMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfiguration {
    @Bean
    ToolCallbackProvider memoryToolCallbacks(MemoryMcpTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();
    }
}
