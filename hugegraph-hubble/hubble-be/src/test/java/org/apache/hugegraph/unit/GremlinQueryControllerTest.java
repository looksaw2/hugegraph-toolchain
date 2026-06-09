/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
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

import org.apache.hugegraph.controller.query.GremlinQueryController;
import org.apache.hugegraph.entity.enums.ExecuteStatus;
import org.apache.hugegraph.entity.query.AdjacentQuery;
import org.apache.hugegraph.entity.query.ExecuteHistory;
import org.apache.hugegraph.entity.query.GraphView;
import org.apache.hugegraph.entity.query.GremlinQuery;
import org.apache.hugegraph.entity.query.GremlinResult;
import org.apache.hugegraph.entity.query.GremlinResult.Type;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.service.query.ExecuteHistoryService;
import org.apache.hugegraph.service.query.GremlinQueryService;
import org.apache.hugegraph.structure.constant.Direction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.ImmutableList;

public class GremlinQueryControllerTest {

    private static final int CONN_ID = 1;

    private GremlinQueryController controller;
    private GremlinQueryService queryService;
    private ExecuteHistoryService historyService;

    @Before
    public void setup() {
        this.controller = new GremlinQueryController();
        this.queryService = Mockito.mock(GremlinQueryService.class);
        this.historyService = Mockito.mock(ExecuteHistoryService.class);
        ReflectionTestUtils.setField(this.controller, "queryService",
                                     this.queryService);
        ReflectionTestUtils.setField(this.controller, "historyService",
                                     this.historyService);
    }

    @Test
    public void testExecuteStoresSuccessHistory() {
        GremlinQuery query = GremlinQuery.builder().content("g.V()").build();
        GremlinResult expected = GremlinResult.builder()
                                             .type(Type.EMPTY)
                                             .build();
        Mockito.when(this.queryService.executeQuery(CONN_ID, query))
               .thenReturn(expected);

        GremlinResult actual = this.controller.execute(CONN_ID, query);

        Assert.assertSame(expected, actual);
        ArgumentCaptor<ExecuteHistory> captor =
                ArgumentCaptor.forClass(ExecuteHistory.class);
        Mockito.verify(this.historyService).save(captor.capture());
        ExecuteHistory saved = captor.getValue();
        Assert.assertEquals(ExecuteStatus.SUCCESS, saved.getStatus());
        Assert.assertEquals("g.V()", saved.getContent());
        Assert.assertTrue(saved.getDuration() >= 0L);
        Mockito.verify(this.historyService).update(saved);
    }

    @Test
    public void testExecuteStoresFailedHistoryAndRethrows() {
        GremlinQuery query = GremlinQuery.builder().content("g.V(").build();
        RuntimeException failure = new RuntimeException("invalid");
        Mockito.when(this.queryService.executeQuery(CONN_ID, query))
               .thenThrow(failure);

        RuntimeException actual = expect(RuntimeException.class, () -> {
            this.controller.execute(CONN_ID, query);
        });

        Assert.assertSame(failure, actual);
        ArgumentCaptor<ExecuteHistory> captor =
                ArgumentCaptor.forClass(ExecuteHistory.class);
        Mockito.verify(this.historyService).save(captor.capture());
        Assert.assertEquals(ExecuteStatus.FAILED, captor.getValue().getStatus());
        Mockito.verify(this.historyService).update(captor.getValue());
    }

    @Test
    public void testExecuteAsyncTaskStoresAsyncIdAndStatus() {
        GremlinQuery query = GremlinQuery.builder().content("g.V().count()")
                                         .build();
        Mockito.when(this.queryService.executeAsyncTask(CONN_ID, query))
               .thenReturn(99L);

        ExecuteStatus status = this.controller.executeAsyncTask(CONN_ID, query);

        Assert.assertEquals(ExecuteStatus.ASYNC_TASK_SUCCESS, status);
        ArgumentCaptor<ExecuteHistory> captor =
                ArgumentCaptor.forClass(ExecuteHistory.class);
        Mockito.verify(this.historyService).save(captor.capture());
        ExecuteHistory saved = captor.getValue();
        Assert.assertEquals(ExecuteStatus.ASYNC_TASK_SUCCESS, saved.getStatus());
        Assert.assertEquals(99L, saved.getAsyncId().longValue());
        Mockito.verify(this.historyService).update(saved);
    }

    @Test(expected = ExternalException.class)
    public void testExpandRejectsInvalidConditionOperator() {
        AdjacentQuery.Condition condition = AdjacentQuery.Condition.builder()
                                                                   .key("age")
                                                                   .operator("bad")
                                                                   .value(1)
                                                                   .build();
        AdjacentQuery query = AdjacentQuery.builder()
                                           .vertexId("1")
                                           .vertexLabel("person")
                                           .conditions(ImmutableList.of(condition))
                                           .build();

        this.controller.expand(CONN_ID, query);
    }

    @Test
    public void testExpandWrapsServiceFailure() {
        AdjacentQuery query = AdjacentQuery.builder()
                                           .vertexId("1")
                                           .vertexLabel("person")
                                           .direction(Direction.BOTH)
                                           .build();
        Mockito.when(this.queryService.expandVertex(CONN_ID, query))
               .thenThrow(new IllegalStateException("backend down"));

        ExternalException exception = expect(ExternalException.class, () -> {
            this.controller.expand(CONN_ID, query);
        });

        Assert.assertTrue(exception.getMessage().contains("gremlin.expand.failed"));
    }

    @Test
    public void testExpandDelegatesValidQuery() {
        AdjacentQuery query = AdjacentQuery.builder()
                                           .vertexId("1")
                                           .vertexLabel("person")
                                           .direction(Direction.OUT)
                                           .build();
        GremlinResult result = GremlinResult.builder()
                                            .type(Type.PATH)
                                            .graphView(new GraphView())
                                            .build();
        Mockito.when(this.queryService.expandVertex(CONN_ID, query))
               .thenReturn(result);

        Assert.assertSame(result, this.controller.expand(CONN_ID, query));
    }

    private interface ThrowingRunnable {

        void run();
    }

    private static <T extends Throwable> T expect(Class<T> type,
                                                  ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
            throw new AssertionError("Unexpected exception type", e);
        }
        throw new AssertionError("Expected exception " + type.getName());
    }
}
