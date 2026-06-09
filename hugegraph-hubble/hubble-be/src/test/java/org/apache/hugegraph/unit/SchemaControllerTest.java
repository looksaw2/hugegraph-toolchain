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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hugegraph.controller.schema.EdgeLabelController;
import org.apache.hugegraph.controller.schema.PropertyKeyController;
import org.apache.hugegraph.controller.schema.SchemaController;
import org.apache.hugegraph.controller.schema.VertexLabelController;
import org.apache.hugegraph.entity.schema.ConflictCheckEntity;
import org.apache.hugegraph.entity.schema.EdgeLabelEntity;
import org.apache.hugegraph.entity.schema.EdgeLabelStyle;
import org.apache.hugegraph.entity.schema.EdgeLabelUpdateEntity;
import org.apache.hugegraph.entity.schema.Property;
import org.apache.hugegraph.entity.schema.PropertyKeyEntity;
import org.apache.hugegraph.entity.schema.UsingCheckEntity;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelStyle;
import org.apache.hugegraph.entity.schema.VertexLabelUpdateEntity;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.exception.InternalException;
import org.apache.hugegraph.service.schema.EdgeLabelService;
import org.apache.hugegraph.service.schema.PropertyIndexService;
import org.apache.hugegraph.service.schema.PropertyKeyService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.constant.Cardinality;
import org.apache.hugegraph.structure.constant.DataType;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

public class SchemaControllerTest {

    private static final int CONN_ID = 1;
    private static final int REUSED_CONN_ID = 2;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PropertyKeyService pkService;
    private PropertyIndexService piService;
    private VertexLabelService vlService;
    private EdgeLabelService elService;

    @Before
    public void setup() {
        this.pkService = Mockito.mock(PropertyKeyService.class);
        this.piService = Mockito.mock(PropertyIndexService.class);
        this.vlService = Mockito.mock(VertexLabelService.class);
        this.elService = Mockito.mock(EdgeLabelService.class);
    }

    @Test
    public void testDisplayInSchemaViewBuildsVerticesAndEdges() {
        SchemaController controller = new SchemaController();
        injectBaseServices(controller);

        Mockito.when(this.pkService.list(CONN_ID))
               .thenReturn(Arrays.asList(pk("name", DataType.TEXT),
                                         pk("since", DataType.INT)));
        Mockito.when(this.vlService.list(CONN_ID))
               .thenReturn(Collections.singletonList(person()));
        Mockito.when(this.elService.list(CONN_ID))
               .thenReturn(Collections.singletonList(knows()));

        JsonNode view = MAPPER.valueToTree(controller.displayInSchemaView(CONN_ID));

        JsonNode vertex = view.get("vertices").get(0);
        Assert.assertEquals("person", vertex.get("id").asText());
        Assert.assertEquals("person", vertex.get("label").asText());
        Assert.assertEquals("name", vertex.get("primary_keys").get(0).asText());
        Assert.assertEquals("text", vertex.get("properties").get("name").asText());

        JsonNode edge = view.get("edges").get(0);
        Assert.assertEquals("person-knows->person", edge.get("id").asText());
        Assert.assertEquals("knows", edge.get("label").asText());
        Assert.assertEquals("person", edge.get("source").asText());
        Assert.assertEquals("since", edge.get("sort_keys").get(0).asText());
        Assert.assertEquals("int", edge.get("properties").get("since").asText());
    }

    @Test(expected = InternalException.class)
    public void testDisplayInSchemaViewRejectsUnknownProperty() {
        SchemaController controller = new SchemaController();
        injectBaseServices(controller);

        Mockito.when(this.pkService.list(CONN_ID)).thenReturn(Collections.emptyList());
        Mockito.when(this.vlService.list(CONN_ID))
               .thenReturn(Collections.singletonList(person()));
        Mockito.when(this.elService.list(CONN_ID)).thenReturn(Collections.emptyList());

        controller.displayInSchemaView(CONN_ID);
    }

    @Test
    public void testListInPageFiltersAndOrdersByRelativity() {
        SchemaController controller = new SchemaController();
        List<PropertyKeyEntity> keys = Arrays.asList(
                pk("name", DataType.TEXT, 1L),
                pk("nickname", DataType.TEXT, 2L),
                pk("age", DataType.INT, 3L)
        );

        IPage<PropertyKeyEntity> page = controller.listInPage(id -> keys, CONN_ID,
                                                              "name", null, 1, 10);

        Assert.assertEquals(2L, page.getTotal());
        Assert.assertEquals("name", page.getRecords().get(0).getName());
        Assert.assertEquals("nickname", page.getRecords().get(1).getName());
    }

    @Test(expected = ExternalException.class)
    public void testListInPageRejectsInvalidNameOrder() {
        new SchemaController().listInPage(id -> Collections.emptyList(), CONN_ID,
                                          null, "invalid", 1, 10);
    }

