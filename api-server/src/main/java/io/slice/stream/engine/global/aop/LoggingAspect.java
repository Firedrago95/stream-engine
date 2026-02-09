package io.slice.stream.engine.global.aop;

import io.slice.stream.engine.global.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("within(io.slice.stream.engine.ingestion.application.IngestionService)")
    public void ingestionServicePointcut() {
    }

    @AfterThrowing(pointcut = "ingestionServicePointcut()", throwing = "ex")
    public void logIngestionException(Exception ex) {
        if (ex instanceof BusinessException be) {
            log.error("Ingestion 비즈니스 예외: {} - {}",
                be.getErrorCode().getCode(), be.getMessage(), ex);
        } else {
            log.error("Ingestion 예상치 못한 예외", ex);
        }
    }
}
