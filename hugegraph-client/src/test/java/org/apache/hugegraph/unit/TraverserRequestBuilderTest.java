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

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import org.apache.hugegraph.api.traverser.NeighborRankAPI;
import org.apache.hugegraph.api.traverser.PersonalRankAPI;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.traverser.CustomizedPathsRequest;
import org.apache.hugegraph.structure.traverser.EdgeStep;
import org.apache.hugegraph.structure.traverser.KneighborRequest;
import org.apache.hugegraph.structure.traverser.KoutRequest;
import org.apache.hugegraph.structure.traverser.PathsRequest;
import org.apache.hugegraph.structure.traverser.RepeatEdgeStep;
import org.apache.hugegraph.structure.traverser.Steps;
import org.apache.hugegraph.structure.traverser.VESteps;
import org.apache.hugegraph.testutil.Assert;

import com.google.common.collect.ImmutableMap;

public class TraverserRequestBuilderTest extends BaseUnitTest {

    @Test
    public void testPersonalRankRequestBuilder() {
        PersonalRankAPI.Request request = PersonalRankAPI.Request.builder()
                                                                 .source("marko")
                                                                 .label("knows")
                                                                 .alpha(0.8D)
                                                                 .degree(100L)
                                                                 .limit(20L)
                                                                 .maxDepth(3)
                                                                 .withLabel(PersonalRankAPI.Request.WithLabel.SAME_LABEL)
                                                                 .sorted(false)
                                                                 .build();

        String json = serialize(request);

        Assert.assertContains("\"source\":\"marko\"", json);
        Assert.assertContains("\"label\":\"knows\"", json);
        Assert.assertContains("\"alpha\":0.8", json);
        Assert.assertContains("\"max_degree\":100", json);
        Assert.assertContains("\"limit\":20", json);
        Assert.assertContains("\"max_depth\":3", json);
        Assert.assertContains("\"with_label\":\"SAME_LABEL\"", json);
        Assert.assertContains("\"sorted\":false", json);
        Assert.assertContains("Request{source=marko,label=knows",
                              request.toString());
    }

