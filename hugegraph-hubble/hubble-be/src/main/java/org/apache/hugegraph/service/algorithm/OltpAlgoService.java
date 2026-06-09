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

package org.apache.hugegraph.service.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.hugegraph.api.traverser.NeighborRankAPI;
import org.apache.hugegraph.api.traverser.PersonalRankAPI;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.TraverserManager;
import org.apache.hugegraph.entity.algorithm.ShortestPath;
import org.apache.hugegraph.entity.enums.AsyncTaskStatus;
import org.apache.hugegraph.entity.enums.ExecuteStatus;
import org.apache.hugegraph.entity.enums.ExecuteType;
import org.apache.hugegraph.entity.query.ExecuteHistory;
import org.apache.hugegraph.entity.query.GraphView;
import org.apache.hugegraph.entity.query.GremlinResult;
import org.apache.hugegraph.entity.query.JsonView;
import org.apache.hugegraph.entity.query.TableView;
import org.apache.hugegraph.service.HugeClientPoolService;
import org.apache.hugegraph.service.query.ExecuteHistoryService;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.graph.Edge;
import org.apache.hugegraph.structure.graph.Path;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.traverser.CustomizedPathsRequest;
import org.apache.hugegraph.structure.traverser.FusiformSimilarityRequest;
import org.apache.hugegraph.structure.traverser.JaccardSimilarity;
import org.apache.hugegraph.structure.traverser.Kneighbor;
import org.apache.hugegraph.structure.traverser.KneighborRequest;
import org.apache.hugegraph.structure.traverser.Kout;
import org.apache.hugegraph.structure.traverser.KoutRequest;
import org.apache.hugegraph.structure.traverser.PathOfVertices;
import org.apache.hugegraph.structure.traverser.PathWithMeasure;
import org.apache.hugegraph.structure.traverser.PathsWithVertices;
import org.apache.hugegraph.structure.traverser.Steps;
import org.apache.hugegraph.structure.traverser.VerticesArgs;
import org.apache.hugegraph.structure.traverser.WeightedPath;
import org.apache.hugegraph.structure.traverser.WeightedPaths;
import org.apache.hugegraph.util.HubbleUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class OltpAlgoService {

    private static final String ALL_LABELS = "__all__";

    @Autowired
    private HugeClientPoolService poolService;
    @Autowired
    private ExecuteHistoryService historyService;

    private HugeClient getClient(int connId) {
        return this.poolService.getOrCreate(connId);
    }

    public GremlinResult shortestPath(int connId, ShortestPath body) {
        HugeClient client = this.getClient(connId);
        TraverserManager traverser = client.traverser();
        PathOfVertices pathResult = traverser.shortestPath(body.getSource(), body.getTarget(),
                                                           body.getDirection(), body.getLabel(),
                                                           body.getMaxDepth(),
                                                           body.getMaxDegree(),
                                                           body.getSkipDegree(),
                                                           body.getCapacity());
        Path result = pathResult.getPath();
        JsonView jsonView = new JsonView();
        jsonView.setData(result.objects());
        Date createTime = HubbleUtil.nowDate();
        TableView tableView = this.buildPathTableView(result);
        GraphView graphView = this.buildPathGraphView(traverser, pathResult);
        // Insert execute history
        ExecuteStatus status = ExecuteStatus.SUCCESS;
        ExecuteHistory history;
        history = new ExecuteHistory(null, connId, 0L, ExecuteType.ALGORITHM,
                                     body.toString(), status,
                                     AsyncTaskStatus.UNKNOWN, -1L, createTime);
        this.historyService.save(history);
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(jsonView)
                            .tableView(tableView)
                            .graphView(graphView)
                            .build();
    }

    public GremlinResult algorithm(int connId, String name, Map<String, Object> body) {
        HugeClient client = this.getClient(connId);
        TraverserManager traverser = client.traverser();
        Map<String, Object> params = body == null ? Collections.emptyMap() : body;
        Object result;
        switch (name) {
            case "shortpath":
                result = traverser.shortestPath(value(params, "source"), value(params, "target"),
                                                direction(params), label(params),
                                                intValue(params, "max_depth", 1),
                                                longValue(params, "max_degree", 10000L),
                                                longValue(params, "skip_degree", 0L),
                                                longValue(params, "capacity", 10000000L));
                return this.shortestPathResult(traverser, (PathOfVertices) result);
            case "allshortpath":
                result = traverser.allShortestPaths(value(params, "source"),
                                                    value(params, "target"),
                                                    direction(params), label(params),
                                                    intValue(params, "max_depth", 1),
                                                    longValue(params, "max_degree", 10000L),
                                                    longValue(params, "skip_degree", 0L),
                                                    longValue(params, "capacity", 10000000L));
                return this.pathWithMeasureResult(traverser, (PathWithMeasure) result);
            case "paths":
                result = traverser.paths(value(params, "source"), value(params, "target"),
                                         direction(params), label(params),
                                         intValue(params, "max_depth", 1),
                                         longValue(params, "max_degree", 10000L),
                                         longValue(params, "capacity", 10000000L),
                                         longValue(params, "limit", 10L));
                return this.pathWithMeasureResult(traverser, (PathWithMeasure) result);
            case "rings":
                result = traverser.rings(value(params, "source"), direction(params),
                                         label(params), intValue(params, "max_depth", 1),
                                         booleanValue(params, "source_in_ring", true),
                                         longValue(params, "max_degree", 10000L),
                                         longValue(params, "capacity", 10000000L),
                                         longValue(params, "limit", 10L));
                return this.pathWithMeasureResult(traverser, (PathWithMeasure) result);
            case "crosspoints":
                result = traverser.crosspoint(value(params, "source"),
                                              value(params, "target"),
                                              direction(params), label(params),
                                              intValue(params, "max_depth", 1),
                                              longValue(params, "max_degree", 10000L),
                                              longValue(params, "capacity", 10000000L),
                                              longValue(params, "limit", 10L));
                return this.pathWithMeasureResult(traverser, (PathWithMeasure) result);
            case "fsimilarity":
                result = traverser.fusiformSimilarity(this.fusiformSimilarityRequest(params));
                return this.generalResult(result);
            case "neighborrank":
                result = traverser.neighborRank(this.neighborRankRequest(params));
                return this.generalResult(result);
            case "kneighbor":
                result = traverser.kneighbor(this.kneighborRequest(params));
                return this.kneighborResult((Kneighbor) result);
            case "kout":
                result = traverser.kout(this.koutRequest(params));
                return this.koutResult((Kout) result);
            case "customizedpaths":
                result = traverser.customizedPaths(this.customizedPathsRequest(params));
                return this.pathsWithVerticesResult((PathsWithVertices) result);
            case "rays":
                result = traverser.rays(value(params, "source"), direction(params),
                                        label(params), intValue(params, "max_depth", 1),
                                        longValue(params, "max_degree", 10000L),
                                        longValue(params, "capacity", 1000000L),
                                        longValue(params, "limit", 10L));
                return this.pathWithMeasureResult(traverser, (PathWithMeasure) result);
            case "sameneighbors":
                result = traverser.sameNeighbors(value(params, "vertex"),
                                                 value(params, "other"),
                                                 direction(params), label(params),
                                                 longValue(params, "max_degree", 10000L),
                                                 longValue(params, "limit", 10000000L));
                return this.generalResult(result);
            case "weightedshortpath":
                result = traverser.weightedShortestPath(value(params, "source"),
                                                        value(params, "target"),
                                                        direction(params), label(params),
                                                        stringValue(params, "weight", null),
                                                        longValue(params, "max_degree", 10000L),
                                                        longValue(params, "skip_degree", 0L),
                                                        longValue(params, "capacity", 10000000L),
                                                        booleanValue(params, "with_vertex", true),
                                                        true);
                return this.weightedPathResult((WeightedPath) result);
            case "singleshortpath":
                result = traverser.singleSourceShortestPath(value(params, "source"),
                                                            direction(params), label(params),
                                                            stringValue(params, "weight", null),
                                                            longValue(params, "max_degree", 10000L),
                                                            longValue(params, "skip_degree", 0L),
                                                            longValue(params, "capacity",
                                                                      10000000L),
                                                            longValue(params, "limit", 10L),
                                                            booleanValue(params, "with_vertex",
                                                                         true),
                                                            true);
                return this.weightedPathsResult((WeightedPaths) result);
            case "jaccardsimilarity":
                result = traverser.jaccardSimilarity(value(params, "vertex"),
                                                     value(params, "other"),
                                                     direction(params), label(params),
                                                     longValue(params, "max_degree", 10000L));
                return this.jaccardSimilarityResult((JaccardSimilarity) result);
            case "personalrank":
                result = traverser.personalRank(this.personalRankRequest(params));
                return this.generalResult(result);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + name);
        }
    }

    private GremlinResult shortestPathResult(TraverserManager traverser,
                                             PathOfVertices pathResult) {
        Path result = pathResult.getPath();
        JsonView jsonView = new JsonView();
        jsonView.setData(result.objects());
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(jsonView)
                            .tableView(this.buildPathTableView(result))
                            .graphView(this.buildPathGraphView(traverser, pathResult))
                            .build();
    }

    private GremlinResult pathWithMeasureResult(TraverserManager traverser,
                                                PathWithMeasure result) {
        List<Path> paths = this.paths(result);
        JsonView jsonView = new JsonView(new ArrayList<>(paths));
        TableView tableView = this.buildPathsTableView(paths);
        GraphView graphView = this.buildPathMeasureGraphView(traverser, result, paths);
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(jsonView)
                            .tableView(tableView)
                            .graphView(graphView)
                            .build();
    }

    private GremlinResult pathsWithVerticesResult(PathsWithVertices result) {
        List<Object> data = new ArrayList<>();
        if (result.paths() != null) {
            data.addAll(result.paths());
        }
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(new JsonView(data))
                            .tableView(this.buildCustomPathsTableView(result.paths()))
                            .graphView(this.graphView(result.vertices(), result.edges()))
                            .build();
    }

    private GremlinResult kneighborResult(Kneighbor result) {
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(new JsonView(this.graphPathData(result.paths(),
                                                                      result.ids())))
                            .tableView(this.buildGraphPathsTableView(result.paths(),
                                                                     result.ids()))
                            .graphView(this.graphView(result.vertices(), result.edges()))
                            .build();
    }

    private GremlinResult koutResult(Kout result) {
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(new JsonView(this.graphPathData(result.paths(),
                                                                      result.ids())))
                            .tableView(this.buildGraphPathsTableView(result.paths(),
                                                                     result.ids()))
                            .graphView(this.graphView(result.vertices(), result.edges()))
                            .build();
    }

    private GremlinResult weightedPathResult(WeightedPath result) {
        List<Object> data = new ArrayList<>();
        if (result.path() != null) {
            data.add(result.path());
        }
        List<Object> rows = new ArrayList<>();
        if (result.path() != null) {
            rows.add(ImmutableMap.of("path", result.path().vertices()));
        }
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(new JsonView(data))
                            .tableView(new TableView(TableView.PATH_HEADER, rows))
                            .graphView(this.graphView(result.vertices(), result.edges()))
                            .build();
    }

    private GremlinResult weightedPathsResult(WeightedPaths result) {
        List<Object> data = new ArrayList<>();
        List<Object> rows = new ArrayList<>();
        Map<Object, WeightedPath.Path> paths = result.paths();
        if (paths != null) {
            for (Map.Entry<Object, WeightedPath.Path> entry : paths.entrySet()) {
                data.add(entry);
                WeightedPath.Path path = entry.getValue();
                if (path != null) {
                    rows.add(ImmutableMap.of("path", path.vertices()));
                }
            }
        }
        return GremlinResult.builder()
                            .type(GremlinResult.Type.PATH)
                            .jsonView(new JsonView(data))
                            .tableView(new TableView(TableView.PATH_HEADER, rows))
                            .graphView(this.graphView(result.vertices(), result.edges()))
                            .build();
    }

    private GremlinResult generalResult(Object result) {
        List<Object> data = new ArrayList<>(1);
        data.add(result);
        List<Object> rows = new ArrayList<>(1);
        rows.add(ImmutableMap.of("result", result));
        return GremlinResult.builder()
                            .type(GremlinResult.Type.GENERAL)
                            .jsonView(new JsonView(data))
                            .tableView(new TableView(TableView.GENERAL_HEADER, rows))
                            .graphView(GraphView.EMPTY)
                            .build();
    }

    private GremlinResult jaccardSimilarityResult(JaccardSimilarity result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("jaccard_similarity", result.getJaccardSimilarity());
        value.put("measure", result.measure());
        return this.generalResult(value);
    }

    private FusiformSimilarityRequest fusiformSimilarityRequest(Map<String, Object> params) {
        FusiformSimilarityRequest.Builder builder = FusiformSimilarityRequest.builder();
        this.applyVertices(builder.sources(), value(params, "sources"), value(params, "source"));
        String label = label(params);
        if (label != null) {
            builder.label(label);
        }
        builder.direction(direction(params));
        builder.minNeighbors(intValue(params, "min_neighbors", 1));
        builder.alpha(doubleValue(params, "alpha", 1.0D));
        builder.minSimilars(intValue(params, "min_similars", 1));
        if (!isBlank(value(params, "top"))) {
            builder.top(intValue(params, "top", 1));
        }
        String groupProperty = stringValue(params, "group_property", null);
        if (groupProperty != null) {
            builder.groupProperty(groupProperty);
            builder.minGroups(intValue(params, "min_groups", 1));
        }
        builder.degree(longValue(params, "max_degree", 10000L));
        builder.capacity(longValue(params, "capacity", 10000000L));
        builder.limit(intValue(params, "limit", 10));
        builder.withIntermediary(booleanValue(params, "with_intermediary", false));
        builder.withVertex(booleanValue(params, "with_vertex", false));
        return builder.build();
    }

    private NeighborRankAPI.Request neighborRankRequest(Map<String, Object> params) {
        NeighborRankAPI.Request.Builder builder = NeighborRankAPI.Request.builder();
        builder.source(value(params, "source"));
        if (!isBlank(value(params, "alpha"))) {
            builder.alpha(doubleValue(params, "alpha", 0.85D));
        }
        builder.capacity(longValue(params, "capacity", 10000000L));

        List<Map<String, Object>> steps = stepMaps(value(params, "steps"));
        if (steps.isEmpty()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("direction", value(params, "direction"));
            step.put("labels", label(params) == null ? Collections.emptyList() :
                               Collections.singletonList(label(params)));
            step.put("degree", longValue(params, "max_degree", 10000L));
            step.put("top", longValue(params, "top", 100L));
            steps.add(step);
        }
        for (Map<String, Object> step : steps) {
            NeighborRankAPI.Request.Step.Builder stepBuilder = builder.steps();
            stepBuilder.direction(direction(step));
            List<String> labels = labels(value(step, "labels"));
            if (!labels.isEmpty()) {
                stepBuilder.labels(labels);
            }
            stepBuilder.degree(longValue(step, "degree", 10000L));
            stepBuilder.top(intValue(step, "top", 100));
        }
        return builder.build();
    }

    private KneighborRequest kneighborRequest(Map<String, Object> params) {
        KneighborRequest.Builder builder = KneighborRequest.builder();
        builder.source(value(params, "source"));
        builder.maxDepth(intValue(params, "max_depth", 1));
        builder.limit(longValue(params, "limit", 10000000L));
        builder.withVertex(true);
        builder.withEdge(true);
        builder.withPath(true);
        this.applySteps(builder.steps(), params);
        return builder.build();
    }

    private KoutRequest koutRequest(Map<String, Object> params) {
        KoutRequest.Builder builder = KoutRequest.builder();
        builder.source(value(params, "source"));
        builder.maxDepth(intValue(params, "max_depth", 1));
        builder.nearest(booleanValue(params, "nearest", true));
        builder.capacity(longValue(params, "capacity", 10000000L));
        builder.limit(longValue(params, "limit", 10000000L));
        builder.withVertex(true);
        builder.withEdge(true);
        builder.withPath(true);
        this.applySteps(builder.steps(), params);
        return builder.build();
    }

    private CustomizedPathsRequest customizedPathsRequest(Map<String, Object> params) {
        CustomizedPathsRequest.Builder builder = CustomizedPathsRequest.builder();
        this.applyVertices(builder.sources(), value(params, "sources"), value(params, "source"));
        builder.sortBy(sortBy(stringValue(params, "sort_by", "NONE")));
        builder.capacity(longValue(params, 10000000L, "capacity", "capactiy"));
        builder.limit(longValue(params, "limit", 10L));
        builder.withVertex(true);
        builder.withEdge(true);

        List<Map<String, Object>> steps = stepMaps(value(params, "steps"));
        if (steps.isEmpty()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("direction", value(params, "direction"));
            step.put("labels", label(params) == null ? Collections.emptyList() :
                               Collections.singletonList(label(params)));
            step.put("degree", longValue(params, "max_degree", 10000L));
            steps.add(step);
        }
        for (Map<String, Object> step : steps) {
            CustomizedPathsRequest.Step.Builder stepBuilder = builder.steps();
            stepBuilder.direction(direction(step));
            List<String> labels = labels(value(step, "labels"));
            if (!labels.isEmpty()) {
                stepBuilder.labels(labels);
            }
            Map<String, Object> properties = properties(value(step, "properties"));
            if (!properties.isEmpty()) {
                stepBuilder.properties(properties);
            }
            String weightBy = stringValue(step, "weight_by", null);
            if (weightBy != null && !"__CUSTOM_WEIGHT__".equals(weightBy)) {
                stepBuilder.weightBy(weightBy);
            }
            if (!isBlank(value(step, "default_weight"))) {
                stepBuilder.defaultWeight(doubleValue(step, "default_weight", 1.0D));
            }
            stepBuilder.degree(longValue(step, "degree", 10000L));
            if (!isBlank(value(step, "sample"))) {
                stepBuilder.sample(intValue(step, "sample", 100));
            }
        }
        return builder.build();
    }

    private PersonalRankAPI.Request personalRankRequest(Map<String, Object> params) {
        PersonalRankAPI.Request.Builder builder = PersonalRankAPI.Request.builder();
        builder.source(value(params, "source"));
        builder.label(stringValue(params, "label", null));
        if (!isBlank(value(params, "alpha"))) {
            builder.alpha(doubleValue(params, "alpha", 0.85D));
        }
        builder.degree(longValue(params, 10000L, "degree", "max_degree"));
        builder.limit(longValue(params, "limit", 10000000L));
        builder.maxDepth(intValue(params, "max_depth", 5));
        builder.withLabel(withLabel(stringValue(params, "with_label", "BOTH_LABEL")));
        builder.sorted(booleanValue(params, "sorted", true));
        return builder.build();
    }

    private void applyVertices(VerticesArgs.Builder builder, Object sources,
                               Object fallbackSource) {
        if (sources instanceof Map) {
            Map<String, Object> sourceMap = asMap(sources);
            Set<Object> ids = objectSet(value(sourceMap, "ids"));
            if (!ids.isEmpty()) {
                builder.ids(ids);
            }
            String label = stringValue(sourceMap, "label", null);
            if (label != null) {
                builder.label(label);
            }
            Map<String, Object> properties = properties(value(sourceMap, "properties"));
            if (!properties.isEmpty()) {
                builder.properties(properties);
            }
            return;
        }

        Set<Object> ids = objectSet(sources);
        if (ids.isEmpty()) {
            ids = objectSet(fallbackSource);
        }
        if (!ids.isEmpty()) {
            builder.ids(ids);
        }
    }

    private void applySteps(Steps.Builder builder, Map<String, Object> params) {
        builder.direction(direction(params));
        builder.degree(longValue(params, "max_degree", 10000L));
        builder.skipDegree(longValue(params, "skip_degree", 0L));
        String label = label(params);
        if (label != null) {
            builder.edgeSteps(new Steps.StepEntity(label));
        }
    }

    private List<Path> paths(PathWithMeasure result) {
        if (result.getPaths() != null) {
            return result.getPaths();
        }
        if (result.getRings() != null) {
            return result.getRings();
        }
        if (result.getCrosspoints() != null) {
            return result.getCrosspoints();
        }
        if (result.getRays() != null) {
            return result.getRays();
        }
        return Collections.emptyList();
    }

    private TableView buildPathsTableView(List<Path> paths) {
        List<Object> rows = new ArrayList<>();
        for (Path path : paths) {
            rows.add(ImmutableMap.of("path", this.pathIds(path)));
        }
        return new TableView(TableView.PATH_HEADER, rows);
    }

    private TableView buildCustomPathsTableView(List<PathsWithVertices.Paths> paths) {
        List<Object> rows = new ArrayList<>();
        if (paths != null) {
            for (PathsWithVertices.Paths path : paths) {
                rows.add(ImmutableMap.of("path", path.objects()));
            }
        }
        return new TableView(TableView.PATH_HEADER, rows);
    }

    private TableView buildGraphPathsTableView(List<Path> paths, Set<Object> ids) {
        if (paths != null && !paths.isEmpty()) {
            return this.buildPathsTableView(paths);
        }
        List<Object> rows = new ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            rows.add(ImmutableMap.of("path", new ArrayList<>(ids)));
        }
        return new TableView(TableView.PATH_HEADER, rows);
    }

    private List<Object> graphPathData(List<Path> paths, Set<Object> ids) {
        List<Object> data = new ArrayList<>();
        if (paths != null && !paths.isEmpty()) {
            data.addAll(paths);
        } else if (ids != null) {
            data.addAll(ids);
        }
        return data;
    }

    private GraphView buildPathMeasureGraphView(TraverserManager traverser,
                                                PathWithMeasure result,
                                                List<Path> paths) {
        Map<Object, Vertex> vertices = new LinkedHashMap<>();
        Map<String, Edge> edges = new LinkedHashMap<>();
        Set<Object> vertexIds = new LinkedHashSet<>();
        for (Path path : paths) {
            this.collectPathElements(path, vertices, edges, vertexIds);
        }

        List<String> resultVertexIds = result.getVertices();
        if (resultVertexIds != null && !resultVertexIds.isEmpty()) {
            this.putVertices(vertices, this.queryVertices(traverser, resultVertexIds));
        } else if (vertices.isEmpty() && !vertexIds.isEmpty()) {
            this.putVertices(vertices, this.queryVertices(traverser, vertexIds));
        }

        List<String> resultEdgeIds = result.getEdges();
        if (resultEdgeIds != null && !resultEdgeIds.isEmpty()) {
            this.putEdges(edges, this.queryEdges(traverser, resultEdgeIds));
        }
        return new GraphView(vertices.values(), edges.values());
    }

    private GraphView graphView(Collection<Vertex> vertices, Collection<Edge> edges) {
        Collection<Vertex> graphVertices = vertices == null ? Collections.emptyList() :
                                           vertices;
        Collection<Edge> graphEdges = edges == null ? Collections.emptyList() : edges;
        return new GraphView(graphVertices, graphEdges);
    }

    private void collectPathElements(Path path, Map<Object, Vertex> vertices,
                                     Map<String, Edge> edges, Set<Object> vertexIds) {
        if (path == null || path.objects() == null) {
            return;
        }
        for (Object element : path.objects()) {
            if (element instanceof Vertex) {
                Vertex vertex = (Vertex) element;
                vertices.put(vertex.id(), vertex);
            } else if (element instanceof Edge) {
                Edge edge = (Edge) element;
                edges.put(edge.id(), edge);
            } else if (element != null) {
                vertexIds.add(element);
            }
        }
    }

    private List<Vertex> queryVertices(TraverserManager traverser,
                                       Collection<?> vertexIds) {
        if (vertexIds == null || vertexIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return traverser.vertices(new ArrayList<>(vertexIds));
        } catch (RuntimeException e) {
            log.warn("Failed to query vertices for algorithm graph view", e);
            return Collections.emptyList();
        }
    }

    private List<Edge> queryEdges(TraverserManager traverser, Collection<String> edgeIds) {
        if (edgeIds == null || edgeIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return traverser.edges(new ArrayList<>(edgeIds));
        } catch (RuntimeException e) {
            log.warn("Failed to query edges for algorithm graph view", e);
            return Collections.emptyList();
        }
    }

    private void putVertices(Map<Object, Vertex> vertices, Collection<Vertex> values) {
        for (Vertex vertex : values) {
            if (vertex != null) {
                vertices.put(vertex.id(), vertex);
            }
        }
    }

    private void putEdges(Map<String, Edge> edges, Collection<Edge> values) {
        for (Edge edge : values) {
            if (edge != null) {
                edges.put(edge.id(), edge);
            }
        }
    }

    private TableView buildPathTableView(Path result) {
        List<Object> paths = new ArrayList<>(1);
        paths.add(ImmutableMap.of("path", this.pathIds(result)));
        return new TableView(TableView.PATH_HEADER, paths);
    }

    private List<Object> pathIds(Path result) {
        List<Object> ids = new ArrayList<>();
        result.objects().forEach(element -> {
            if (element instanceof Vertex) {
                ids.add(((Vertex) element).id());
            } else if (element instanceof Edge) {
                ids.add(((Edge) element).id());
            } else {
                ids.add(element);
            }
        });
        return ids;
    }

    private static Direction direction(Map<String, Object> params) {
        String value = stringValue(params, "direction", "BOTH");
        return Direction.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String label(Map<String, Object> params) {
        String label = stringValue(params, "label", null);
        return ALL_LABELS.equals(label) ? null : label;
    }

    private static Object value(Map<String, Object> params, String key) {
        return params.get(key);
    }

    private static int intValue(Map<String, Object> params, String key,
                                int defaultValue) {
        Object value = value(params, key);
        if (isBlank(value)) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static long longValue(Map<String, Object> params, String key,
                                  long defaultValue) {
        return longValue(params, defaultValue, key);
    }

    private static long longValue(Map<String, Object> params, long defaultValue,
                                  String... keys) {
        for (String key : keys) {
            Object value = value(params, key);
            if (!isBlank(value)) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(value.toString());
            }
        }
        return defaultValue;
    }

    private static double doubleValue(Map<String, Object> params, String key,
                                      double defaultValue) {
        Object value = value(params, key);
        if (isBlank(value)) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static boolean booleanValue(Map<String, Object> params, String key,
                                        boolean defaultValue) {
        Object value = value(params, key);
        if (isBlank(value)) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static String stringValue(Map<String, Object> params, String key,
                                      String defaultValue) {
        Object value = value(params, key);
        if (isBlank(value)) {
            return defaultValue;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> properties(Object value) {
        Map<String, Object> properties = asMap(value);
        return properties.isEmpty() ? Collections.emptyMap() : properties;
    }

    private static Set<Object> objectSet(Object value) {
        Set<Object> ids = new LinkedHashSet<>();
        if (value == null) {
            return ids;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                if (!isBlank(item)) {
                    ids.add(item);
                }
            }
        } else if (value.getClass().isArray()) {
            Object[] items = (Object[]) value;
            for (Object item : items) {
                if (!isBlank(item)) {
                    ids.add(item);
                }
            }
        } else if (!isBlank(value)) {
            ids.add(value);
        }
        return ids;
    }

    private static List<String> labels(Object value) {
        List<String> labels = new ArrayList<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                String label = item == null ? null : item.toString();
                if (!isBlank(label) && !ALL_LABELS.equals(label)) {
                    labels.add(label);
                }
            }
        } else if (!isBlank(value) && !ALL_LABELS.equals(value.toString())) {
            labels.add(value.toString());
        }
        return labels;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stepMaps(Object value) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (!(value instanceof Iterable)) {
            return steps;
        }
        for (Object step : (Iterable<?>) value) {
            if (step instanceof Map) {
                steps.add((Map<String, Object>) step);
            }
        }
        return steps;
    }

    private static CustomizedPathsRequest.SortBy sortBy(String value) {
        return CustomizedPathsRequest.SortBy.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static PersonalRankAPI.Request.WithLabel withLabel(String value) {
        return PersonalRankAPI.Request.WithLabel.valueOf(
                value.toUpperCase(Locale.ROOT));
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private GraphView buildPathGraphView(TraverserManager traverser, PathOfVertices result) {
        List<Object> vertexIds = this.vertexIdsOfPath(result);
        if (vertexIds.isEmpty()) {
            return GraphView.EMPTY;
        }
        List<Vertex> vertices;
        try {
            vertices = traverser.vertices(vertexIds);
        } catch (RuntimeException e) {
            log.warn("Failed to query vertices for shortestPath graph view", e);
            return GraphView.EMPTY;
        }

        List<Edge> edges = Collections.emptyList();
        List<String> edgeIds = result.getEdges();
        if (edgeIds != null && !edgeIds.isEmpty()) {
            try {
                edges = traverser.edges(edgeIds);
            } catch (RuntimeException e) {
                log.warn("Failed to query edges for shortestPath graph view", e);
            }
        }
        return new GraphView(vertices, edges);
    }

    private List<Object> vertexIdsOfPath(PathOfVertices result) {
        Set<Object> ids = new LinkedHashSet<>();
        List<String> vertexIds = result.getVertices();
        if (vertexIds != null && !vertexIds.isEmpty()) {
            ids.addAll(vertexIds);
            return new ArrayList<>(ids);
        }

        List<Object> elements = result.getPath().objects();
        for (Object element : elements) {
            if (element instanceof Vertex) {
                ids.add(((Vertex) element).id());
            } else if (!(element instanceof Edge)) {
                ids.add(element);
            }
        }
        return new ArrayList<>(ids);
    }
}
