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

import org.apache.hugegraph.api.schema.IndexLabelAPI;
import org.apache.hugegraph.api.schema.PropertyKeyAPI;
import org.apache.hugegraph.api.traverser.VerticesAPI;
import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.exception.NotSupportException;
import org.apache.hugegraph.rest.RestHeaders;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.constant.Cardinality;
import org.apache.hugegraph.structure.constant.DataType;
import org.apache.hugegraph.structure.graph.Shard;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.graph.Vertices;
import org.apache.hugegraph.structure.schema.IndexLabel;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class SchemaAndTraverserApiRequestTest extends BaseUnitTest {

    private RestClient client;

    @Before
    public void setup() {
        this.client = Mockito.mock(RestClient.class);
    }

    @Test
    public void testPropertyKeyListByNamesChecksVersionAndPassesNamesParam() {
        PropertyKeyAPI api = new PropertyKeyAPI(this.client, "DEFAULT", "hugegraph");
        RestResult result = new RestResult(200, "{\"propertykeys\":[]}",
                                           new RestHeaders());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.anyString(), paramsCaptor.capture()))
               .thenReturn(result);

        List<PropertyKey> propertyKeys = api.list(List.of("name", "age"));

        Assert.assertTrue(propertyKeys.isEmpty());
        Mockito.verify(this.client).checkApiVersion("0.48", "getting schema by names");
        Assert.assertEquals(List.of("name", "age"),
                            paramsCaptor.getValue().get("names"));
    }

    @Test
    public void testPropertyKeyListByNamesRejectsEmptyNames() {
        PropertyKeyAPI api = new PropertyKeyAPI(this.client, "DEFAULT", "hugegraph");

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            api.list(List.of());
        });
    }

    @Test
    public void testPropertyKeyClearRejectsLegacyApiVersion() {
        Mockito.when(this.client.apiVersionLt("0.65")).thenReturn(true);
        PropertyKeyAPI api = new PropertyKeyAPI(this.client, "DEFAULT", "hugegraph");
        PropertyKey propertyKey = new PropertyKey.BuilderImpl("name", null)
                                  .dataType(DataType.TEXT)
                                  .cardinality(Cardinality.SINGLE)
                                  .build();

        Assert.assertThrows(NotSupportException.class, () -> {
            api.clear(propertyKey);
        });
        Mockito.verify(this.client, Mockito.never())
               .put(Mockito.anyString(), Mockito.anyString(), Mockito.any(),
                    Mockito.anyMap());
    }

    @Test
    public void testIndexLabelAppendUsesActionParameter() {
        Mockito.when(this.client.apiVersionLt(Mockito.anyString())).thenReturn(false);
        IndexLabelAPI api = new IndexLabelAPI(this.client, "DEFAULT", "hugegraph");
        RestResult result = new RestResult(200, "{"
                                               + "\"name\":\"personByAge\","
                                               + "\"base_type\":\"VERTEX_LABEL\","
                                               + "\"base_value\":\"person\","
                                               + "\"index_type\":\"SECONDARY\","
                                               + "\"fields\":[\"age\"]"
                                               + "}", new RestHeaders());
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.put(pathCaptor.capture(), idCaptor.capture(),
                                     Mockito.any(), paramsCaptor.capture()))
               .thenReturn(result);

        IndexLabel indexLabel = new IndexLabel.BuilderImpl("personByAge", null)
                                .onV("person")
                                .secondary()
                                .by("age")
                                .build();
        IndexLabel appended = api.append(indexLabel);

        Assert.assertEquals("personByAge", appended.name());
        Assert.assertEquals("graphspaces/DEFAULT/graphs/hugegraph/schema/indexlabels",
                            pathCaptor.getValue());
        Assert.assertEquals("personByAge", idCaptor.getValue());
        Assert.assertEquals("append", paramsCaptor.getValue().get("action"));
    }

    @Test
    public void testVerticesApiListFormatsIdsAndRejectsEmptyIds() {
        VerticesAPI api = new VerticesAPI(this.client, "DEFAULT", "hugegraph");
        RestResult result = new RestResult(200, "{"
                                               + "\"vertices\":[{\"id\":\"1\","
                                               + "\"label\":\"person\","
                                               + "\"properties\":{}}]"
                                               + "}", new RestHeaders());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.anyString(), paramsCaptor.capture()))
               .thenReturn(result);

        List<Vertex> vertices = api.list(List.of("1", 2));

        Assert.assertEquals(1, vertices.size());
        Assert.assertEquals("1", vertices.get(0).id());
        Assert.assertEquals(List.of("\"1\"", "2"),
                            paramsCaptor.getValue().get("ids"));
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            api.list(List.of());
        });
    }

    @Test
    public void testVerticesApiScanPassesShardPagingParameters() {
        VerticesAPI api = new VerticesAPI(this.client, "DEFAULT", "hugegraph");
        RestResult result = new RestResult(200, "{\"vertices\":[],\"page\":\"next\"}",
                                           new RestHeaders());
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(pathCaptor.capture(), paramsCaptor.capture()))
               .thenReturn(result);

        Vertices vertices = api.scan(new Shard("a", "z", 0L), "page-token", 100L);

        Assert.assertEquals("next", vertices.page());
        Assert.assertEquals("graphspaces/DEFAULT/graphs/hugegraph/traversers/vertices/scan",
                            pathCaptor.getValue());
        Assert.assertEquals("a", paramsCaptor.getValue().get("start"));
        Assert.assertEquals("z", paramsCaptor.getValue().get("end"));
        Assert.assertEquals("page-token", paramsCaptor.getValue().get("page"));
        Assert.assertEquals(100L, paramsCaptor.getValue().get("page_limit"));
    }
}
