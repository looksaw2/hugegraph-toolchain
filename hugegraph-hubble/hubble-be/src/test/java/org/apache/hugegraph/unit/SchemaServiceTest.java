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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.SchemaManager;
import org.apache.hugegraph.entity.schema.ConflictCheckEntity;
import org.apache.hugegraph.entity.schema.ConflictDetail;
import org.apache.hugegraph.entity.schema.ConflictStatus;
import org.apache.hugegraph.entity.schema.EdgeLabelEntity;
import org.apache.hugegraph.entity.schema.EdgeLabelStyle;
import org.apache.hugegraph.entity.schema.EdgeLabelUpdateEntity;
import org.apache.hugegraph.entity.schema.Property;
import org.apache.hugegraph.entity.schema.PropertyIndex;
import org.apache.hugegraph.entity.schema.PropertyKeyEntity;
import org.apache.hugegraph.entity.schema.SchemaType;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelStyle;
import org.apache.hugegraph.entity.schema.VertexLabelUpdateEntity;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.exception.ServerException;
import org.apache.hugegraph.service.HugeClientPoolService;
import org.apache.hugegraph.service.schema.EdgeLabelService;
import org.apache.hugegraph.service.schema.PropertyIndexService;
import org.apache.hugegraph.service.schema.PropertyKeyService;
import org.apache.hugegraph.service.schema.SchemaService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.constant.Cardinality;
import org.apache.hugegraph.structure.constant.DataType;
import org.apache.hugegraph.structure.constant.Frequency;
import org.apache.hugegraph.structure.constant.HugeType;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.apache.hugegraph.structure.constant.IndexType;
import org.apache.hugegraph.structure.schema.EdgeLabel;
import org.apache.hugegraph.structure.schema.IndexLabel;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class SchemaServiceTest {

    private static final int CONN_ID = 1;

    private HugeClient client;
    private SchemaManager schema;
    private PropertyKeyService pkService;
    private PropertyIndexService piService;
    private VertexLabelService vlService;
    private EdgeLabelService elService;

    @Before
    public void setup() {
        this.client = Mockito.mock(HugeClient.class);
        this.schema = Mockito.mock(SchemaManager.class);
        Mockito.when(this.client.schema()).thenReturn(this.schema);
        Mockito.when(this.schema.propertyKey(Mockito.anyString()))
               .thenAnswer(invocation -> new PropertyKey.BuilderImpl(
                       invocation.getArgument(0), this.schema));
        Mockito.when(this.schema.vertexLabel(Mockito.anyString()))
               .thenAnswer(invocation -> new VertexLabel.BuilderImpl(
                       invocation.getArgument(0), this.schema));
        Mockito.when(this.schema.edgeLabel(Mockito.anyString()))
               .thenAnswer(invocation -> new EdgeLabel.BuilderImpl(
                       invocation.getArgument(0), this.schema));
        Mockito.when(this.schema.indexLabel(Mockito.anyString()))
               .thenAnswer(invocation -> new IndexLabel.BuilderImpl(
                       invocation.getArgument(0), this.schema));

        HugeClientPoolService pool = new HugeClientPoolService();
        pool.put(CONN_ID, this.client);

        this.pkService = Mockito.spy(new PropertyKeyService());
        this.piService = Mockito.spy(new PropertyIndexService());
        this.vlService = Mockito.spy(new VertexLabelService());
        this.elService = Mockito.spy(new EdgeLabelService());

        injectPool(this.pkService, pool);
        injectPool(this.piService, pool);
        injectPool(this.vlService, pool);
        injectPool(this.elService, pool);
        ReflectionTestUtils.setField(this.vlService, "pkService", this.pkService);
        ReflectionTestUtils.setField(this.vlService, "piService", this.piService);
        ReflectionTestUtils.setField(this.elService, "pkService", this.pkService);
        ReflectionTestUtils.setField(this.elService, "piService", this.piService);
        ReflectionTestUtils.setField(this.elService, "vlService", this.vlService);
    }

    @Test
    public void testPropertyKeyListGetCheckExistAndRemove() {
        PropertyKey name = propertyKey("name", DataType.TEXT);
        Mockito.doReturn(Collections.singletonList(name))
               .when(this.schema).getPropertyKeys();
        Mockito.doReturn(name).when(this.schema).getPropertyKey("name");

        List<PropertyKeyEntity> results = this.pkService.list(CONN_ID);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("name", results.get(0).getName());
        Assert.assertEquals(DataType.TEXT, results.get(0).getDataType());

        Assert.assertEquals("name", this.pkService.get("name", CONN_ID).getName());
        this.pkService.checkExist("name", CONN_ID);
        expect(ExternalException.class,
               () -> this.pkService.checkNotExist("name", CONN_ID));

        this.pkService.remove("name", CONN_ID);
        Mockito.verify(this.schema).removePropertyKey("name");
    }

    @Test
    public void testPropertyKeyNotFoundAllowsCheckNotExist() {
        ServerException notFound = serverException(Constant.STATUS_NOT_FOUND);
        Mockito.doThrow(notFound).when(this.schema).getPropertyKey("missing");

        ExternalException exception = expect(ExternalException.class,
                                             () -> this.pkService.get("missing",
                                                                      CONN_ID));

        Assert.assertSame(notFound, exception.getCause());
        this.pkService.checkNotExist("missing", CONN_ID);
    }

    @Test
    public void testPropertyKeyAddConvertsEntityAndCheckUsingScansLabels() {
        PropertyKeyEntity entity = pk("age", DataType.INT);
        Mockito.doReturn(Collections.singletonList(vertexLabel("person", "name")))
               .when(this.schema).getVertexLabels();
        Mockito.doReturn(Collections.singletonList(edgeLabel("knows", "person",
                                                            "person", "age")))
               .when(this.schema).getEdgeLabels();

        this.pkService.add(entity, CONN_ID);
        ArgumentCaptor<PropertyKey> captor =
                ArgumentCaptor.forClass(PropertyKey.class);
        Mockito.verify(this.schema).addPropertyKey(captor.capture());
        Assert.assertEquals("age", captor.getValue().name());
        Assert.assertEquals(DataType.INT, captor.getValue().dataType());

        Assert.assertTrue(this.pkService.checkUsing("age", CONN_ID));
        Assert.assertFalse(this.pkService.checkUsing("unused", CONN_ID));
    }

    @Test
    public void testPropertyKeyConflictAndReuseUseStatuses() {
        Mockito.doReturn(Collections.singletonList(pk("name", DataType.TEXT)))
               .when(this.pkService).list(CONN_ID);
        ConflictDetail detail = new ConflictDetail(SchemaType.PROPERTY_KEY);

        this.pkService.checkConflict(Arrays.asList(pk("name", DataType.TEXT),
                                                   pk("age", DataType.INT),
                                                   pk("age", DataType.LONG)),
                                     detail, CONN_ID, true);

        Assert.assertEquals(ConflictStatus.EXISTED,
                            detail.getPkConflicts().get(0).getStatus());
        Assert.assertEquals(ConflictStatus.DUPNAME,
                            detail.getPkConflicts().get(1).getStatus());
        Assert.assertEquals(ConflictStatus.DUPNAME,
                            detail.getPkConflicts().get(2).getStatus());
        expect(ExternalException.class,
               () -> this.pkService.reuse(detail, CONN_ID));
    }

    @Test
    public void testVertexLabelListAddRemoveAndCheckUsing() {
        VertexLabel person = vertexLabel("person", "name");
        IndexLabel index = indexLabel("personByName", true, "person", "name");
        Mockito.doReturn(Collections.singletonList(person))
               .when(this.schema).getVertexLabels();
        Mockito.doReturn(Collections.singletonList(index))
               .when(this.schema).getIndexLabels();
        Mockito.doReturn(Collections.singletonList(edgeLabel("knows", "person",
                                                            "software", "date")))
               .when(this.schema).getEdgeLabels();

        List<VertexLabelEntity> labels = this.vlService.list(CONN_ID);
        Assert.assertEquals("person", labels.get(0).getName());
        Assert.assertEquals(IdStrategy.PRIMARY_KEY, labels.get(0).getIdStrategy());
        Assert.assertEquals(1, labels.get(0).getPropertyIndexes().size());
        Assert.assertTrue(this.vlService.checkUsing("person", CONN_ID));
        Assert.assertFalse(this.vlService.checkUsing("project", CONN_ID));

        VertexLabelEntity entity = person();
        this.vlService.add(entity, CONN_ID);
        Mockito.verify(this.schema).addVertexLabel(Mockito.any(VertexLabel.class));
        Mockito.verify(this.piService).addBatch(Mockito.anyList(),
                                                Mockito.same(this.client));

        this.vlService.remove("person", CONN_ID);
        Mockito.verify(this.schema).removeVertexLabelAsync("person");
    }

    @Test
    public void testVertexLabelUpdateRejectsInvalidIndexChangesAndSucceeds() {
        Mockito.doReturn(Collections.singletonList(
                indexLabel("personByName", true, "person", "name")))
               .when(this.schema).getIndexLabels();

        VertexLabelUpdateEntity appendExisting = new VertexLabelUpdateEntity();
        appendExisting.setName("person");
        appendExisting.setAppendPropertyIndexes(Collections.singletonList(
                propertyIndex("person", SchemaType.VERTEX_LABEL, "personByName",
                              "name")));

        expect(ExternalException.class,
               () -> this.vlService.update(appendExisting, CONN_ID));

        VertexLabelUpdateEntity removeMissing = new VertexLabelUpdateEntity();
        removeMissing.setName("person");
        removeMissing.setRemovePropertyIndexes(
                Collections.singletonList("personByAge"));
        expect(ExternalException.class,
               () -> this.vlService.update(removeMissing, CONN_ID));

        VertexLabelUpdateEntity update = new VertexLabelUpdateEntity();
        update.setName("person");
        update.setAppendProperties(ImmutableSet.of(new Property("age", true)));
        update.setAppendPropertyIndexes(Collections.singletonList(
                propertyIndex("person", SchemaType.VERTEX_LABEL, "personByAge",
                              "age")));
        update.setRemovePropertyIndexes(Collections.singletonList("personByName"));
        update.setStyle(new VertexLabelStyle());

        this.vlService.update(update, CONN_ID);

        Mockito.verify(this.schema).appendVertexLabel(Mockito.any(VertexLabel.class));
        Mockito.verify(this.piService)
               .addBatch(Mockito.anyList(), Mockito.same(this.client));
        Mockito.verify(this.piService).removeBatch(
                Collections.singletonList("personByName"), this.client);
    }

    @Test
    public void testVertexLabelConflictMarksDependencyConflict() {
        ConflictDetail detail = new ConflictDetail(SchemaType.VERTEX_LABEL);
        detail.add(pk("name", DataType.TEXT), ConflictStatus.DUPNAME);

        this.vlService.checkConflict(Collections.singletonList(person()), detail,
                                     CONN_ID, false);

        Assert.assertEquals(ConflictStatus.DEP_CONFLICT,
                            detail.getVlConflicts().get(0).getStatus());
    }

    @Test
    public void testVertexLabelReuseRollsBackPropertyKeysWhenLabelFails() {
        PropertyKeyService pk = Mockito.mock(PropertyKeyService.class);
        PropertyIndexService pi = Mockito.mock(PropertyIndexService.class);
        VertexLabelService service = Mockito.spy(new VertexLabelService());
        injectPool(service, ReflectionTestUtils.getField(this.pkService,
                                                         "poolService"));
        ReflectionTestUtils.setField(service, "pkService", pk);
        ReflectionTestUtils.setField(service, "piService", pi);

        ConflictDetail detail = new ConflictDetail(SchemaType.VERTEX_LABEL);
        List<PropertyKey> keys = Collections.singletonList(
                propertyKey("name", DataType.TEXT));
        Mockito.when(pk.filter(detail, this.client)).thenReturn(keys);
        Mockito.when(pi.filter(detail, this.client)).thenReturn(Collections.emptyList());
        Mockito.doReturn(Collections.singletonList(vertexLabel("person", "name")))
               .when(service).filter(detail, this.client);
        Mockito.doThrow(new RuntimeException("boom")).when(service)
               .addBatch(Mockito.anyList(), Mockito.same(this.client));

        expect(ExternalException.class, () -> service.reuse(detail, CONN_ID));

        Mockito.verify(pk).addBatch(keys, this.client);
        Mockito.verify(pk).removeBatch(keys, this.client);
    }

    @Test
    public void testEdgeLabelListAddRemoveAndUpdate() {
        EdgeLabel edge = edgeLabel("knows", "person", "person", "date");
        IndexLabel index = indexLabel("knowsByDate", false, "knows", "date");
        Mockito.doReturn(Collections.singletonList(edge))
               .when(this.schema).getEdgeLabels();
        Mockito.doReturn(Collections.singletonList(index))
               .when(this.schema).getIndexLabels();

        List<EdgeLabelEntity> labels = this.elService.list(CONN_ID);
        Assert.assertEquals("knows", labels.get(0).getName());
        Assert.assertTrue(labels.get(0).isLinkMultiTimes());
        Assert.assertEquals(1, labels.get(0).getPropertyIndexes().size());

        this.elService.add(knows(), CONN_ID);
        Mockito.verify(this.schema).addEdgeLabel(Mockito.any(EdgeLabel.class));
        Mockito.verify(this.piService).addBatch(Mockito.anyList(),
                                                Mockito.same(this.client));

        EdgeLabelUpdateEntity update = new EdgeLabelUpdateEntity();
        update.setName("knows");
        update.setAppendProperties(ImmutableSet.of(new Property("weight", true)));
        update.setAppendPropertyIndexes(Collections.singletonList(
                propertyIndex("knows", SchemaType.EDGE_LABEL, "knowsByWeight",
                              "weight")));
        update.setRemovePropertyIndexes(Collections.singletonList("knowsByDate"));
        update.setStyle(new EdgeLabelStyle());
        this.elService.update(update, CONN_ID);

        Mockito.verify(this.schema).appendEdgeLabel(Mockito.any(EdgeLabel.class));
        Mockito.verify(this.piService, Mockito.times(2))
               .addBatch(Mockito.anyList(), Mockito.same(this.client));
        Mockito.verify(this.piService).removeBatch(
                Collections.singletonList("knowsByDate"), this.client);

        this.elService.remove("knows", CONN_ID);
        Mockito.verify(this.schema).removeEdgeLabelAsync("knows");
    }

    @Test
    public void testEdgeLabelConflictHonorsDependencyConflicts() {
        ConflictCheckEntity entity = new ConflictCheckEntity();
        entity.setElEntities(Collections.singletonList(knows()));
        entity.setVlEntities(Collections.emptyList());
        Mockito.doReturn(Collections.emptyList()).when(this.elService).list(CONN_ID);
        Mockito.doAnswer(invocation -> {
            ConflictDetail detail = invocation.getArgument(1);
            detail.add(person(), ConflictStatus.DUPNAME);
            return null;
        }).when(this.vlService).checkConflict(Mockito.anyList(),
                                             Mockito.any(ConflictDetail.class),
                                             Mockito.eq(CONN_ID),
                                             Mockito.eq(false));

        ConflictDetail detail = this.elService.checkConflict(entity, CONN_ID,
                                                             false);

        Assert.assertEquals(ConflictStatus.DEP_CONFLICT,
                            detail.getElConflicts().get(0).getStatus());
    }

    @Test
    public void testPropertyIndexListPagingAndConflictStatus() {
        IndexLabel personByName = indexLabel("personByName", true, "person",
                                             "name");
        IndexLabel softwareByPrice = indexLabel("softwareByPrice", true,
                                                "software", "price");
        IndexLabel knowsByDate = indexLabel("knowsByDate", false, "knows",
                                            "date");
        Mockito.doReturn(Arrays.asList(softwareByPrice, knowsByDate,
                                       personByName))
               .when(this.schema).getIndexLabels();
        Mockito.doReturn(personByName).when(this.schema)
               .getIndexLabel("personByName");
        ServerException notFound = serverException(Constant.STATUS_NOT_FOUND);
        Mockito.doThrow(notFound).when(this.schema).getIndexLabel("missing");

        IPage<PropertyIndex> page = this.piService.list(CONN_ID,
                                                        HugeType.VERTEX_LABEL,
                                                        1, 10);
        Assert.assertEquals(2L, page.getTotal());
        Assert.assertEquals("person", page.getRecords().get(0).getOwner());
        Assert.assertEquals("software", page.getRecords().get(1).getOwner());

        IPage<PropertyIndex> filtered = this.piService.list(
                CONN_ID, HugeType.VERTEX_LABEL, "price", 1, 10);
        Assert.assertEquals(1L, filtered.getTotal());
        Assert.assertEquals("softwareByPrice", filtered.getRecords().get(0).getName());

        Assert.assertEquals(ConflictStatus.PASSED,
                            this.piService.checkConflict(
                                    propertyIndex("person", SchemaType.VERTEX_LABEL,
                                                  "missing", "name"),
                                    CONN_ID));
        Assert.assertEquals(ConflictStatus.EXISTED,
                            this.piService.checkConflict(
                                    propertyIndex("person", SchemaType.VERTEX_LABEL,
                                                  "personByName", "name"),
                                    CONN_ID));
        Assert.assertEquals(ConflictStatus.DUPNAME,
                            this.piService.checkConflict(
                                    propertyIndex("person", SchemaType.VERTEX_LABEL,
                                                  "personByName", "age"),
                                    CONN_ID));
    }

    @Test
    public void testPropertyIndexConflictAndBatchOperations() {
        Mockito.doReturn(Collections.singletonList(
                propertyIndex("person", SchemaType.VERTEX_LABEL, "personByName",
                              "name")))
               .when(this.piService).list(CONN_ID);
        ConflictDetail detail = new ConflictDetail(SchemaType.PROPERTY_INDEX);
        detail.add(pk("name", DataType.TEXT), ConflictStatus.DUPNAME);

        this.piService.checkConflict(Arrays.asList(
                propertyIndex("person", SchemaType.VERTEX_LABEL, "personByName",
                              "name"),
                propertyIndex("person", SchemaType.VERTEX_LABEL, "personByAge",
                              "age")),
                                     detail, CONN_ID, false);

        Assert.assertEquals(ConflictStatus.DEP_CONFLICT,
                            detail.getPiConflicts().get(0).getStatus());
        Assert.assertEquals(ConflictStatus.PASSED,
                            detail.getPiConflicts().get(1).getStatus());

        Mockito.doReturn(11L).when(this.schema)
               .addIndexLabelAsync(Mockito.any(IndexLabel.class));
        Mockito.doReturn(12L).when(this.schema).removeIndexLabelAsync("personByAge");

        List<Long> added = this.piService.addBatch(
                Collections.singletonList(indexLabel("personByAge", true,
                                                    "person", "age")),
                this.client);
        List<Long> removed = this.piService.removeBatch(
                Collections.singletonList("personByAge"), this.client);

        Assert.assertEquals(Collections.singletonList(11L), added);
        Assert.assertEquals(Collections.singletonList(12L), removed);
    }

    @Test
    public void testSchemaServiceStaticHelpers() {
        VertexLabel person = vertexLabel("person", "name", "age");
        IndexLabel personByAge = indexLabel("personByAge", true, "person", "age");
        Set<Property> properties = SchemaService.collectProperties(person);
        List<PropertyIndex> indexes = SchemaService.collectPropertyIndexes(
                person, Collections.singletonList(personByAge));

        Assert.assertTrue(properties.contains(new Property("name", false)));
        Assert.assertTrue(properties.contains(new Property("age", true)));
        Assert.assertEquals("personByAge", indexes.get(0).getName());
        Assert.assertArrayEquals(new String[]{"name", "age"},
                                 SchemaService.toStringArray(
                                         Arrays.asList("name", "age")));
        Assert.assertEquals(1L, SchemaService.getCreateTime(person).getTime());

        AtomicInteger adds = new AtomicInteger();
        SchemaService.addBatch(Collections.singletonList(person), this.client,
                               (BiConsumer<HugeClient, VertexLabel>)
                               (client, schema) -> adds.incrementAndGet(),
                               SchemaType.VERTEX_LABEL);
        Assert.assertEquals(1, adds.get());
        Assert.assertTrue(person.userdata()
                                .get(SchemaService.USER_KEY_CREATE_TIME)
                                instanceof Date);

        List<Long> removes = SchemaService.removeBatch(
                Collections.singletonList("person"), this.client,
                (client, name) -> 7L, SchemaType.VERTEX_LABEL);
        Assert.assertEquals(Collections.singletonList(7L), removes);
    }

    @SuppressWarnings("unchecked")
    private static void injectPool(SchemaService service, Object pool) {
        ReflectionTestUtils.setField(service, "poolService", pool);
    }

    private static PropertyKeyEntity pk(String name, DataType dataType) {
        return PropertyKeyEntity.builder()
                                .name(name)
                                .dataType(dataType)
                                .cardinality(Cardinality.SINGLE)
                                .createTime(new Date(1L))
                                .build();
    }

    private static VertexLabelEntity person() {
        return VertexLabelEntity.builder()
                                .name("person")
                                .idStrategy(IdStrategy.PRIMARY_KEY)
                                .properties(ImmutableSet.of(
                                        new Property("name", false)))
                                .primaryKeys(ImmutableList.of("name"))
                                .propertyIndexes(Collections.emptyList())
                                .openLabelIndex(true)
                                .style(new VertexLabelStyle())
                                .createTime(new Date(1L))
                                .build();
    }

    private static EdgeLabelEntity knows() {
        return EdgeLabelEntity.builder()
                              .name("knows")
                              .sourceLabel("person")
                              .targetLabel("person")
                              .linkMultiTimes(true)
                              .properties(ImmutableSet.of(new Property("date",
                                                                        false)))
                              .sortKeys(ImmutableList.of("date"))
                              .propertyIndexes(Collections.emptyList())
                              .openLabelIndex(true)
                              .style(new EdgeLabelStyle())
                              .createTime(new Date(1L))
                              .build();
    }

    private PropertyKey propertyKey(String name, DataType type) {
        return this.schema.propertyKey(name)
                          .dataType(type)
                          .cardinality(Cardinality.SINGLE)
                          .userdata(SchemaService.USER_KEY_CREATE_TIME, 1L)
                          .build();
    }

    private VertexLabel vertexLabel(String name, String... properties) {
        VertexLabel label = this.schema.vertexLabel(name)
                                      .idStrategy(IdStrategy.PRIMARY_KEY)
                                      .properties(properties)
                                      .primaryKeys(properties[0])
                                      .nullableKeys(nullable(properties))
                                      .enableLabelIndex(true)
                                      .userdata(SchemaService.USER_KEY_CREATE_TIME,
                                                1L)
                                      .build();
        return label;
    }

    private EdgeLabel edgeLabel(String name, String source, String target,
                                String... properties) {
        return this.schema.edgeLabel(name)
                          .sourceLabel(source)
                          .targetLabel(target)
                          .frequency(Frequency.MULTIPLE)
                          .properties(properties)
                          .sortKeys(properties[0])
                          .nullableKeys(nullable(properties))
                          .enableLabelIndex(true)
                          .userdata(SchemaService.USER_KEY_CREATE_TIME, 1L)
                          .build();
    }

    private IndexLabel indexLabel(String name, boolean vertex, String owner,
                                  String... fields) {
        return this.schema.indexLabel(name)
                          .on(vertex, owner)
                          .indexType(IndexType.SECONDARY)
                          .by(fields)
                          .build();
    }

    private static PropertyIndex propertyIndex(String owner, SchemaType ownerType,
                                               String name, String... fields) {
        return PropertyIndex.builder()
                            .owner(owner)
                            .ownerType(ownerType)
                            .name(name)
                            .type(IndexType.SECONDARY)
                            .fields(Arrays.asList(fields))
                            .build();
    }

    private static String[] nullable(String[] properties) {
        if (properties.length <= 1) {
            return new String[]{};
        }
        return Arrays.copyOfRange(properties, 1, properties.length);
    }

    private static ServerException serverException(int status) {
        ServerException exception = new ServerException("server error");
        exception.status(status);
        return exception;
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
