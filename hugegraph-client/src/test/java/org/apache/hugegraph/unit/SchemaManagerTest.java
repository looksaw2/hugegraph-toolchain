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
import org.apache.hugegraph.driver.SchemaManager;
import org.apache.hugegraph.rest.RestHeaders;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.SchemaElement;
import org.apache.hugegraph.structure.constant.Cardinality;
import org.apache.hugegraph.structure.constant.DataType;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.apache.hugegraph.structure.schema.EdgeLabel;
import org.apache.hugegraph.structure.schema.IndexLabel;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class SchemaManagerTest extends BaseUnitTest {

    private RestClient client;
    private SchemaManager schema;

    @Before
    public void setup() {
        this.client = Mockito.mock(RestClient.class);
        Mockito.when(this.client.apiVersionLt(Mockito.anyString())).thenReturn(false);
        this.schema = new SchemaManager(this.client, "DEFAULT", "hugegraph");
    }

    @Test
    public void testBuilderEntriesBuildSchemaObjectsWithIdAndOptions() {
        PropertyKey propertyKey = this.schema.propertyKey(1L, "age")
                                            .asInt()
                                            .valueSingle()
                                            .build();
        VertexLabel vertexLabel = this.schema.vertexLabel(2L, "person")
                                            .usePrimaryKeyId()
                                            .properties("name", "age")
                                            .primaryKeys("name")
                                            .build();
        EdgeLabel edgeLabel = this.schema.edgeLabel(3L, "knows")
                                        .link("person", "person")
                                        .properties("date")
                                        .singleTime()
                                        .build();
        IndexLabel indexLabel = this.schema.indexLabel(4L, "personByAge")
                                          .onV("person")
                                          .by("age")
                                          .range()
                                          .build();

        Assert.assertEquals(1L, propertyKey.id());
        Assert.assertEquals(DataType.INT, propertyKey.dataType());
        Assert.assertEquals(Cardinality.SINGLE, propertyKey.cardinality());
        Assert.assertEquals(2L, vertexLabel.id());
        Assert.assertEquals(IdStrategy.PRIMARY_KEY, vertexLabel.idStrategy());
        Assert.assertTrue(vertexLabel.properties().containsAll(Arrays.asList("name", "age")));
        Assert.assertEquals(Collections.singletonList("name"), vertexLabel.primaryKeys());
        Assert.assertEquals(3L, edgeLabel.id());
        Assert.assertEquals("person", edgeLabel.sourceLabel());
        Assert.assertEquals("person", edgeLabel.targetLabel());
        Assert.assertEquals(4L, indexLabel.id());
        Assert.assertEquals("person", indexLabel.baseValue());
        Assert.assertEquals(Collections.singletonList("age"), indexLabel.indexFields());
    }

    @Test
    public void testAddPropertyKeySyncAndAsyncDelegateToApiAndParseTask() {
        PropertyKey propertyKey = this.schema.propertyKey("age").asInt().build();
        Mockito.when(this.client.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result("{\"property_key\":{\"name\":\"age\","
                                  + "\"data_type\":\"INT\","
                                  + "\"cardinality\":\"SINGLE\"},"
                                  + "\"task_id\":0}"))
               .thenReturn(result("{\"property_key\":{\"name\":\"age\","
                                  + "\"data_type\":\"INT\","
                                  + "\"cardinality\":\"SINGLE\"},"
                                  + "\"task_id\":18}"));
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);

        PropertyKey created = this.schema.addPropertyKey(propertyKey, 1L);
        long task = this.schema.addPropertyKeyAsync(propertyKey);

        Assert.assertEquals("age", created.name());
        Assert.assertEquals(18L, task);
        Mockito.verify(this.client, Mockito.times(2))
               .post(pathCaptor.capture(), Mockito.same(propertyKey));
        Assert.assertEquals("graphspaces/DEFAULT/graphs/hugegraph/schema/propertykeys",
                            pathCaptor.getAllValues().get(0));
        Mockito.verify(this.client, Mockito.never())
               .get("graphspaces/DEFAULT/graphs/hugegraph/tasks", "0");
    }

    @Test
    public void testRemoveIndexLabelSyncAndAsyncDelegateToTaskApi() {
        Mockito.when(this.client.delete(Mockito.anyString(), Mockito.anyString()))
               .thenReturn(result("{\"task_id\":0}"))
               .thenReturn(result("{\"task_id\":28}"));
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);

        this.schema.removeIndexLabel("personByAge", 1L);
        long task = this.schema.removeIndexLabelAsync("personByAge");

        Assert.assertEquals(28L, task);
        Mockito.verify(this.client, Mockito.times(2))
               .delete(pathCaptor.capture(), idCaptor.capture());
        Assert.assertEquals("graphspaces/DEFAULT/graphs/hugegraph/schema/indexlabels",
                            pathCaptor.getValue());
        Assert.assertEquals("personByAge", idCaptor.getValue());
    }

    @Test
    public void testSchemaAndGroovySchemaDelegateAndParseDefaults() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.anyString(), paramsCaptor.capture()))
               .thenReturn(result("{\"propertykeys\":[{\"name\":\"age\","
                                  + "\"data_type\":\"INT\","
                                  + "\"cardinality\":\"SINGLE\"}],"
                                  + "\"vertexlabels\":[],"
                                  + "\"edgelabels\":[],"
                                  + "\"indexlabels\":[]}"))
               .thenReturn(result("{\"schema\":\"schema.propertyKey('age')\"}"))
               .thenReturn(result("{\"schema\":\"groovy\",\"format\":\"groovy\"}"));

        Map<String, List<SchemaElement>> schema = this.schema.getSchema();
        String groovy = this.schema.getGroovySchema();
        Map<String, Object> groovyMap = this.schema.getGroovySchema("groovy", true);

        Assert.assertEquals(1, schema.get("propertykeys").size());
        Assert.assertEquals("schema.propertyKey('age')", groovy);
        Assert.assertEquals("groovy", groovyMap.get("schema"));
        Assert.assertEquals("groovy", paramsCaptor.getValue().get("format"));
        Assert.assertEquals(true, paramsCaptor.getValue().get("attachidflag"));
    }

    @Test
    public void testVertexAndEdgeAndIndexCrudDelegates() {
        Mockito.when(this.client.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result("{\"name\":\"person\","
                                  + "\"id_strategy\":\"DEFAULT\","
                                  + "\"properties\":[\"name\"],"
                                  + "\"primary_keys\":[],"
                                  + "\"nullable_keys\":[]}"))
               .thenReturn(result("{\"name\":\"knows\","
                                  + "\"source_label\":\"person\","
                                  + "\"target_label\":\"person\","
                                  + "\"frequency\":\"SINGLE\","
                                  + "\"properties\":[],"
                                  + "\"sort_keys\":[],"
                                  + "\"nullable_keys\":[]}"))
               .thenReturn(result("{\"index_label\":{\"name\":\"personByName\","
                                  + "\"base_type\":\"VERTEX_LABEL\","
                                  + "\"base_value\":\"person\","
                                  + "\"index_type\":\"SECONDARY\","
                                  + "\"fields\":[\"name\"]},"
                                  + "\"task_id\":0}"));

        VertexLabel vertexLabel = this.schema.addVertexLabel(
                this.schema.vertexLabel("person").properties("name").build());
        EdgeLabel edgeLabel = this.schema.addEdgeLabel(
                this.schema.edgeLabel("knows").link("person", "person").build());
        IndexLabel indexLabel = this.schema.addIndexLabel(
                this.schema.indexLabel("personByName").onV("person").by("name").secondary().build(),
                1L);

        Assert.assertEquals("person", vertexLabel.name());
        Assert.assertEquals("knows", edgeLabel.name());
        Assert.assertEquals("personByName", indexLabel.name());
        Mockito.verify(this.client).post(Mockito.endsWith("/schema/vertexlabels"),
                                         Mockito.any(VertexLabel.class));
        Mockito.verify(this.client).post(Mockito.endsWith("/schema/edgelabels"),
                                         Mockito.any(EdgeLabel.class));
        Mockito.verify(this.client).post(Mockito.endsWith("/schema/indexlabels"),
                                         Mockito.any(IndexLabel.class));
    }

    private static RestResult result(String content) {
        return new RestResult(200, content, new RestHeaders());
    }
}