    @Test
    public void testPropertyKeyCreateValidatesAndDelegates() {
        PropertyKeyController controller = propertyKeyController();
        PropertyKeyEntity entity = pk("name", DataType.TEXT);

        controller.create(CONN_ID, entity);

        Mockito.verify(this.pkService).checkNotExist("name", CONN_ID);
        Mockito.verify(this.pkService).add(entity, CONN_ID);
        Assert.assertNotNull(entity.getCreateTime());
    }

    @Test(expected = ExternalException.class)
    public void testPropertyKeyCreateRejectsExistingCreateTime() {
        PropertyKeyController controller = propertyKeyController();
        controller.create(CONN_ID, pk("name", DataType.TEXT, 1L));
    }

    @Test
    public void testPropertyKeyCheckUsingChecksEachName() {
        PropertyKeyController controller = propertyKeyController();
        Mockito.when(this.pkService.checkUsing("name", CONN_ID)).thenReturn(true);
        Mockito.when(this.pkService.checkUsing("age", CONN_ID)).thenReturn(false);

        Map<String, Boolean> result = controller.checkUsing(
                CONN_ID, UsingCheckEntity.builder()
                                         .names(Arrays.asList("name", "age"))
                                         .build());

        Assert.assertEquals(Boolean.TRUE, result.get("name"));
        Assert.assertEquals(Boolean.FALSE, result.get("age"));
        Mockito.verify(this.pkService).checkExist("name", CONN_ID);
        Mockito.verify(this.pkService).checkExist("age", CONN_ID);
    }

    @Test
    public void testVertexLabelCreateValidatesPropertiesAndDelegates() {
        VertexLabelController controller = vertexLabelController();
        VertexLabelEntity entity = person();

        controller.create(CONN_ID, entity);

        Mockito.verify(this.pkService).checkExist("name", CONN_ID);
        Mockito.verify(this.vlService).checkNotExist("person", CONN_ID);
        Mockito.verify(this.vlService).add(entity, CONN_ID);
        Assert.assertNotNull(entity.getCreateTime());
    }

    @Test(expected = ExternalException.class)
    public void testVertexLabelCreateRejectsNullablePrimaryKey() {
        VertexLabelController controller = vertexLabelController();
        VertexLabelEntity entity = VertexLabelEntity.builder()
                                                    .name("person")
                                                    .idStrategy(IdStrategy.PRIMARY_KEY)
                                                    .properties(props(prop("name", true)))
                                                    .primaryKeys(ImmutableList.of("name"))
                                                    .style(new VertexLabelStyle())
                                                    .build();

        controller.create(CONN_ID, entity);
    }

    @Test
    public void testVertexLabelUpdateSetsPathNameAndDelegates() {
        VertexLabelController controller = vertexLabelController();
        VertexLabelUpdateEntity entity = new VertexLabelUpdateEntity();
        entity.setAppendProperties(props(prop("age", true)));

        controller.update(CONN_ID, "person", entity);

        Assert.assertEquals("person", entity.getName());
        Mockito.verify(this.vlService).checkExist("person", CONN_ID);
        Mockito.verify(this.pkService).checkExist("age", CONN_ID);
        Mockito.verify(this.vlService).update(entity, CONN_ID);
    }

    @Test
    public void testVertexLabelCheckConflictCollectsReferencedSchemas() {
        VertexLabelController controller = vertexLabelController();
        ConflictCheckEntity entity = new ConflictCheckEntity();
        entity.setVlEntities(Collections.singletonList(person()));

        controller.checkConflicts(CONN_ID, REUSED_CONN_ID, entity);

        Mockito.verify(this.pkService).list(Collections.singleton("name"),
                                           REUSED_CONN_ID, false);
        Mockito.verify(this.piService).list(Collections.emptySet(), REUSED_CONN_ID,
                                           false);
        Mockito.verify(this.vlService).checkConflict(entity, CONN_ID, false);
    }

    @Test
    public void testEdgeLabelCreateValidatesRelationAndDelegates() {
        EdgeLabelController controller = edgeLabelController();
        EdgeLabelEntity entity = knows();

        controller.create(CONN_ID, entity);

        Mockito.verify(this.vlService, Mockito.times(2)).checkExist("person",
                                                                    CONN_ID);
        Mockito.verify(this.pkService).checkExist("since", CONN_ID);
        Mockito.verify(this.elService).checkNotExist("knows", CONN_ID);
        Mockito.verify(this.elService).add(entity, CONN_ID);
        Assert.assertNotNull(entity.getCreateTime());
    }

