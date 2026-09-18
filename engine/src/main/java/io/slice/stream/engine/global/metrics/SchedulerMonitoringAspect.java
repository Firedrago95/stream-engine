package io.slice.stream.engine.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SchedulerMonitoringAspect {

    private static final String METRIC_NAME_EXECUTION = "scheduler.execution";
    private static final String METRIC_NAME_DURATION = "scheduler.execution.duration";
    private static final String METRIC_NAME_LAST_SUCCESS = "scheduler.last.success.timestamp";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicLong> lastSuccessTimestamps = new ConcurrentHashMap<>();

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object monitorScheduledTask(ProceedingJoinPoint joinPoint) throws Throwable {
        String schedulerName = resolveSchedulerName(joinPoint);
        long startTime = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long durationNanos = System.nanoTime() - startTime;

            recordSuccess(schedulerName, durationNanos);
            return result;
        } catch (Throwable throwable) {
            long durationNanos = System.nanoTime() - startTime;
            recordFailure(schedulerName, durationNanos, throwable);
            throw throwable;
        }
    }

    private String resolveSchedulerName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName();
    }

    private void recordSuccess(String schedulerName, long durationNanos) {
        Timer.builder(METRIC_NAME_DURATION)
            .description("스케줄러 작업 실행 소요 시간")
            .tag("scheduler", schedulerName)
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        Counter.builder(METRIC_NAME_EXECUTION)
            .description("스케줄러 작업 실행 횟수 및 결과")
            .tag("scheduler", schedulerName)
            .tag("status", "success")
            .register(meterRegistry)
            .increment();

        AtomicLong lastSuccess = lastSuccessTimestamps.computeIfAbsent(schedulerName, key -> {
            AtomicLong holder = new AtomicLong(Instant.now().getEpochSecond());
            meterRegistry.gauge(METRIC_NAME_LAST_SUCCESS, Tags.of("scheduler", key), holder);
            return holder;
        });
        lastSuccess.set(Instant.now().getEpochSecond());
    }

    private void recordFailure(String schedulerName, long durationNanos, Throwable throwable) {
        log.error("[Scheduler Monitor] 스케줄러 '{}' 실행 중 예외 발생 (사유: {})", schedulerName, throwable.getMessage(), throwable);

        Timer.builder(METRIC_NAME_DURATION)
            .description("스케줄러 작업 실행 소요 시간")
            .tag("scheduler", schedulerName)
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        Counter.builder(METRIC_NAME_EXECUTION)
            .description("스케줄러 작업 실행 횟수 및 결과")
            .tag("scheduler", schedulerName)
            .tag("status", "failure")
            .register(meterRegistry)
            .increment();
    }
}
