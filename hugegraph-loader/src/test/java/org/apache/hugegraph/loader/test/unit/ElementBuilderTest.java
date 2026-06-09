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

package org.apache.hugegraph.loader.test.unit;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.apache.hugegraph.loader.builder.EdgeBuilder;
import org.apache.hugegraph.loader.builder.SchemaCache;
import org.apache.hugegraph.loader.builder.VertexBuilder;
import org.apache.hugegraph.loader.executor.LoadContext;
import org.apache.hugegraph.loader.executor.LoadOptions;
import org.apache.hugegraph.loader.mapping.EdgeMapping;
import org.apache.hugegraph.loader.mapping.InputStruct;
import org.apache.hugegraph.loader.mapping.VertexMapping;
import org.apache.hugegraph.loader.source.file.FileSource;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.apache.hugegraph.structure.graph.Edge;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.schema.EdgeLabel;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.apache.hugegraph.testutil.Assert;

import sun.misc.Unsafe;

public class ElementBuilderTest {

    @Test
    public void testVertexBuilderBuildsCustomizeStringVertex() {
        LoadContext context = this.context();
        InputStruct struct = this.struct();
        VertexMapping mapping = new VertexMapping("id", false);
        mapping.label("person");

        VertexBuilder builder = new VertexBuilder(context, struct, mapping);
        List<Vertex> vertices = builder.build(new String[]{"id", "name", "age"},
                                              new Object[]{"1", "marko", "29"});

        Assert.assertEquals(1, vertices.size());
        Vertex vertex = vertices.get(0);
        Assert.assertEquals("person", vertex.label());
        Assert.assertEquals("1", vertex.id());
        Assert.assertEquals("1", vertex.property("id"));
        Assert.assertEquals("marko", vertex.property("name"));
        Assert.assertEquals(29, vertex.property("age"));
    }

    @Test
    public void testVertexBuilderIgnoresNullableNullValue() {
        LoadContext context = this.context();
        InputStruct struct = this.struct();
        VertexMapping mapping = new VertexMapping(null, false);
        mapping.label("city");

        VertexBuilder builder = new VertexBuilder(context, struct, mapping);
        List<Vertex> vertices = builder.build(new String[]{"name", "country"},
                                              new Object[]{"Beijing", ""});

        Assert.assertEquals(1, vertices.size());
        Vertex vertex = vertices.get(0);
        Assert.assertEquals("Beijing", vertex.property("name"));
        Assert.assertFalse(vertex.properties().containsKey("country"));
    }

    @Test
    public void testVertexBuilderRejectsMissingCustomizeIdField() {
        LoadContext context = this.context();
        InputStruct struct = this.struct();
        VertexMapping mapping = new VertexMapping(null, false);
        mapping.label("person");

        Assert.assertThrows(IllegalStateException.class, () -> {
            new VertexBuilder(context, struct, mapping);
        });
    }

    @Test
    public void testEdgeBuilderBuildsEdgeWithConvertedProperty() {
        LoadContext context = this.context();
        InputStruct struct = this.struct();
        EdgeMapping mapping = new EdgeMapping(Arrays.asList("id"), false,
                                              Arrays.asList("friend_id"),
                                              false);
        mapping.label("knows");

        EdgeBuilder builder = new EdgeBuilder(context, struct, mapping);
        List<Edge> edges = builder.build(new String[]{"id", "friend_id", "since"},
                                         new Object[]{"1", "2", "2013"});

        Assert.assertEquals(1, edges.size());
        Edge edge = edges.get(0);
        Assert.assertEquals("knows", edge.label());
        Assert.assertEquals("1", edge.sourceId());
        Assert.assertEquals("2", edge.targetId());
        Assert.assertEquals("person", edge.sourceLabel());
        Assert.assertEquals("person", edge.targetLabel());
        Assert.assertEquals(2013, edge.property("since"));
    }

    @Test
    public void testEdgeBuilderRejectsMissingTargetField() {
        LoadContext context = this.context();
        InputStruct struct = this.struct();
        EdgeMapping mapping = new EdgeMapping(Arrays.asList("id"), false,
                                              Arrays.asList("friend_id"),
                                              false);
        mapping.label("knows");

        EdgeBuilder builder = new EdgeBuilder(context, struct, mapping);
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            builder.build(new String[]{"id", "since"}, new Object[]{"1", "2013"});
        });
    }

    @Test
    public void testSchemaCacheReportsEmptyState() {
        SchemaCache empty = new SchemaCache(Collections.emptyList(),
                                            Collections.emptyList(),
                                            Collections.emptyList());
        Assert.assertTrue(empty.isEmpty());
        Assert.assertFalse(this.schema().isEmpty());
    }

    private LoadContext context() {
        try {
            LoadContext context = (LoadContext) unsafe().allocateInstance(
                    LoadContext.class);
            setField(context, "options", new LoadOptions());
            setField(context, "schemaCache", this.schema());
            return context;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Object object, String name, Object value)
            throws ReflectiveOperationException {
        Field field = LoadContext.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private InputStruct struct() {
        FileSource source = new FileSource();
        InputStruct struct = new InputStruct(Collections.emptyList(),
                                             Collections.emptyList());
        struct.id("builder-test");
        struct.input(source);
        return struct;
    }

    private SchemaCache schema() {
        return new SchemaCache(
                Arrays.asList(this.text("id"), this.text("name"),
                              this.integer("age"), this.text("country"),
                              this.integer("since")),
                Arrays.asList(this.personLabel(), this.cityLabel()),
                Arrays.asList(this.knowsLabel()));
    }

    private PropertyKey text(String name) {
        return new PropertyKey.BuilderImpl(name, null).asText().build();
    }

    private PropertyKey integer(String name) {
        return new PropertyKey.BuilderImpl(name, null).asInt().build();
    }

    private VertexLabel personLabel() {
        return new VertexLabel.BuilderImpl("person", null)
                              .id(1L)
                              .idStrategy(IdStrategy.CUSTOMIZE_STRING)
                              .properties("id", "name", "age")
                              .build();
    }

    private VertexLabel cityLabel() {
        return new VertexLabel.BuilderImpl("city", null)
                              .id(2L)
                              .idStrategy(IdStrategy.PRIMARY_KEY)
                              .properties("name", "country")
                              .primaryKeys("name")
                              .nullableKeys("country")
                              .build();
    }

    private EdgeLabel knowsLabel() {
        return new EdgeLabel.BuilderImpl("knows", null)
                            .link("person", "person")
                            .properties("since")
                            .build();
    }
}
