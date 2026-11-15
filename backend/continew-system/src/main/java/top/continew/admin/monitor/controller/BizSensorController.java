/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
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

package top.continew.admin.monitor.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizSensorQuery;
import top.continew.admin.monitor.model.req.BizSensorReq;
import top.continew.admin.monitor.model.resp.BizSensorResp;
import top.continew.admin.monitor.service.BizSensorService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;

/** 传感器对象 API。 */
@Tag(name = "传感器对象 API")
@RestController
@CrudRequestMapping(value = "/monitor/sensor",
    api = {Api.PAGE, Api.LIST, Api.GET, Api.CREATE, Api.UPDATE, Api.BATCH_DELETE})
public class BizSensorController
    extends
    BaseController<BizSensorService, BizSensorResp, BizSensorResp, BizSensorQuery, BizSensorReq> {
}
