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

package com.mbj.jdcompatiblemonitor.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 领域模块不得反向依赖 Web 与壳。 */
@AnalyzeClasses(packages = "com.mbj.jdcompatiblemonitor",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class DependencyRulesTest {

    @ArchTest
    static final ArchRule DOMAIN_NO_WEB_OR_SHELL = noClasses().that()
        .resideInAnyPackage("..protocol..", "..device..", "..storage..", "..report..", "..sync..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "top.continew..");
}
