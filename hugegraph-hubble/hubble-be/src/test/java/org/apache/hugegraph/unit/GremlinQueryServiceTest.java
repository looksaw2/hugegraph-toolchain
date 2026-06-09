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

import java.util.Collections;
import java.util.Iterator;

import org.apache.hugegraph.api.gremlin.GremlinRequest;
import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.driver.GremlinManager;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.query.AdjacentQuery;
import org.apache.hugegraph.entity.query.GremlinQuery;
import org.apache.hugegraph.entity.query.GremlinResult;
import org.apache.hugegraph.entity.query.GremlinResult.Type;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.service.HugeClientPoolService;
import org.apache.hugegraph.service.query.GremlinQueryService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.apache.hugegraph.structure.gremlin.Result;
import org.apache.hugegraph.structure.gremlin.ResultSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.ImmutableList;

public class GremlinQueryServiceTest {

    private static final int CONN_ID = 1;

    private GremlinQueryService service;
    private HugeConfig config;
    private HugeClient client;
    private GremlinManager gremlin;
    private GremlinRequest.Builder builder;
    private VertexLabelService vlService;

    @Before
    public void setup() {
        this.service = new GremlinQueryService();
        this.config = Mockito.mock(HugeConfig.class);
        this.client = Mockito.mock(HugeClient.class);
        this.gremlin = Mockito.mock(GremlinManager.class);
        this.builder = Mockito.mock(GremlinRequest.Builder.class);
        this.vlService = Mockito.mock(VertexLabelService.class);

        Mockito.when(this.config.get(HubbleOptions.GREMLIN_SUFFIX_LIMIT))
               .thenReturn(10);
        Mockito.when(this.config.get(HubbleOptions.GREMLIN_VERTEX_DEGREE_LIMIT))
               .thenReturn(2);
        Mockito.when(this.config.get(HubbleOptions.GREMLIN_EDGES_TOTAL_LIMIT))
               .thenReturn(20);
        Mockito.when(this.config.get(HubbleOptions.GREMLIN_BATCH_QUERY_IDS))
               .thenReturn(10);
        Mockito.when(this.client.gremlin()).thenReturn(this.gremlin);
        Mockito.when(this.gremlin.gremlin(Mockito.anyString()))
               .thenReturn(this.builder);

        HugeClientPoolService pool = new HugeClientPoolService();
        pool.put(CONN_ID, this.client);
        ReflectionTestUtils.setField(this.service, "config", this.config);
        ReflectionTestUtils.setField(this.service, "poolService", pool);
        ReflectionTestUtils.setField(this.service, "vlService", this.vlService);
    }

    @Test
    public void testExecuteQueryBuildsGeneralViews() {
        ResultSet resultSet = resultSet(new Result("marko"), new Result(29));
        Mockito.when(this.builder.execute()).thenReturn(resultSet);

        GremlinResult result = this.service.executeQuery(
                CONN_ID, GremlinQuery.builder().content("g.V();g.E()").build());

        Assert.assertEquals(Type.GENERAL, result.getType());
        Assert.assertEquals(2, result.getJsonView().getData().size());
        Assert.assertEquals(2, result.getTableView().getRows().size());
        Assert.assertSame(result.getGraphView().EMPTY, result.getGraphView());
        Mockito.verify(this.gremlin).gremlin("g.V().limit(10);g.E().limit(10)");
    }

    @Test
    public void testExecuteAsyncTaskDelegatesRawQuery() {
        Mockito.when(this.gremlin.executeAsTask(Mockito.any(GremlinRequest.class)))
               .thenReturn(77L);
        ArgumentCaptor<GremlinRequest> captor =
                ArgumentCaptor.forClass(GremlinRequest.class);

        Long task = this.service.executeAsyncTask(
                CONN_ID, GremlinQuery.builder().content("g.V().count()").build());

        Assert.assertEquals(Long.valueOf(77L), task);
        Mockito.verify(this.gremlin).executeAsTask(captor.capture());
        Assert.assertEquals("g.V().count()", captor.getValue().gremlin);
    }

    @Test
    public void testExpandVertexBuildsGremlinWithConvertedNumericId() {
        ResultSet resultSet = resultSet();
        Mockito.when(this.builder.execute()).thenReturn(resultSet);
        Mockito.when(this.vlService.get("person", CONN_ID))
               .thenReturn(VertexLabelEntity.builder()
                                            .name("person")
                                            .idStrategy(IdStrategy.CUSTOMIZE_NUMBER)
                                            .build());
        AdjacentQuery.Condition condition = AdjacentQuery.Condition.builder()
                                                                   .key("weight")
                                                                   .operator("gt")
                                                                   .value(0.5D)
                                                                   .build();
        AdjacentQuery query = AdjacentQuery.builder()
                                           .vertexId("123")
                                           .vertexLabel("person")
                                           .direction(Direction.OUT)
                                           .edgeLabel("knows")
                                           .conditions(ImmutableList.of(condition))
                                           .build();

        GremlinResult result = this.service.expandVertex(CONN_ID, query);

        Assert.assertEquals(Type.PATH, result.getType());
        Assert.assertTrue(result.getGraphView().getVertices().isEmpty());
        Mockito.verify(this.gremlin).gremlin(
                "g.V(123).toE(OUT, 'knows').has('weight', gt(0.5))" +
                ".limit(2).otherV().path()");
    }

    private static ResultSet resultSet(Result... results) {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.size()).thenReturn(results.length);
        Mockito.when(resultSet.iterator()).thenAnswer(invocation -> {
            Iterator<Result> iterator;
            iterator = ImmutableList.copyOf(results).iterator();
            return iterator;
        });
        if (results.length == 0) {
            Mockito.when(resultSet.iterator()).thenAnswer(invocation -> {
                return Collections.<Result>emptyList().iterator();
            });
        }
        return resultSet;
    }
}
