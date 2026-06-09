/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.unit;

import java.util.Arrays;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.controller.load.FileUploadController;
import org.apache.hugegraph.entity.enums.JobStatus;
import org.apache.hugegraph.entity.load.JobManager;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.options.HubbleOptions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

public class FileUploadControllerTest {

    private FileUploadController controller;
    private HugeConfig config;

    @Before
    public void setup() {
        this.controller = new FileUploadController();
        this.config = Mockito.mock(HugeConfig.class);
        Mockito.when(this.config.get(HubbleOptions.UPLOAD_FILE_FORMAT_LIST))
               .thenReturn(Arrays.asList("csv", " txt "));
        ReflectionTestUtils.setField(this.controller, "config", this.config);
    }

    @Test
    public void testCheckFileValidAcceptsUppercaseAllowedExtension() {
        ReflectionTestUtils.invokeMethod(this.controller, "checkFileValid", 1,
                                         job(), file(), "HLM.TXT");
    }

    @Test(expected = ExternalException.class)
    public void testCheckFileValidRejectsUnsupportedExtension() {
        ReflectionTestUtils.invokeMethod(this.controller, "checkFileValid", 1,
                                         job(), file(), "hlm.json");
    }

    @Test(expected = ExternalException.class)
    public void testCheckFileValidRejectsMissingExtension() {
        ReflectionTestUtils.invokeMethod(this.controller, "checkFileValid", 1,
                                         job(), file(), "hlm");
    }

    private static JobManager job() {
        return JobManager.builder()
                         .jobStatus(JobStatus.DEFAULT)
                         .build();
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "HLM.TXT", "text/plain",
                                     "source,target\n".getBytes());
    }
}
