/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mbj.jdcompatiblemonitor.device.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 熔断与退避逻辑的单元测试（FR-PROTO-07、FR-ACQ-09）。 */
class CircuitBreakerTest {

    @Test
    void staysClosedBelowThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000, 60_000);
        breaker.recordFailure(0);
        breaker.recordFailure(1);
        assertEquals(CircuitState.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest(2));
        assertEquals(0, breaker.tripCount());
    }

    @Test
    void opensWhenThresholdReached() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000, 60_000);
        breaker.recordFailure(0);
        breaker.recordFailure(0);
        breaker.recordFailure(0);
        assertEquals(CircuitState.OPEN, breaker.state());
        assertEquals(1, breaker.tripCount());
        assertFalse(breaker.allowRequest(999), "熔断期内不放行");
        assertEquals(1000L, breaker.openUntil());
    }

    @Test
    void halfOpensAfterOpenPeriodThenClosesOnSuccess() {
        CircuitBreaker breaker = new CircuitBreaker(2, 1000, 60_000);
        breaker.recordFailure(0);
        breaker.recordFailure(0);
        assertEquals(CircuitState.OPEN, breaker.state());

        assertTrue(breaker.allowRequest(1000), "期满后放行一次探测");
        assertEquals(CircuitState.HALF_OPEN, breaker.state());
        breaker.recordSuccess();
        assertEquals(CircuitState.CLOSED, breaker.state());
        assertEquals(0, breaker.consecutiveFailures());
        assertTrue(breaker.isHealthy());
    }

    @Test
    void halfOpenFailureTripsAgain() {
        CircuitBreaker breaker = new CircuitBreaker(2, 1000, 60_000);
        breaker.recordFailure(0);
        breaker.recordFailure(0);
        assertTrue(breaker.allowRequest(1000));
        breaker.recordFailure(1000);
        assertEquals(CircuitState.OPEN, breaker.state());
        assertEquals(2, breaker.tripCount());
        assertEquals(2000L, breaker.openUntil());
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        CircuitBreaker breaker = new CircuitBreaker(10, 1000, 8000);
        assertEquals(1000, breaker.backoffInterval(1000), "正常时使用基础间隔");
        breaker.recordFailure(0);
        assertEquals(2000, breaker.backoffInterval(1000));
        breaker.recordFailure(0);
        assertEquals(4000, breaker.backoffInterval(1000));
        breaker.recordFailure(0);
        assertEquals(8000, breaker.backoffInterval(1000));
        breaker.recordFailure(0);
        assertEquals(8000, breaker.backoffInterval(1000), "不超过退避上限");
        breaker.recordSuccess();
        assertEquals(1000, breaker.backoffInterval(1000));
    }

    @Test
    void neverBacksOffBelowBaseInterval() {
        CircuitBreaker breaker = new CircuitBreaker(1, 1000, 100);
        breaker.recordFailure(0);
        assertEquals(500, breaker.backoffInterval(500), "退避上限低于基础间隔时取基础间隔");
    }
}
