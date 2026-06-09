/*
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

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.driver.GraphManager;
import org.apache.hugegraph.driver.TraverserManager;
import org.apache.hugegraph.rest.RestHeaders;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.graph.Edge;
import org.apache.hugegraph.structure.graph.Shard;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.graph.Vertices;
import org.apache.hugegraph.structure.traverser.PathOfVertices;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TraverserManagerTest extends BaseUnitTest {

    private RestClient client;
    private TraverserManager traverser;

    @Before
    public void setup() {
        this.client = Mockito.mock(RestClient.class);
        GraphManager graph = new GraphManager(this.client, "DEFAULT", "hugegraph");
        this.traverser = new TraverserManager(this.client, graph);
    }

    @Test
    public void testShortestPathBuildsParamsAndParsesResponse() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.anyString(), paramsCaptor.capture()))
               .thenReturn(result("{\"path\":[\"1\",\"2\"],"
                                  + "\"vertices\":[\"1\",\"2\"],"
                                  + "\"edges\":[],\"measure\":{\"weight\":1}}"));

        PathOfVertices path = this.traverser.shortestPath("1", "2",
                                                          Direction.OUT,
                                                          "knows", 3,
                                                          10L, 20L);

        Assert.assertEquals(Arrays.asList("1", "2"), path.getVertices());
        Map<String, Object> params = paramsCaptor.getValue();
        Assert.assertEquals("\"1\"", params.get("source"));
        Assert.assertEquals("\"2\"", params.get("target"));
        Assert.assertEquals(Direction.OUT, params.get("direction"));
        Assert.assertEquals("knows", params.get("label"));
        Assert.assertEquals(3, params.get("max_depth"));
        Assert.assertEquals(10L, params.get("max_degree"));
        Assert.assertEquals(20L, params.get("capacity"));
    }

    @Test
    public void testShortestPathRejectsInvalidDepthBeforeRequest() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.traverser.shortestPath("1", "2", Direction.BOTH, null, 0);
        });
        Mockito.verifyNoInteractions(this.client);
    }

    @Test
    public void testKoutPassesNearestDegreeCapacityAndLimit() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.endsWith("/traversers/kout"),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"vertices\":[\"2\",\"3\"]}"));

        Map<String, Object> result = this.traverser.kout("1", Direction.IN,
                                                         "created", 2, false,
                                                         9L, 99L, 7L);

        Assert.assertEquals(Arrays.asList("2", "3"), result.get("vertices"));
        Assert.assertEquals("\"1\"", paramsCaptor.getValue().get("source"));
        Assert.assertEquals(Direction.IN, paramsCaptor.getValue().get("direction"));
        Assert.assertEquals("created", paramsCaptor.getValue().get("label"));
        Assert.assertEquals(2, paramsCaptor.getValue().get("max_depth"));
        Assert.assertEquals(false, paramsCaptor.getValue().get("nearest"));
        Assert.assertEquals(9L, paramsCaptor.getValue().get("max_degree"));
        Assert.assertEquals(99L, paramsCaptor.getValue().get("capacity"));
        Assert.assertEquals(7L, paramsCaptor.getValue().get("limit"));
    }

    @Test
    public void testVerticesScanRejectsInvalidPagingAndDelegatesValidScan() {
        Shard shard = new Shard("a", "z", 0L);
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.traverser.vertices(shard, null);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.traverser.vertices(shard, "page-token", -1L);
        });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.endsWith("/traversers/vertices/scan"),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"vertices\":[{\"id\":\"1\","
                                  + "\"label\":\"person\","
                                  + "\"properties\":{}}],"
                                  + "\"page\":\"next\"}"));

        Vertices vertices = this.traverser.vertices(shard, "page-token", 10L);

        Assert.assertEquals("next", vertices.page());
        Assert.assertEquals(1, vertices.results().size());
        Assert.assertEquals("a", paramsCaptor.getValue().get("start"));
        Assert.assertEquals("z", paramsCaptor.getValue().get("end"));
        Assert.assertEquals("page-token", paramsCaptor.getValue().get("page"));
        Assert.assertEquals(10L, paramsCaptor.getValue().get("page_limit"));
    }

    @Test
    public void testVerticesAndEdgesListAttachGraphManagerAndEdgeExistenceDefaults() {
        Mockito.when(this.client.get(Mockito.endsWith("/traversers/vertices"),
                                     Mockito.anyMap()))
               .thenReturn(result("{\"vertices\":[{\"id\":\"1\","
                                  + "\"label\":\"person\","
                                  + "\"properties\":{}}]}"));
        Mockito.when(this.client.get(Mockito.endsWith("/traversers/edges"),
                                     Mockito.anyMap()))
               .thenReturn(result("{\"edges\":[{\"id\":\"S1:knows:sort:T2\","
                                  + "\"label\":\"knows\","
                                  + "\"type\":\"edge\","
                                  + "\"outV\":\"1\","
                                  + "\"outVLabel\":\"person\","
                                  + "\"inV\":\"2\","
                                  + "\"inVLabel\":\"person\","
                                  + "\"properties\":{}}]}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.endsWith("/traversers/edgeexist"),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"edges\":[]}"));

        List<Vertex> vertices = this.traverser.vertices(Collections.singletonList("1"));
        List<Edge> edges = this.traverser.edges(
                Collections.singletonList("S1:knows:sort:T2"));
        List<Edge> existing = this.traverser.edgeExistence("1", "2");

        Assert.assertEquals(1, vertices.size());
        Assert.assertEquals(1, edges.size());
        Assert.assertTrue(existing.isEmpty());
        Assert.assertEquals("\"1\"", paramsCaptor.getValue().get("source"));
        Assert.assertEquals("\"2\"", paramsCaptor.getValue().get("target"));
        Assert.assertEquals("", paramsCaptor.getValue().get("label"));
        Assert.assertFalse(paramsCaptor.getValue().containsKey("sort_values"));
        Assert.assertEquals(-1, paramsCaptor.getValue().get("limit"));
    }

    private static RestResult result(String content) {
        return new RestResult(200, content, new RestHeaders());
    }
}
