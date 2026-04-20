package com.hospital.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * AOP aspect that logs every service method call with duration.
 * Follows the Observer pattern at the infrastructure level.
 */
@Aspect
@Component
@Slf4j
public class AopLoggingAspect {

    /** Intercepts all public methods inside any service.impl package. */
    @Around("execution(public * com.hospital.service.impl.*.*(..))")
    public Object logServiceMethod(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().toShortString();
        log.debug(">>> Entering: {}", methodName);
        StopWatch sw = new StopWatch();
        sw.start();
        try {
            Object result = pjp.proceed();
            sw.stop();
            log.debug("<<< Exited: {} in {}ms", methodName, sw.getTotalTimeMillis());
            return result;
        } catch (Throwable ex) {
            sw.stop();
            log.warn("<<< Exception in {} after {}ms: {}", methodName, sw.getTotalTimeMillis(), ex.getMessage());
            throw ex;
        }
    }
}
