package io.slice.stream.apiserver.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String METRIC_NAME_LAST_DURATION = "scheduler.last.duration";
    private static final String METRIC_NAME_LAST_SUCCESS = "scheduler.last.success.timestamp";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicLong> lastSuccessTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicReference<Double>> lastDurations = new ConcurrentHashMap<>();

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
        recordDuration(schedulerName, durationNanos);
        recordExecutionCounter(schedulerName, "success");
        recordLastSuccessTimestamp(schedulerName);
    }

    private void recordFailure(String schedulerName, long durationNanos, Throwable throwable) {
        log.error("[Scheduler Monitor] 스케줄러 '{}' 실행 중 예외 발생 (사유: {})", schedulerName, throwable.getMessage(), throwable);
        recordDuration(schedulerName, durationNanos);
        recordExecutionCounter(schedulerName, "failure");
    }

    private void recordDuration(String schedulerName, long durationNanos) {
        Timer.builder(METRIC_NAME_DURATION)
            .description("스케줄러 작업 실행 소요 시간")
            .tag("scheduler", schedulerName)
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        double durationSeconds = durationNanos / 1_000_000_000.0;
        AtomicReference<Double> holder = lastDurations.computeIfAbsent(schedulerName, key -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            Gauge.builder(METRIC_NAME_LAST_DURATION, ref, AtomicReference::get)
                .description("스케줄러 작업 직전 실행 소요 시간")
                .baseUnit("seconds")
                .tag("scheduler", key)
                .register(meterRegistry);
            return ref;
        });
        holder.set(durationSeconds);
    }

    private void recordExecutionCounter(String schedulerName, String status) {
        Counter.builder(METRIC_NAME_EXECUTION)
            .description("스케줄러 작업 실행 횟수 및 결과")
            .tag("scheduler", schedulerName)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    private void recordLastSuccessTimestamp(String schedulerName) {
        AtomicLong lastSuccess = lastSuccessTimestamps.computeIfAbsent(schedulerName, key -> {
            AtomicLong holder = new AtomicLong(Instant.now().getEpochSecond());
            meterRegistry.gauge(METRIC_NAME_LAST_SUCCESS, Tags.of("scheduler", key), holder);
            return holder;
        });
        lastSuccess.set(Instant.now().getEpochSecond());
    }
}
