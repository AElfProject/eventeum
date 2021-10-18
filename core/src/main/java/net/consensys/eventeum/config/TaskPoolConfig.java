package net.consensys.eventeum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Title:TaskPoolConfig
 * @auther: kakapo911
 * @date: 2021/10/156:14 PM
 */
@Configuration
@EnableAsync
public class TaskPoolConfig implements AsyncConfigurer {

    public static final int cpuNum = Runtime.getRuntime().availableProcessors();

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(cpuNum);
        taskExecutor.setMaxPoolSize(cpuNum*2);
        taskExecutor.setQueueCapacity(Integer.MAX_VALUE);
        taskExecutor.setKeepAliveSeconds(60);
        taskExecutor.setThreadNamePrefix("AsyncTaskExecutor--");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(60);
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        taskExecutor.initialize();
        return taskExecutor;
    }
}
