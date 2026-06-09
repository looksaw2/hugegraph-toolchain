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

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.hugegraph.entity.enums.JobStatus;
import org.apache.hugegraph.entity.enums.LoadStatus;
import org.apache.hugegraph.entity.load.JobManager;
import org.apache.hugegraph.entity.load.LoadTask;
import org.apache.hugegraph.mapper.load.JobManagerMapper;
import org.apache.hugegraph.service.load.JobManagerService;
import org.apache.hugegraph.service.load.LoadTaskService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JobManagerServiceTest {

    private JobManagerService service;
    private StubJobManagerMapper mapper;
    private StubLoadTaskService taskService;

    @Before
    public void setup() {
        this.service = new JobManagerService();
        this.mapper = new StubJobManagerMapper();
        this.taskService = new StubLoadTaskService();
        ReflectionTestUtils.setField(this.service, "mapper",
                                     this.mapper.proxy());
        ReflectionTestUtils.setField(this.service, "taskService",
                                     this.taskService);
    }

    @Test
    public void testGetRefreshesLoadingJobToSuccess() {
        Date loadingTime = new Date(1L);
        JobManager job = job(JobStatus.LOADING, loadingTime);
        this.mapper.job = job;
        this.taskService.tasks = Arrays.asList(loadTask(LoadStatus.SUCCEED));

        JobManager result = this.service.get(1);

        Assert.assertSame(job, result);
        Assert.assertEquals(JobStatus.SUCCESS, result.getJobStatus());
        Assert.assertTrue(result.getUpdateTime().getTime() >=
                          loadingTime.getTime());
        Assert.assertEquals(1, this.mapper.updateCount);
    }

    @Test
    public void testGetRefreshesLoadingJobToFailed() {
        Date loadingTime = new Date(1L);
        JobManager job = job(JobStatus.LOADING, loadingTime);
        this.mapper.job = job;
        this.taskService.tasks = Arrays.asList(loadTask(LoadStatus.SUCCEED),
                                               loadTask(LoadStatus.FAILED));

        JobManager result = this.service.get(1);

        Assert.assertEquals(JobStatus.FAILED, result.getJobStatus());
        Assert.assertTrue(result.getUpdateTime().getTime() >=
                          loadingTime.getTime());
        Assert.assertEquals(1, this.mapper.updateCount);
    }

    @Test
    public void testGetKeepsLoadingJobWhenTaskIsStillRunning() {
        JobManager job = job(JobStatus.LOADING, new Date(1L));
        this.mapper.job = job;
        this.taskService.tasks = Arrays.asList(loadTask(LoadStatus.SUCCEED),
                                               loadTask(LoadStatus.RUNNING));

        JobManager result = this.service.get(1);

        Assert.assertEquals(JobStatus.LOADING, result.getJobStatus());
        Assert.assertEquals(0, this.mapper.updateCount);
    }

    @Test
    public void testBatchListRefreshesLoadingJob() {
        JobManager job = job(JobStatus.LOADING, new Date(1L));
        this.mapper.jobs = Arrays.asList(job);
        this.taskService.tasks = Arrays.asList(loadTask(LoadStatus.SUCCEED));

        List<JobManager> results = this.service.list(1, Arrays.asList(1));

        Assert.assertEquals(1, results.size());
        Assert.assertSame(job, results.get(0));
        Assert.assertEquals(JobStatus.SUCCESS, results.get(0).getJobStatus());
        Assert.assertEquals(1, this.mapper.updateCount);
    }

    @Test
    public void testGetKeepsLoadingJobWhenTaskListIsEmpty() {
        JobManager job = job(JobStatus.LOADING, new Date(1L));
        this.mapper.job = job;
        this.taskService.tasks = Collections.emptyList();

        JobManager result = this.service.get(1);

        Assert.assertEquals(JobStatus.LOADING, result.getJobStatus());
        Assert.assertEquals(0, this.mapper.updateCount);
    }

    private static JobManager job(JobStatus status, Date updateTime) {
        return JobManager.builder()
                         .id(1)
                         .jobStatus(status)
                         .createTime(new Date(0L))
                         .updateTime(updateTime)
                         .build();
    }

    private static LoadTask loadTask(LoadStatus status) {
        return LoadTask.builder()
                       .status(status)
                       .build();
    }

    private static class StubLoadTaskService extends LoadTaskService {

        private List<LoadTask> tasks;

        @Override
        public List<LoadTask> taskListByJob(int jobId) {
            Assert.assertEquals(1, jobId);
            return this.tasks;
        }
    }

    private static class StubJobManagerMapper {

        private JobManager job;
        private List<JobManager> jobs;
        private int updateCount;

        private JobManagerMapper proxy() {
            return (JobManagerMapper) Proxy.newProxyInstance(
                    JobManagerMapper.class.getClassLoader(),
                    new Class[]{JobManagerMapper.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("selectById")) {
                            Assert.assertEquals(1, args[0]);
                            return this.job;
                        }
                        if (method.getName().equals("selectBatchIds")) {
                            return this.jobs;
                        }
                        if (method.getName().equals("updateById")) {
                            if (this.job != null) {
                                Assert.assertSame(this.job, args[0]);
                            } else {
                                Assert.assertTrue(this.jobs.contains(args[0]));
                            }
                            this.updateCount++;
                            return 1;
                        }
                        if (method.getName().equals("toString")) {
                            return "StubJobManagerMapper";
                        }
                        throw new UnsupportedOperationException(
                                method.getName());
                    });
        }
    }
}
