package com.group7.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Global async-execution configuration (#349 polish). Replaces Spring's
 * default {@code SimpleAsyncTaskExecutor} (one new thread per
 * {@code @Async} invocation, unbounded — wasteful under heavy load and
 * easy to OOM in pathological cases) with a bounded
 * {@link ThreadPoolTaskExecutor} that pools threads and queues overflow.
 *
 * <p>Affects every {@code @Async} listener in the codebase:
 * {@link com.group7.backend.event.NotificationEventListener},
 * {@link com.group7.backend.event.FeedFanoutListener}, and any future
 * additions. Existing per-listener {@code try/catch} blocks remain
 * load-bearing because they capture contextual fields (postId,
 * recipientId, etc.) that the global handler can't see.
 *
 * <p><b>Pool sizing.</b> Core 4 / max 16 / queue 100 is the
 * conservative shape for a single-replica deploy: most async work is
 * short-lived broker dispatches and DB writes, so the core pool absorbs
 * baseline load and the queue absorbs bursts. If the queue fills, the
 * caller-runs policy back-pressures the publisher rather than rejecting
 * — for transactional event listeners this means the AFTER_COMMIT
 * synchronisation just runs the listener inline rather than dropping
 * the event.
 *
 * <p><b>Why caller-runs over abort.</b> Abort would surface as a
 * {@code RejectedExecutionException} which the publisher's transaction
 * is already past (AFTER_COMMIT). The work would be silently lost.
 * Caller-runs is slower but always completes; the publisher just feels
 * a one-time latency bump on the rare overflow.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * Catches anything an {@code @Async} method throws that wasn't
     * handled inside the method itself. The default handler in Spring
     * just logs at WARN with no method context; this version logs at
     * ERROR with the declaring class + method name + arg count so
     * production triage has something to grep for.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error(
                "Async error in {}.{} (args={}): {}",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                params.length,
                ex.getMessage(),
                ex);
    }
}
