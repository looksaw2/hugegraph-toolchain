/*
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

import java.util.List;
import java.util.Map;

import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.driver.GraphManager;
import org.apache.hugegraph.exception.InvalidOperationException;
import org.apache.hugegraph.rest.RestHeaders;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.constant.T;
import org.apache.hugegraph.structure.graph.Edge;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableMap;

public class GraphManagerRequestTest extends BaseUnitTest {

    private RestClient client;
    private GraphManager manager;

    @Before
    public void setup() {
        this.client = Mockito.mock(RestClient.class);
        this.manager = new GraphManager(this.client, "DEFAULT", "hugegraph");
    }

    @Test
    public void testAddVertexBuildsVertexAndPostsToVertexPath() {
        RestResult result = new RestResult(200, "{"
                                               + "\"id\":\"1\","
                                               + "\"label\":\"person\","
                                               + "\"properties\":{\"name\":\"marko\"}"
                                               + "}", new RestHeaders());
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.when(this.client.post(pathCaptor.capture(), bodyCaptor.capture()))
               .thenReturn(result);

        Vertex vertex = this.manager.addVertex(T.LABEL, "person", T.ID, "1",
                                               "name", "marko");

        Assert.assertEquals("1", vertex.id());
        Assert.assertEquals("person", vertex.label());
        Assert.assertEquals("marko", vertex.property("name"));
        Assert.assertEquals("graphspaces/DEFAULT/graphs/hugegraph/graph/vertices",
                            pathCaptor.getValue());

        Vertex posted = (Vertex) bodyCaptor.getValue();
        Assert.assertEquals("1", posted.id());
        Assert.assertEquals("person", posted.label());
        Assert.assertEquals("marko", posted.property("name"));
    }

    @Test
    public void testAddVertexRejectsMissingLabel() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.manager.addVertex(T.ID, "1", "name", "marko");
        });
        Mockito.verifyNoInteractions(this.client);
    }

    @Test
    public void testListEdgesPassesQueryParameters() {
        RestResult result = new RestResult(200, "{\"edges\":[],\"page\":null}",
                                           new RestHeaders());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.anyString(), paramsCaptor.capture()))
               .thenReturn(result);

        List<Edge> edges = this.manager.getEdges("1", Direction.OUT, "knows",
                                                 ImmutableMap.of("weight", 0.5D),
                                                 true, 2, 10);

        Assert.assertTrue(edges.isEmpty());
        Map<String, Object> params = paramsCaptor.getValue();
        Assert.assertEquals("\"1\"", params.get("vertex_id"));
        Assert.assertEquals(Direction.OUT, params.get("direction"));
        Assert.assertEquals("knows", params.get("label"));
        Assert.assertEquals("{\"weight\":0.5}", params.get("properties"));
        Assert.assertEquals(true, params.get("keep_start_p"));
        Assert.assertEquals(2, params.get("offset"));
        Assert.assertEquals(10, params.get("limit"));
        Assert.assertNull(params.get("page"));
    }

    @Test
    public void testAddEdgeRejectsCustomIdBeforeCallingClient() {
        Edge edge = new Edge("knows");
        edge.id("custom-id");
        edge.sourceId("1");
        edge.targetId("2");

        Assert.assertThrows(InvalidOperationException.class, () -> {
            this.manager.addEdge(edge);
        });
        Mockito.verifyNoInteractions(this.client);
    }
}
