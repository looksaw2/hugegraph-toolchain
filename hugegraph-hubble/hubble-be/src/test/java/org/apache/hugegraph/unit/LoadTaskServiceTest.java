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
import java.util.LinkedHashSet;

import org.apache.hugegraph.entity.GraphConnection;
import org.apache.hugegraph.entity.load.EdgeMapping;
import org.apache.hugegraph.entity.load.FieldMappingItem;
import org.apache.hugegraph.entity.load.FileMapping;
import org.apache.hugegraph.entity.load.FileSetting;
import org.apache.hugegraph.entity.load.NullValues;
import org.apache.hugegraph.entity.load.VertexMapping;
import org.apache.hugegraph.entity.schema.EdgeLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.loader.mapping.InputStruct;
import org.apache.hugegraph.loader.mapping.LoadMapping;
import org.apache.hugegraph.service.load.LoadTaskService;
import org.apache.hugegraph.service.schema.EdgeLabelService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class LoadTaskServiceTest {

    private LoadTaskService service;
    private VertexLabelService vlService;
    private EdgeLabelService elService;

    @Before
    public void setup() {
        this.service = new LoadTaskService();
        this.vlService = Mockito.mock(VertexLabelService.class);
        this.elService = Mockito.mock(EdgeLabelService.class);
        ReflectionTestUtils.setField(this.service, "vlService", this.vlService);
        ReflectionTestUtils.setField(this.service, "elService", this.elService);
    }

    @Test
    public void testBuildLoadMappingUsesScalarIdsByDefault() {
        Mockito.when(this.vlService.get("person", 1))
               .thenReturn(VertexLabelEntity.builder()
                                            .name("person")
                                            .idStrategy(IdStrategy.CUSTOMIZE_STRING)
                                            .build());
        Mockito.when(this.elService.get("knows", 1))
               .thenReturn(EdgeLabelEntity.builder()
                                          .name("knows")
                                          .sourceLabel("person")
                                          .targetLabel("person")
                                          .build());

        LoadMapping mapping = ReflectionTestUtils.invokeMethod(
                this.service, "buildLoadMapping", connection(), fileMapping());
        InputStruct struct = mapping.structs().get(0);

        Assert.assertFalse(struct.vertices().get(0).unfold());
        Assert.assertFalse(struct.edges().get(0).unfoldSource());
        Assert.assertFalse(struct.edges().get(0).unfoldTarget());
    }

    private static GraphConnection connection() {
        return GraphConnection.builder()
                              .id(1)
                              .graph("hugegraph")
                              .host("127.0.0.1")
                              .port(8080)
                              .build();
    }

    private static FileMapping fileMapping() {
        FileMapping mapping = new FileMapping();
        mapping.setPath("/tmp/hubble-load-task-test.csv");
        mapping.setFileSetting(fileSetting());
        mapping.setVertexMappings(new LinkedHashSet<>(Arrays.asList(
                vertexMapping())));
        mapping.setEdgeMappings(new LinkedHashSet<>(Arrays.asList(
                edgeMapping())));
        return mapping;
    }

    private static FileSetting fileSetting() {
        FileSetting setting = new FileSetting();
        setting.setColumnNames(Arrays.asList("source", "target", "relation"));
        return setting;
    }

    private static VertexMapping vertexMapping() {
        VertexMapping mapping = new VertexMapping();
        mapping.setId("source-vertex");
        mapping.setLabel("person");
        mapping.setFieldMappings(Arrays.asList(
                FieldMappingItem.builder()
                                .columnName("source")
                                .mappedName("name")
                                .build()));
        mapping.setNullValues(emptyNullValues());
        ReflectionTestUtils.setField(mapping, "idFields",
                                     Arrays.asList("source"));
        return mapping;
    }

    private static EdgeMapping edgeMapping() {
        EdgeMapping mapping = new EdgeMapping();
        mapping.setId("knows-edge");
        mapping.setLabel("knows");
        mapping.setFieldMappings(Arrays.asList(
                FieldMappingItem.builder()
                                .columnName("relation")
                                .mappedName("relation")
                                .build()));
        mapping.setNullValues(emptyNullValues());
        ReflectionTestUtils.setField(mapping, "sourceFields",
                                     Arrays.asList("source"));
        ReflectionTestUtils.setField(mapping, "targetFields",
                                     Arrays.asList("target"));
        return mapping;
    }

    private static NullValues emptyNullValues() {
        return NullValues.builder()
                         .checked(Collections.emptySet())
                         .customized(Collections.emptySet())
                         .build();
    }
}