    @Test(expected = ExternalException.class)
    public void testEdgeLabelCreateRejectsSortKeyOutsideProperties() {
        EdgeLabelController controller = edgeLabelController();
        EdgeLabelEntity entity = EdgeLabelEntity.builder()
                                                .name("knows")
                                                .sourceLabel("person")
                                                .targetLabel("person")
                                                .linkMultiTimes(true)
                                                .properties(props(prop("since",
                                                                       false)))
                                                .sortKeys(ImmutableList.of("weight"))
                                                .style(new EdgeLabelStyle())
                                                .build();

        controller.create(CONN_ID, entity);
    }

    @Test
    public void testEdgeLabelUpdateSetsPathNameAndDelegates() {
        EdgeLabelController controller = edgeLabelController();
        EdgeLabelUpdateEntity entity = new EdgeLabelUpdateEntity();
        entity.setAppendProperties(props(prop("since", true)));

        controller.update(CONN_ID, "knows", entity);

        Assert.assertEquals("knows", entity.getName());
        Mockito.verify(this.elService).checkExist("knows", CONN_ID);
        Mockito.verify(this.pkService).checkExist("since", CONN_ID);
        Mockito.verify(this.elService).update(entity, CONN_ID);
    }

    @Test
    public void testEdgeLabelCheckConflictIncludesLinkedVertexSchemas() {
        EdgeLabelController controller = edgeLabelController();
        ConflictCheckEntity entity = new ConflictCheckEntity();
        entity.setElEntities(Collections.singletonList(knows()));
        Mockito.when(this.vlService.list(Mockito.anySet(), Mockito.eq(REUSED_CONN_ID),
                                         Mockito.eq(false)))
               .thenReturn(Collections.singletonList(person()));

        controller.checkConflict(CONN_ID, REUSED_CONN_ID, entity);

        Mockito.verify(this.pkService)
               .list(Mockito.argThat(names -> names.contains("name") &&
                                             names.contains("since")),
                     Mockito.eq(REUSED_CONN_ID), Mockito.eq(false));
        Mockito.verify(this.elService).checkConflict(entity, CONN_ID, false);
    }

    private PropertyKeyController propertyKeyController() {
        PropertyKeyController controller = new PropertyKeyController();
        ReflectionTestUtils.setField(controller, "service", this.pkService);
        return controller;
    }

    private VertexLabelController vertexLabelController() {
        VertexLabelController controller = new VertexLabelController();
        ReflectionTestUtils.setField(controller, "pkService", this.pkService);
        ReflectionTestUtils.setField(controller, "piService", this.piService);
        ReflectionTestUtils.setField(controller, "vlService", this.vlService);
        return controller;
    }

    private EdgeLabelController edgeLabelController() {
        EdgeLabelController controller = new EdgeLabelController();
        ReflectionTestUtils.setField(controller, "pkService", this.pkService);
        ReflectionTestUtils.setField(controller, "piService", this.piService);
        ReflectionTestUtils.setField(controller, "vlService", this.vlService);
        ReflectionTestUtils.setField(controller, "elService", this.elService);
        return controller;
    }

    private void injectBaseServices(SchemaController controller) {
        ReflectionTestUtils.setField(controller, "pkService", this.pkService);
        ReflectionTestUtils.setField(controller, "vlService", this.vlService);
        ReflectionTestUtils.setField(controller, "elService", this.elService);
    }

    private static PropertyKeyEntity pk(String name, DataType dataType) {
        return PropertyKeyEntity.builder()
                                .name(name)
                                .dataType(dataType)
                                .cardinality(Cardinality.SINGLE)
                                .build();
    }

    private static PropertyKeyEntity pk(String name, DataType dataType,
                                        long createTime) {
        return PropertyKeyEntity.builder()
                                .name(name)
                                .dataType(dataType)
                                .cardinality(Cardinality.SINGLE)
                                .createTime(new Date(createTime))
                                .build();
    }

    private static VertexLabelEntity person() {
        return VertexLabelEntity.builder()
                                .name("person")
                                .idStrategy(IdStrategy.PRIMARY_KEY)
                                .properties(props(prop("name", false)))
                                .primaryKeys(ImmutableList.of("name"))
                                .style(new VertexLabelStyle())
                                .build();
    }

    private static EdgeLabelEntity knows() {
        return EdgeLabelEntity.builder()
                              .name("knows")
                              .sourceLabel("person")
                              .targetLabel("person")
                              .linkMultiTimes(true)
                              .properties(props(prop("since", false)))
                              .sortKeys(ImmutableList.of("since"))
                              .style(new EdgeLabelStyle())
                              .build();
    }

    private static Property prop(String name, boolean nullable) {
        return Property.builder()
                       .name(name)
                       .nullable(nullable)
                       .build();
    }

    private static Set<Property> props(Property... properties) {
        return new LinkedHashSet<>(Arrays.asList(properties));
    }
}
