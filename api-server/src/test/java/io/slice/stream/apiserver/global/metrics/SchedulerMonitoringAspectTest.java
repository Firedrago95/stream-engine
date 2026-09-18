package io.slice.stream.apiserver.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchedulerMonitoringAspectTest {

    private MeterRegistry meterRegistry;
    private SchedulerMonitoringAspect aspect;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new SchedulerMonitoringAspect(meterRegistry);
    }

    @Test
    @DisplayName("스케줄러 작업이 정상 완료되면 성공 메트릭과 소요 시간이 기록된다")
    void monitorSuccessfulScheduledTask() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        SampleScheduler target = new SampleScheduler();

        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = aspect.monitorScheduledTask(joinPoint);

        assertThat(result).isEqualTo("done");

        Counter successCounter = meterRegistry.find("scheduler.execution")
            .tag("scheduler", "SampleScheduler")
            .tag("status", "success")
            .counter();

        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);

        assertThat(meterRegistry.find("scheduler.execution.duration")
            .tag("scheduler", "SampleScheduler")
            .timer()).isNotNull();

        assertThat(meterRegistry.find("scheduler.last.success.timestamp")
            .tag("scheduler", "SampleScheduler")
            .gauge()).isNotNull();
    }

    @Test
    @DisplayName("스케줄러 작업 중 예외가 발생하면 실패 메트릭이 기록되고 예외가 재전파된다")
    void monitorFailedScheduledTask() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        SampleScheduler target = new SampleScheduler();

        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("스케줄러 작업 실패"));

        assertThatThrownBy(() -> aspect.monitorScheduledTask(joinPoint))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("스케줄러 작업 실패");

        Counter failureCounter = meterRegistry.find("scheduler.execution")
            .tag("scheduler", "SampleScheduler")
            .tag("status", "failure")
            .counter();

        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    static class SampleScheduler {
        public void execute() {}
    }
}
