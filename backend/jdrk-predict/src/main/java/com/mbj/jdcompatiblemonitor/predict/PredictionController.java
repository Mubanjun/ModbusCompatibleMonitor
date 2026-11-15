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

package com.mbj.jdcompatiblemonitor.predict;

import com.mbj.jdcompatiblemonitor.module.predict.PredictionProvider;
import com.mbj.jdcompatiblemonitor.module.predict.PredictionRequest;
import com.mbj.jdcompatiblemonitor.module.predict.PredictionResult;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 预测预留接口：未启用时返回 enabled=false，不抛 500。 */
@RestController
@RequestMapping("/monitor/predict")
public class PredictionController {

    private final PredictionProvider provider;

    public PredictionController(PredictionProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/status")
    public PredictionResult status() {
        return provider.predict(new PredictionRequest(null, Instant.now(), Instant.now()));
    }

    @GetMapping("/sample")
    public PredictionResult sample(@RequestParam Long sensorId) {
        Instant now = Instant.now();
        return provider.predict(new PredictionRequest(sensorId, now.minusSeconds(3600), now));
    }
}
