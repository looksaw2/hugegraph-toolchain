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

package org.apache.hugegraph.loader.test.unit;

import java.util.Arrays;

import org.junit.Test;

import org.apache.hugegraph.loader.constant.ElemType;
import org.apache.hugegraph.loader.mapping.EdgeMapping;
import org.apache.hugegraph.loader.mapping.InputStruct;
import org.apache.hugegraph.loader.mapping.LoadMapping;
import org.apache.hugegraph.loader.mapping.VertexMapping;
import org.apache.hugegraph.loader.metrics.LoadMetrics;
import org.apache.hugegraph.loader.metrics.LoadSummary;
import org.apache.hugegraph.testutil.Assert;

public class LoadMetricsTest {

    @Test
    public void testLoadMetricsCounters() {
        InputStruct struct = newStruct("1");
        VertexMapping vertex = struct.vertices().get(0);
        EdgeMapping edge = struct.edges().get(0);
        LoadMetrics metrics = new LoadMetrics(struct);

        metrics.readSuccess(2L);
        metrics.plusReadSuccess(3L);
        metrics.increaseReadSuccess();
        metrics.readFailure(4L);
        metrics.increaseReadFailure();
        metrics.startInFlight();
        metrics.plusFlighting(6);
        metrics.minusFlighting(2);
        metrics.stopInFlight();
        metrics.plusParseSuccess(vertex, 7L);
        metrics.increaseParseFailure(vertex);
        metrics.plusInsertSuccess(vertex, 11L);
        metrics.increaseInsertFailure(vertex);
        metrics.plusParseSuccess(edge, 13L);
        metrics.increaseParseFailure(edge);
        metrics.plusInsertSuccess(edge, 17L);
        metrics.increaseInsertFailure(edge);

        Assert.assertEquals(6L, metrics.readSuccess());
        Assert.assertEquals(5L, metrics.readFailure());
        Assert.assertEquals(7L, metrics.parseSuccess(vertex));
        Assert.assertEquals(1L, metrics.parseFailure(vertex));
        Assert.assertEquals(11L, metrics.insertSuccess(vertex));
        Assert.assertEquals(1L, metrics.insertFailure(vertex));
        Assert.assertEquals(13L, metrics.parseSuccess(edge));
        Assert.assertEquals(1L, metrics.parseFailure(edge));
        Assert.assertEquals(17L, metrics.insertSuccess(edge));
        Assert.assertEquals(1L, metrics.insertFailure(edge));
        Assert.assertEquals(2L, metrics.totalParseFailures());
        Assert.assertEquals(2L, metrics.totalInsertFailures());
        Assert.assertEquals(1, metrics.vertexMetrics().size());
        Assert.assertEquals(1, metrics.edgeMetrics().size());
    }

    @Test
    public void testLoadSummaryAggregatesMetrics() {
        InputStruct first = newStruct("1");
        InputStruct second = newStruct("2");
        LoadMapping mapping = new LoadMapping(Arrays.asList(first, second));
        LoadSummary summary = new LoadSummary();

        summary.initMetrics(mapping);
        LoadMetrics firstMetrics = summary.metrics(first);
        LoadMetrics secondMetrics = summary.metrics(second);
        firstMetrics.readSuccess(10L);
        firstMetrics.readFailure(1L);
        firstMetrics.increaseParseFailure(first.vertices().get(0));
        firstMetrics.increaseInsertFailure(first.edges().get(0));
        secondMetrics.readSuccess(20L);
        secondMetrics.readFailure(2L);
        secondMetrics.increaseParseFailure(second.edges().get(0));
        secondMetrics.increaseInsertFailure(second.vertices().get(0));

        summary.plusLoaded(ElemType.VERTEX, 3);
        summary.plusLoaded(ElemType.EDGE, 4);

        Assert.assertSame(firstMetrics, summary.inputMetricsMap().get("1"));
        Assert.assertEquals(3L, summary.vertexLoaded());
        Assert.assertEquals(4L, summary.edgeLoaded());
        Assert.assertEquals(33L, summary.totalReadLines());
        Assert.assertEquals(30L, summary.totalReadSuccess());
        Assert.assertEquals(3L, summary.totalReadFailures());
        Assert.assertEquals(2L, summary.totalParseFailures());
        Assert.assertEquals(2L, summary.totalInsertFailures());
    }

    @Test
    public void testLoadSummaryTimeAndRate() {
        LoadSummary summary = new LoadSummary();

        Assert.assertEquals(-1L, summary.loadRate(ElemType.VERTEX));

        summary.plusLoaded(ElemType.VERTEX, 6);
        summary.plusLoaded(ElemType.EDGE, 8);
        summary.addTimeRange(ElemType.VERTEX, 100L, 250L);
        summary.addTimeRange(ElemType.EDGE, 200L, 500L);
        summary.calculateTotalTime(ElemType.VERTEX);
        summary.calculateTotalTime(ElemType.EDGE);

        Assert.assertEquals(150L, summary.vertexTime());
        Assert.assertEquals(300L, summary.edgeTime());
        Assert.assertEquals(400L, summary.loadTime());
        Assert.assertEquals(40L, summary.loadRate(ElemType.VERTEX));
        Assert.assertEquals(26L, summary.loadRate(ElemType.EDGE));

        summary.startTotalTimer();
        summary.startTotalTimer();
        summary.stopTotalTimer();
        summary.stopTotalTimer();
        Assert.assertTrue(summary.totalTime() >= 0L);
    }

    private static InputStruct newStruct(String id) {
        VertexMapping vertex = new VertexMapping("name", false);
        vertex.label("person" + id);
        EdgeMapping edge = new EdgeMapping(Arrays.asList("source"), false,
                                           Arrays.asList("target"), false);
        edge.label("knows" + id);
        InputStruct struct = new InputStruct(null, null);
        struct.id(id);
        struct.add(vertex);
        struct.add(edge);
        return struct;
    }
}