    @Test
    public void testPersonalRankRequestValidation() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PersonalRankAPI.Request.builder().source(null);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PersonalRankAPI.Request.builder().label(null);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PersonalRankAPI.Request.builder()
                                   .source("marko")
                                   .label("knows")
                                   .alpha(0D);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PersonalRankAPI.Request.builder()
                                   .source("marko")
                                   .label("knows")
                                   .maxDepth(0);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PersonalRankAPI.Request.builder().source("marko").build();
        });
    }

    @Test
    public void testNeighborRankRequestBuilder() {
        NeighborRankAPI.Request.Builder builder =
                NeighborRankAPI.Request.builder()
                                       .source("marko")
                                       .alpha(0.7D)
                                       .capacity(200L);
        builder.steps()
               .direction(Direction.OUT)
               .labels(Arrays.asList("knows", "created"))
               .degree(100L)
               .top(10);
        builder.steps()
               .direction(Direction.IN)
               .labels("likes")
               .degree(50L)
               .top(5);
        NeighborRankAPI.Request request = builder.build();

        String json = serialize(request);

        Assert.assertContains("\"source\":\"marko\"", json);
        Assert.assertContains("\"alpha\":0.7", json);
        Assert.assertContains("\"capacity\":200", json);
        Assert.assertContains("\"direction\":\"OUT\"", json);
        Assert.assertContains("\"labels\":[\"knows\",\"created\"]", json);
        Assert.assertContains("\"top\":10", json);
        Assert.assertContains("Request{source=marko,steps=",
                              request.toString());
    }

    @Test
    public void testNeighborRankRequestValidation() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            NeighborRankAPI.Request.builder().source(null);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            NeighborRankAPI.Request.builder().source("marko").build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            NeighborRankAPI.Request.builder().source("marko").alpha(2D);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            NeighborRankAPI.Request.builder().steps().top(0);
        });
    }

    @Test
    public void testKoutAndKneighborRequestBuilders() {
        KoutRequest.Builder koutBuilder = KoutRequest.builder()
                                                     .source("marko");
        koutBuilder.steps()
                   .direction(Direction.OUT)
                   .edgeSteps(new Steps.StepEntity(
                           "knows",
                           ImmutableMap.of("weight", 1)))
                   .vertexSteps(new Steps.StepEntity("person"))
                   .degree(100L)
                   .skipDegree(100L);
        KoutRequest kout = koutBuilder.maxDepth(2)
                                      .nearest(false)
                                      .capacity(500L)
                                      .limit(30L)
                                      .withVertex(true)
                                      .withPath(true)
                                      .withEdge(true)
                                      .build();

        KneighborRequest.Builder kneighborBuilder =
                KneighborRequest.builder().source("marko");
        kneighborBuilder.steps()
                        .direction(Direction.BOTH)
                        .edgeSteps(new Steps.StepEntity("knows"))
                        .degree(50L);
        KneighborRequest kneighbor = kneighborBuilder.maxDepth(3)
                                                    .limit(20L)
                                                    .withVertex(true)
                                                    .withPath(true)
                                                    .withEdge(true)
                                                    .build();

        String koutJson = serialize(kout);
        String kneighborJson = serialize(kneighbor);
        Assert.assertContains("\"source\":\"marko\"", koutJson);
        Assert.assertContains("\"max_depth\":2", koutJson);
        Assert.assertContains("\"nearest\":false", koutJson);
        Assert.assertContains("\"with_edge\":true", koutJson);
        Assert.assertContains("KoutRequest{source=marko", kout.toString());
        Assert.assertContains("\"max_depth\":3", kneighborJson);
        Assert.assertContains("\"with_path\":true", kneighborJson);
        Assert.assertContains("KneighborRequest{source=marko",
                              kneighbor.toString());
    }

    @Test
    public void testKoutAndKneighborValidation() {
        Assert.assertThrows(NullPointerException.class, () -> {
            KoutRequest.builder().build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            KoutRequest.builder()
                       .source("marko")
                       .countOnly(true)
                       .withVertex(true)
                       .build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            KneighborRequest.builder()
                            .source("marko")
                            .countOnly(true)
                            .withEdge(true)
                            .build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            KneighborRequest.builder().source("marko").limit(0L);
        });
    }

    @Test
    public void testStepBuilders() {
        EdgeStep edgeStep = EdgeStep.builder()
                                    .direction(Direction.OUT)
                                    .labels("knows")
                                    .properties("weight", 1)
                                    .degree(100L)
                                    .skipDegree(100L)
                                    .build();
        RepeatEdgeStep repeat = RepeatEdgeStep.repeatStepBuilder()
                                              .direction(Direction.IN)
                                              .labels(Collections.singletonList("created"))
                                              .properties(ImmutableMap.of("lang",
                                                                         "java"))
                                              .degree(20L)
                                              .skipDegree(20L)
                                              .maxTimes(3)
                                              .build();
        VESteps veSteps = VESteps.builder()
                                 .direction(Direction.BOTH)
                                 .addVStep("person", ImmutableMap.of("age", 29))
                                 .addEStep("knows")
                                 .degree(10L)
                                 .skipDegree(10L)
                                 .build();

        Assert.assertContains("EdgeStep{direction=OUT", edgeStep.toString());
        Assert.assertContains("RepeatEdgeStep{direction=IN", repeat.toString());
        Assert.assertContains("maxTimes=3", repeat.toString());
        Assert.assertContains("Steps{direction=BOTH", veSteps.toString());
    }

    @Test
    public void testStepValidation() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            EdgeStep.builder().degree(0L);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            RepeatEdgeStep.repeatStepBuilder().maxTimes(0).build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            VESteps.builder().degree(10L).skipDegree(5L);
        });
    }

    @Test
    public void testPathsRequestBuilders() {
        PathsRequest.Builder pathsBuilder = PathsRequest.builder();
        pathsBuilder.sources().ids("marko").label("person");
        pathsBuilder.targets().ids("lop").property("name", "lop");
        pathsBuilder.step()
                    .direction(Direction.OUT)
                    .labels("created")
                    .degree(100L);
        PathsRequest paths = pathsBuilder.maxDepth(3)
                                         .nearest(true)
                                         .capacity(500L)
                                         .limit(20)
                                         .withVertex(true)
                                         .withEdge(true)
                                         .build();

        CustomizedPathsRequest.Builder customizedBuilder =
                CustomizedPathsRequest.builder();
        customizedBuilder.sources().ids("marko").property("name", "marko");
        customizedBuilder.steps()
                         .direction(Direction.OUT)
                         .labels("created")
                         .properties("weight", 1)
                         .weightBy("weight")
                         .defaultWeight(0.5D)
                         .degree(100L)
                         .sample(10);
        CustomizedPathsRequest customized =
                customizedBuilder.sortBy(CustomizedPathsRequest.SortBy.INCR)
                                 .capacity(500L)
                                 .limit(20L)
                                 .withVertex(true)
                                 .withEdge(true)
                                 .build();

        Assert.assertContains("\"sources\"", serialize(paths));
        Assert.assertContains("\"targets\"", serialize(paths));
        Assert.assertContains("\"max_depth\":3", serialize(paths));
        Assert.assertContains("PathRequest{sources=", paths.toString());
        Assert.assertContains("\"sort_by\":\"INCR\"", serialize(customized));
        Assert.assertContains("\"weight_by\":\"weight\"", serialize(customized));
        Assert.assertContains("CustomizedPathsRequest{sourceVertex=",
                              customized.toString());
    }

    @Test
    public void testPathsRequestValidation() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            PathsRequest.Builder builder = PathsRequest.builder();
            builder.sources().ids("marko");
            builder.build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            CustomizedPathsRequest.Builder builder =
                    CustomizedPathsRequest.builder();
            builder.sources().ids("marko");
            builder.build();
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            CustomizedPathsRequest.builder().steps().sample(0);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            CustomizedPathsRequest.Builder builder =
                    CustomizedPathsRequest.builder();
            builder.sources().ids("marko");
            builder.steps().degree(3L).sample(4);
            builder.build();
        });
    }
}
