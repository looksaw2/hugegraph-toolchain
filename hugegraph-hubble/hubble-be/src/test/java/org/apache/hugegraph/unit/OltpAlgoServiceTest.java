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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hugegraph.api.traverser.NeighborRankAPI;
import org.apache.hugegraph.api.traverser.PersonalRankAPI;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.TraverserManager;
import org.apache.hugegraph.entity.algorithm.ShortestPath;
import org.apache.hugegraph.entity.query.GraphView;
import org.apache.hugegraph.entity.query.GremlinResult;
import org.apache.hugegraph.service.HugeClientPoolService;
import org.apache.hugegraph.service.algorithm.OltpAlgoService;
import org.apache.hugegraph.service.query.ExecuteHistoryService;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.graph.Edge;
import org.apache.hugegraph.structure.graph.Path;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.traverser.CustomizedPathsRequest;
import org.apache.hugegraph.structure.traverser.FusiformSimilarity;
import org.apache.hugegraph.structure.traverser.FusiformSimilarityRequest;
import org.apache.hugegraph.structure.traverser.JaccardSimilarity;
import org.apache.hugegraph.structure.traverser.Kneighbor;
import org.apache.hugegraph.structure.traverser.KneighborRequest;
import org.apache.hugegraph.structure.traverser.Kout;
import org.apache.hugegraph.structure.traverser.KoutRequest;
import org.apache.hugegraph.structure.traverser.PathOfVertices;
import org.apache.hugegraph.structure.traverser.PathWithMeasure;
import org.apache.hugegraph.structure.traverser.PathsWithVertices;
import org.apache.hugegraph.structure.traverser.RanksWithMeasure;
import org.apache.hugegraph.structure.traverser.SameNeighbors;
import org.apache.hugegraph.structure.traverser.WeightedPath;
import org.apache.hugegraph.structure.traverser.WeightedPaths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OltpAlgoServiceTest {

    private static final int CONN_ID = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OltpAlgoService service;
    private HugeClientPoolService poolService;
    private ExecuteHistoryService historyService;
    private HugeClient client;
    private TraverserManager traverser;

    @Before
    public void setup() {
        this.service = new OltpAlgoService();
        this.poolService = new HugeClientPoolService();
        this.historyService = Mockito.mock(ExecuteHistoryService.class);
        this.client = Mockito.mock(HugeClient.class);
        this.traverser = Mockito.mock(TraverserManager.class);

        ReflectionTestUtils.setField(this.service, "poolService", this.poolService);
        ReflectionTestUtils.setField(this.service, "historyService", this.historyService);

        this.poolService.put(CONN_ID, this.client);
        Mockito.when(this.client.traverser()).thenReturn(this.traverser);
    }

    @Test
    public void testShortestPathBuildsGraphViewFromReturnedIds() {
        PathOfVertices path = new PathOfVertices();
        ReflectionTestUtils.setField(path, "path", Arrays.asList("person:marko", "person:vadas"));
        ReflectionTestUtils.setField(path, "vertices",
                                     Arrays.asList("person:marko", "person:vadas"));
        ReflectionTestUtils.setField(path, "edges",
                                     Collections.singletonList("person:marko>knows>>person:vadas"));

        Vertex source = new Vertex("person");
        source.id("person:marko");
        source.property("name", "marko");

        Vertex target = new Vertex("person");
        target.id("person:vadas");
        target.property("name", "vadas");

        Edge edge = new Edge("knows");
        edge.id("person:marko>knows>>person:vadas");
        edge.sourceId("person:marko");
        edge.sourceLabel("person");
        edge.targetId("person:vadas");
        edge.targetLabel("person");

        Mockito.when(this.traverser.shortestPath(Mockito.any(), Mockito.any(),
                                                 Mockito.any(), Mockito.any(),
                                                 Mockito.anyInt(), Mockito.anyLong(),
                                                 Mockito.anyLong(), Mockito.anyLong()))
               .thenReturn(path);
        Mockito.when(this.traverser.vertices(Mockito.anyList()))
               .thenReturn(Arrays.asList(source, target));
        Mockito.when(this.traverser.edges(Mockito.anyList()))
               .thenReturn(Collections.singletonList(edge));

        GremlinResult result = this.service.shortestPath(CONN_ID, shortestPathBody());

        Assert.assertEquals(GremlinResult.Type.PATH, result.getType());
        Assert.assertEquals(Arrays.asList("person:marko", "person:vadas"),
                            result.getJsonView().getData());
        Assert.assertEquals(2, result.getGraphView().getVertices().size());
        Assert.assertEquals(1, result.getGraphView().getEdges().size());
    }

    @Test
    public void testAlgorithmShortPathSlugBuildsGraphViewFromReturnedIds() {
        PathOfVertices path = new PathOfVertices();
        ReflectionTestUtils.setField(path, "path", Arrays.asList("person:marko", "person:vadas"));
        ReflectionTestUtils.setField(path, "vertices",
                                     Arrays.asList("person:marko", "person:vadas"));
        ReflectionTestUtils.setField(path, "edges",
                                     Collections.singletonList("person:marko>knows>>person:vadas"));

        Vertex source = new Vertex("person");
        source.id("person:marko");

        Vertex target = new Vertex("person");
        target.id("person:vadas");

        Edge edge = new Edge("knows");
        edge.id("person:marko>knows>>person:vadas");
        edge.sourceId("person:marko");
        edge.sourceLabel("person");
        edge.targetId("person:vadas");
        edge.targetLabel("person");

        Mockito.when(this.traverser.shortestPath(Mockito.eq("person:marko"),
                                                 Mockito.eq("person:vadas"),
                                                 Mockito.eq(Direction.OUT),
                                                 Mockito.eq("knows"),
                                                 Mockito.eq(3),
                                                 Mockito.eq(100L),
                                                 Mockito.eq(0L),
                                                 Mockito.eq(1000L)))
               .thenReturn(path);
        Mockito.when(this.traverser.vertices(Mockito.anyList()))
               .thenReturn(Arrays.asList(source, target));
        Mockito.when(this.traverser.edges(Mockito.anyList()))
               .thenReturn(Collections.singletonList(edge));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", "person:marko");
        params.put("target", "person:vadas");
        params.put("direction", "OUT");
        params.put("label", "knows");
        params.put("max_depth", "3");
        params.put("max_degree", "100");
        params.put("skip_degree", "0");
        params.put("capacity", "1000");
        GremlinResult result = this.service.algorithm(CONN_ID, "shortpath", params);

        Assert.assertEquals(GremlinResult.Type.PATH, result.getType());
        Assert.assertEquals(2, result.getGraphView().getVertices().size());
        Assert.assertEquals(1, result.getGraphView().getEdges().size());
    }

    @Test
    public void testAlgorithmPathMeasureSlugsRouteAndBuildGraphViews() {
        Path path = graphPath("person:marko", "person:vadas");
        PathWithMeasure withPaths = pathMeasure("paths", path,
                                                Arrays.asList("person:marko",
                                                              "person:vadas"),
                                                Collections.singletonList("edge-1"));
        PathWithMeasure withRings = pathMeasure("rings", path, null, null);
        PathWithMeasure withCrosspoints = pathMeasure("crosspoints", path, null, null);
        PathWithMeasure withRays = pathMeasure("rays", path, null, null);

        Vertex source = vertex("person:marko");
        Vertex target = vertex("person:vadas");
        Edge edge = edge("edge-1");

        Mockito.when(this.traverser.vertices(Mockito.anyList()))
               .thenReturn(Arrays.asList(source, target));
        Mockito.when(this.traverser.edges(Mockito.anyList()))
               .thenReturn(Collections.singletonList(edge));
        Mockito.when(this.traverser.allShortestPaths(Mockito.eq("person:marko"),
                                                     Mockito.eq("person:vadas"),
                                                     Mockito.eq(Direction.OUT),
                                                     Mockito.eq("knows"),
                                                     Mockito.eq(3),
                                                     Mockito.eq(100L),
                                                     Mockito.eq(1L),
                                                     Mockito.eq(1000L)))
               .thenReturn(withPaths);
        Mockito.when(this.traverser.paths(Mockito.eq("person:marko"),
                                          Mockito.eq("person:vadas"),
                                          Mockito.eq(Direction.OUT),
                                          Mockito.eq("knows"),
                                          Mockito.eq(3),
                                          Mockito.eq(100L),
                                          Mockito.eq(1000L),
                                          Mockito.eq(9L)))
               .thenReturn(withPaths);
        Mockito.when(this.traverser.rings(Mockito.eq("person:marko"),
                                          Mockito.eq(Direction.OUT),
                                          Mockito.eq("knows"),
                                          Mockito.eq(3),
                                          Mockito.eq(false),
                                          Mockito.eq(100L),
                                          Mockito.eq(1000L),
                                          Mockito.eq(9L)))
               .thenReturn(withRings);
        Mockito.when(this.traverser.crosspoint(Mockito.eq("person:marko"),
                                               Mockito.eq("person:vadas"),
                                               Mockito.eq(Direction.OUT),
                                               Mockito.eq("knows"),
                                               Mockito.eq(3),
                                               Mockito.eq(100L),
                                               Mockito.eq(1000L),
                                               Mockito.eq(9L)))
               .thenReturn(withCrosspoints);
        Mockito.when(this.traverser.rays(Mockito.eq("person:marko"),
                                         Mockito.eq(Direction.OUT),
                                         Mockito.eq("knows"),
                                         Mockito.eq(3),
                                         Mockito.eq(100L),
                                         Mockito.eq(1000L),
                                         Mockito.eq(9L)))
               .thenReturn(withRays);

        Map<String, Object> params = params("source", "person:marko",
                                            "target", "person:vadas",
                                            "direction", "OUT",
                                            "label", "knows",
                                            "max_depth", "3",
                                            "max_degree", "100",
                                            "skip_degree", "1",
                                            "capacity", "1000",
                                            "limit", "9",
                                            "source_in_ring", false);

        GremlinResult allShortPath = this.service.algorithm(CONN_ID, "allshortpath",
                                                            params);
        GremlinResult paths = this.service.algorithm(CONN_ID, "paths", params);
        GremlinResult rings = this.service.algorithm(CONN_ID, "rings", params);
        GremlinResult crosspoints = this.service.algorithm(CONN_ID, "crosspoints",
                                                           params);
        GremlinResult rays = this.service.algorithm(CONN_ID, "rays", params);

        assertPathResult(allShortPath, 2, 1);
        assertPathResult(paths, 2, 1);
        assertPathResult(rings, 2, 0);
        assertPathResult(crosspoints, 2, 0);
        assertPathResult(rays, 2, 0);
    }

    @Test
    public void testAlgorithmKneighborAndKoutBuildPostRequestsAndResults()
            throws Exception {
        Kneighbor kneighbor = new Kneighbor();
        ReflectionTestUtils.setField(kneighbor, "ids",
                                     linkedSet("person:vadas", "person:josh"));
        Kout kout = new Kout();
        ReflectionTestUtils.setField(kout, "paths",
                                     Collections.singletonList(graphPath("person:marko",
                                                                        "person:vadas")));
        ReflectionTestUtils.setField(kout, "vertices",
                                     linkedSet(vertex("person:marko"),
                                               vertex("person:vadas")));
        ReflectionTestUtils.setField(kout, "edges",
                                     linkedSet(edge("edge-1")));

        Mockito.when(this.traverser.kneighbor(Mockito.any(KneighborRequest.class)))
               .thenReturn(kneighbor);
        Mockito.when(this.traverser.kout(Mockito.any(KoutRequest.class)))
               .thenReturn(kout);

        Map<String, Object> params = params("source", "person:marko",
                                            "direction", "OUT",
                                            "label", "knows",
                                            "max_depth", 2,
                                            "max_degree", "20",
                                            "skip_degree", "0",
                                            "limit", 5,
                                            "capacity", 100,
                                            "nearest", false);

        GremlinResult kneighborResult = this.service.algorithm(CONN_ID, "kneighbor",
                                                               params);
        GremlinResult koutResult = this.service.algorithm(CONN_ID, "kout", params);

        Assert.assertEquals(GremlinResult.Type.PATH, kneighborResult.getType());
        Assert.assertEquals(2, kneighborResult.getJsonView().getData().size());
        assertPathResult(koutResult, 2, 1);

        ArgumentCaptor<KneighborRequest> kneighborCaptor =
                ArgumentCaptor.forClass(KneighborRequest.class);
        Mockito.verify(this.traverser).kneighbor(kneighborCaptor.capture());
        JsonNode kneighborJson = json(kneighborCaptor.getValue());
        Assert.assertEquals("person:marko", kneighborJson.get("source").asText());
        Assert.assertEquals(2, kneighborJson.get("max_depth").asInt());
        Assert.assertTrue(kneighborJson.get("with_vertex").asBoolean());
        Assert.assertTrue(kneighborJson.get("with_path").asBoolean());
        Assert.assertTrue(kneighborJson.get("with_edge").asBoolean());
        Assert.assertEquals("OUT", kneighborJson.get("steps").get("direction").asText());
        Assert.assertEquals("knows", kneighborJson.get("steps")
                                                  .get("edge_steps").get(0)
                                                  .get("label").asText());

        ArgumentCaptor<KoutRequest> koutCaptor =
                ArgumentCaptor.forClass(KoutRequest.class);
        Mockito.verify(this.traverser).kout(koutCaptor.capture());
        JsonNode koutJson = json(koutCaptor.getValue());
        Assert.assertFalse(koutJson.get("nearest").asBoolean());
        Assert.assertEquals(100, koutJson.get("capacity").asInt());
        Assert.assertEquals(5, koutJson.get("limit").asInt());
    }

    @Test
    public void testAlgorithmCustomAndWeightedResults() throws Exception {
        PathsWithVertices custom = new PathsWithVertices();
        PathsWithVertices.Paths customPath = new PathsWithVertices.Paths();
        ReflectionTestUtils.setField(customPath, "objects",
                                     Arrays.asList("person:marko",
                                                   "person:vadas"));
        ReflectionTestUtils.setField(custom, "paths",
                                     Collections.singletonList(customPath));
        ReflectionTestUtils.setField(custom, "vertices",
                                     linkedSet(vertex("person:marko"),
                                               vertex("person:vadas")));
        ReflectionTestUtils.setField(custom, "edges", linkedSet(edge("edge-1")));

        WeightedPath weightedPath = new WeightedPath();
        WeightedPath.Path path = new WeightedPath.Path();
        ReflectionTestUtils.setField(path, "vertices",
                                     Arrays.asList("person:marko",
                                                   "person:vadas"));
        ReflectionTestUtils.setField(weightedPath, "path", path);
        ReflectionTestUtils.setField(weightedPath, "vertices",
                                     linkedSet(vertex("person:marko"),
                                               vertex("person:vadas")));
        ReflectionTestUtils.setField(weightedPath, "edges",
                                     linkedSet(edge("edge-1")));

        WeightedPaths weightedPaths = new WeightedPaths();
        Map<Object, WeightedPath.Path> paths = new LinkedHashMap<>();
        paths.put("person:vadas", path);
        ReflectionTestUtils.setField(weightedPaths, "paths", paths);
        ReflectionTestUtils.setField(weightedPaths, "vertices",
                                     linkedSet(vertex("person:marko"),
                                               vertex("person:vadas")));
        ReflectionTestUtils.setField(weightedPaths, "edges",
                                     linkedSet(edge("edge-1")));

        Mockito.when(this.traverser.customizedPaths(
                Mockito.any(CustomizedPathsRequest.class))).thenReturn(custom);
        Mockito.when(this.traverser.weightedShortestPath(
                Mockito.eq("person:marko"), Mockito.eq("person:vadas"),
                Mockito.eq(Direction.OUT), Mockito.eq("knows"),
                Mockito.eq("weight"), Mockito.eq(20L), Mockito.eq(1L),
                Mockito.eq(200L), Mockito.eq(false), Mockito.eq(true)))
               .thenReturn(weightedPath);
        Mockito.when(this.traverser.singleSourceShortestPath(
                Mockito.eq("person:marko"), Mockito.eq(Direction.OUT),
                Mockito.eq("knows"), Mockito.eq("weight"), Mockito.eq(20L),
                Mockito.eq(1L), Mockito.eq(200L), Mockito.eq(6L),
                Mockito.eq(false), Mockito.eq(true))).thenReturn(weightedPaths);

        Map<String, Object> step = params("direction", "OUT",
                                          "labels", Collections.singletonList("knows"),
                                          "properties", params("since", 2020),
                                          "weight_by", "weight",
                                          "default_weight", "1.5",
                                          "degree", "20",
                                          "sample", "3");
        Map<String, Object> params = params("source", "person:marko",
                                            "target", "person:vadas",
                                            "sources", Collections.singletonList(
                                                    "person:marko"),
                                            "steps", Collections.singletonList(step),
                                            "direction", "OUT",
                                            "label", "knows",
                                            "weight", "weight",
                                            "max_degree", "20",
                                            "skip_degree", "1",
                                            "capacity", "200",
                                            "limit", "6",
                                            "with_vertex", false,
                                            "sort_by", "INCR");

        GremlinResult customized = this.service.algorithm(CONN_ID, "customizedpaths",
                                                          params);
        GremlinResult shortest = this.service.algorithm(CONN_ID, "weightedshortpath",
                                                        params);
        GremlinResult single = this.service.algorithm(CONN_ID, "singleshortpath",
                                                      params);

        assertPathResult(customized, 2, 1);
        assertPathResult(shortest, 2, 1);
        assertPathResult(single, 2, 1);

        ArgumentCaptor<CustomizedPathsRequest> captor =
                ArgumentCaptor.forClass(CustomizedPathsRequest.class);
        Mockito.verify(this.traverser).customizedPaths(captor.capture());
        JsonNode requestJson = json(captor.getValue());
        Assert.assertEquals("INCR", requestJson.get("sort_by").asText());
        Assert.assertEquals(6, requestJson.get("limit").asInt());
        Assert.assertEquals("OUT", requestJson.get("steps").get(0)
                                              .get("direction").asText());
        Assert.assertEquals("knows", requestJson.get("steps").get(0)
                                                .get("labels").get(0).asText());
        Assert.assertEquals(2020, requestJson.get("steps").get(0)
                                             .get("properties")
                                             .get("since").asInt());
    }

    @Test
    public void testAlgorithmGeneralRequestsAndUnsupportedSlug() throws Exception {
        FusiformSimilarity fusiform = Mockito.mock(FusiformSimilarity.class);
        RanksWithMeasure ranks = Mockito.mock(RanksWithMeasure.class);
        SameNeighbors sameNeighbors = Mockito.mock(SameNeighbors.class);

        Mockito.when(this.traverser.fusiformSimilarity(
                Mockito.any(FusiformSimilarityRequest.class))).thenReturn(fusiform);
        Mockito.when(this.traverser.neighborRank(
                Mockito.any(NeighborRankAPI.Request.class))).thenReturn(ranks);
        Mockito.when(this.traverser.sameNeighbors(Mockito.eq("person:marko"),
                                                  Mockito.eq("person:vadas"),
                                                  Mockito.eq(Direction.IN),
                                                  Mockito.isNull(),
                                                  Mockito.eq(30L),
                                                  Mockito.eq(7L)))
               .thenReturn(sameNeighbors);
        Mockito.when(this.traverser.personalRank(
                Mockito.any(PersonalRankAPI.Request.class))).thenReturn(ranks);

        Map<String, Object> source = params("ids", Arrays.asList("person:marko",
                                                                 "person:vadas"),
                                            "label", "person",
                                            "properties", params("group", "A"));
        Map<String, Object> rankStep = params("direction", "IN",
                                              "labels", Collections.singletonList(
                                                      "knows"),
                                              "degree", "30",
                                              "top", "5");
        Map<String, Object> params = params("source", "person:marko",
                                            "sources", source,
                                            "vertex", "person:marko",
                                            "other", "person:vadas",
                                            "direction", "IN",
                                            "label", "__all__",
                                            "steps", Collections.singletonList(rankStep),
                                            "max_degree", "30",
                                            "capacity", "300",
                                            "limit", "7",
                                            "alpha", "0.7",
                                            "min_neighbors", "2",
                                            "min_similars", "2",
                                            "top", "5",
                                            "group_property", "group",
                                            "min_groups", "2",
                                            "with_intermediary", true,
                                            "with_vertex", true,
                                            "with_label", "SAME_LABEL",
                                            "sorted", false);

        Assert.assertEquals(GremlinResult.Type.GENERAL,
                            this.service.algorithm(CONN_ID, "fsimilarity",
                                                   params).getType());
        Assert.assertEquals(GremlinResult.Type.GENERAL,
                            this.service.algorithm(CONN_ID, "neighborrank",
                                                   params).getType());
        Assert.assertEquals(GremlinResult.Type.GENERAL,
                            this.service.algorithm(CONN_ID, "sameneighbors",
                                                   params).getType());

        Map<String, Object> personalRankParams = new LinkedHashMap<>(params);
        personalRankParams.put("label", "knows");
        Assert.assertEquals(GremlinResult.Type.GENERAL,
                            this.service.algorithm(CONN_ID, "personalrank",
                                                   personalRankParams).getType());

        ArgumentCaptor<FusiformSimilarityRequest> fusiformCaptor =
                ArgumentCaptor.forClass(FusiformSimilarityRequest.class);
        Mockito.verify(this.traverser).fusiformSimilarity(fusiformCaptor.capture());
        JsonNode fusiformJson = json(fusiformCaptor.getValue());
        Assert.assertEquals("IN", fusiformJson.get("direction").asText());
        Assert.assertTrue(fusiformJson.get("label").isNull());
        Assert.assertEquals(2, fusiformJson.get("min_neighbors").asInt());
        Assert.assertEquals("person", fusiformJson.get("sources").get("label").asText());

        ArgumentCaptor<NeighborRankAPI.Request> rankCaptor =
                ArgumentCaptor.forClass(NeighborRankAPI.Request.class);
        Mockito.verify(this.traverser).neighborRank(rankCaptor.capture());
        JsonNode rankJson = json(rankCaptor.getValue());
        Assert.assertEquals("person:marko", rankJson.get("source").asText());
        Assert.assertEquals("IN", rankJson.get("steps").get(0)
                                           .get("direction").asText());
        Assert.assertEquals("knows", rankJson.get("steps").get(0)
                                             .get("labels").get(0).asText());

        ArgumentCaptor<PersonalRankAPI.Request> personalCaptor =
                ArgumentCaptor.forClass(PersonalRankAPI.Request.class);
        Mockito.verify(this.traverser).personalRank(personalCaptor.capture());
        JsonNode personalJson = json(personalCaptor.getValue());
        Assert.assertEquals("knows", personalJson.get("label").asText());
        Assert.assertEquals("SAME_LABEL", personalJson.get("with_label").asText());
        Assert.assertFalse(personalJson.get("sorted").asBoolean());

        try {
            this.service.algorithm(CONN_ID, "unknown", Collections.emptyMap());
            Assert.fail("Expected unsupported algorithm slug to fail");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Unsupported algorithm"));
        }
    }

    @Test
    public void testAlgorithmJaccardSimilaritySerializesNestedSimilarityMap()
            throws Exception {
        JaccardSimilarity jaccard = new JaccardSimilarity();
        Map<Object, Object> similarities = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("person:vadas", 0.5D);
        similarities.put("person:marko", nested);
        jaccard.setSimilarsMap(similarities);

        Mockito.when(this.traverser.jaccardSimilarity(Mockito.eq("person:marko"),
                                                      Mockito.eq("person:vadas"),
                                                      Mockito.eq(Direction.BOTH),
                                                      Mockito.eq("knows"),
                                                      Mockito.eq(100L)))
               .thenReturn(jaccard);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("vertex", "person:marko");
        params.put("other", "person:vadas");
        params.put("direction", "BOTH");
        params.put("label", "knows");
        params.put("max_degree", "100");

        GremlinResult result = this.service.algorithm(CONN_ID, "jaccardsimilarity",
                                                      params);
        String json = new ObjectMapper().writeValueAsString(result);

        Assert.assertEquals(GremlinResult.Type.GENERAL, result.getType());
        Assert.assertTrue(json.contains("\"jaccard_similarity\""));
        Assert.assertTrue(json.contains("\"person:vadas\":0.5"));
    }

    @Test
    public void testAlgorithmJaccardSimilaritySerializesScalarSimilarity()
            throws Exception {
        JaccardSimilarity jaccard = new JaccardSimilarity();
        jaccard.setJaccardSimilarity(0.3333333333333333D);

        Mockito.when(this.traverser.jaccardSimilarity(Mockito.eq("person:marko"),
                                                      Mockito.eq("person:vadas"),
                                                      Mockito.eq(Direction.BOTH),
                                                      Mockito.eq("knows"),
                                                      Mockito.eq(100L)))
               .thenReturn(jaccard);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("vertex", "person:marko");
        params.put("other", "person:vadas");
        params.put("direction", "BOTH");
        params.put("label", "knows");
        params.put("max_degree", "100");

        GremlinResult result = this.service.algorithm(CONN_ID, "jaccardsimilarity",
                                                      params);
        String json = new ObjectMapper().writeValueAsString(result);

        Assert.assertEquals(GremlinResult.Type.GENERAL, result.getType());
        Assert.assertTrue(json.contains("\"jaccard_similarity\":0.3333333333333333"));
    }

    @Test
    public void testShortestPathFallsBackToPathIdsWhenVerticesListMissing() {
        PathOfVertices path = new PathOfVertices();
        ReflectionTestUtils.setField(path, "path", Arrays.asList("person:marko", "person:vadas"));

        Vertex source = new Vertex("person");
        source.id("person:marko");

        Vertex target = new Vertex("person");
        target.id("person:vadas");

        Mockito.when(this.traverser.shortestPath(Mockito.any(), Mockito.any(),
                                                 Mockito.any(), Mockito.any(),
                                                 Mockito.anyInt(), Mockito.anyLong(),
                                                 Mockito.anyLong(), Mockito.anyLong()))
               .thenReturn(path);
        Mockito.when(this.traverser.vertices(Mockito.anyList()))
               .thenReturn(Arrays.asList(source, target));

        GremlinResult result = this.service.shortestPath(CONN_ID, shortestPathBody());

        Assert.assertEquals(2, result.getGraphView().getVertices().size());
        Assert.assertTrue(result.getGraphView().getEdges().isEmpty());
        Mockito.verify(this.traverser, Mockito.never()).edges(Mockito.anyList());
    }

    @Test
    public void testShortestPathReturnsEmptyGraphViewWhenPathIsEmpty() {
        PathOfVertices path = new PathOfVertices();
        ReflectionTestUtils.setField(path, "path", Collections.emptyList());

        Mockito.when(this.traverser.shortestPath(Mockito.any(), Mockito.any(),
                                                 Mockito.any(), Mockito.any(),
                                                 Mockito.anyInt(), Mockito.anyLong(),
                                                 Mockito.anyLong(), Mockito.anyLong()))
               .thenReturn(path);

        GremlinResult result = this.service.shortestPath(CONN_ID, shortestPathBody());

        Assert.assertSame(GraphView.EMPTY, result.getGraphView());
        Mockito.verify(this.traverser, Mockito.never()).vertices(Mockito.anyList());
        Mockito.verify(this.traverser, Mockito.never()).edges(Mockito.anyList());
    }

    @Test
    public void testShortestPathKeepsResultWhenGraphViewLookupFails() {
        PathOfVertices path = new PathOfVertices();
        ReflectionTestUtils.setField(path, "path", Arrays.asList("person:marko", "person:vadas"));
        ReflectionTestUtils.setField(path, "vertices",
                                     Arrays.asList("person:marko", "person:vadas"));

        Mockito.when(this.traverser.shortestPath(Mockito.any(), Mockito.any(),
                                                 Mockito.any(), Mockito.any(),
                                                 Mockito.anyInt(), Mockito.anyLong(),
                                                 Mockito.anyLong(), Mockito.anyLong()))
               .thenReturn(path);
        Mockito.when(this.traverser.vertices(Mockito.anyList()))
               .thenThrow(new RuntimeException("lookup failed"));

        GremlinResult result = this.service.shortestPath(CONN_ID, shortestPathBody());

        Assert.assertEquals(GremlinResult.Type.PATH, result.getType());
        Assert.assertEquals(Arrays.asList("person:marko", "person:vadas"),
                            result.getJsonView().getData());
        Assert.assertSame(GraphView.EMPTY, result.getGraphView());
    }

    private static ShortestPath shortestPathBody() {
        return ShortestPath.builder()
                           .source("person:marko")
                           .target("person:vadas")
                           .direction(Direction.OUT)
                           .label("knows")
                           .maxDepth(3)
                           .maxDegree(100)
                           .skipDegree(0)
                           .capacity(1000)
                           .build();
    }

    private static void assertPathResult(GremlinResult result, int vertices,
                                         int edges) {
        Assert.assertEquals(GremlinResult.Type.PATH, result.getType());
        Assert.assertEquals(vertices, result.getGraphView().getVertices().size());
        Assert.assertEquals(edges, result.getGraphView().getEdges().size());
    }

    private static PathWithMeasure pathMeasure(String field, Path path,
                                               List<String> vertices,
                                               List<String> edges) {
        PathWithMeasure result = new PathWithMeasure();
        ReflectionTestUtils.setField(result, field, Collections.singletonList(path));
        ReflectionTestUtils.setField(result, "vertices", vertices);
        ReflectionTestUtils.setField(result, "edges", edges);
        return result;
    }

    private static Path graphPath(Object... objects) {
        return new Path(Arrays.asList(objects));
    }

    private static Vertex vertex(Object id) {
        Vertex vertex = new Vertex("person");
        vertex.id(id);
        return vertex;
    }

    private static Edge edge(String id) {
        Edge edge = new Edge("knows");
        edge.id(id);
        edge.sourceId("person:marko");
        edge.sourceLabel("person");
        edge.targetId("person:vadas");
        edge.targetLabel("person");
        return edge;
    }

    private static JsonNode json(Object value) {
        return MAPPER.valueToTree(value);
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    @SafeVarargs
    private static <T> Set<T> linkedSet(T... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
