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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hugegraph.api.traverser.JaccardSimilarityAPI;
import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.rest.RestHeaders;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.constant.Direction;
import org.apache.hugegraph.structure.traverser.JaccardSimilarity;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class JaccardSimilarityAPITest extends BaseUnitTest {

    private RestClient client;
    private JaccardSimilarityAPI api;

    @Before
    public void setup() {
        this.client = Mockito.mock(RestClient.class);
        this.api = new JaccardSimilarityAPI(this.client, "DEFAULT", "hugegraph");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDeserializeWrappedNestedJaccardSimilarity() {
        String json = "{"
                      + "\"jaccard_similarity\":{"
                      + "\"node:1\":{\"node:2\":0.5}"
                      + "},"
                      + "\"measure\":{\"edge_iterations\":3,"
                      + "\"vertice_iterations\":2,\"cost(ns)\":1}"
                      + "}";
        RestResult result = new RestResult(200, json, new RestHeaders());
        Mockito.when(this.client.get(Mockito.anyString(), Mockito.anyMap()))
               .thenReturn(result);

        JaccardSimilarity jaccard = this.api.get("node:1", "node:2",
                                                 Direction.BOTH, "link",
                                                 10000L);

        Map<Object, Object> similarities = jaccard.similarsMap();
        Assert.assertEquals(1, similarities.size());
        Assert.assertTrue(similarities.get("node:1") instanceof Map);
        Map<Object, Object> nested = (Map<Object, Object>) similarities.get("node:1");
        Assert.assertEquals(0.5D, nested.get("node:2"));
        Assert.assertEquals(3L, jaccard.measure().edgeIters().longValue());
    }

    @Test
    public void testDeserializeWrappedScalarJaccardSimilarity() {
        String json = "{"
                      + "\"jaccard_similarity\":0.3333333333333333,"
                      + "\"measure\":{\"edge_iterations\":8,"
                      + "\"vertice_iterations\":2,\"cost(ns)\":312800}"
                      + "}";
        RestResult result = new RestResult(200, json, new RestHeaders());
        Mockito.when(this.client.get(Mockito.anyString(), Mockito.anyMap()))
               .thenReturn(result);

        JaccardSimilarity jaccard = this.api.get("node:1", "node:2",
                                                 Direction.BOTH, "link",
                                                 10000L);

        Assert.assertEquals(0.3333333333333333D,
                            (Double) jaccard.getJaccardSimilarity(),
                            Double.MIN_VALUE);
        Assert.assertEquals(8L, jaccard.measure().edgeIters().longValue());
    }

    @Test
    public void testDeserializeLegacyFlatJaccardSimilarity() {
        String json = "{\"node:2\":0.5}";
        RestResult result = new RestResult(200, json, new RestHeaders());
        Mockito.when(this.client.get(Mockito.anyString(), Mockito.anyMap()))
               .thenReturn(result);

        JaccardSimilarity jaccard = this.api.get("node:1", "node:2",
                                                 Direction.BOTH, "link",
                                                 10000L);

        Assert.assertEquals(0.5D, jaccard.get("node:2"));
    }

    @Test
    public void testDeserializeLegacySimilarsMapAlias() {
        String json = "{"
                      + "\"similarsMap\":{\"node:2\":0.5},"
                      + "\"measure\":{\"edge_iterations\":4,"
                      + "\"vertice_iterations\":2,\"cost(ns)\":10}"
                      + "}";
        RestResult result = new RestResult(200, json, new RestHeaders());
        Mockito.when(this.client.get(Mockito.anyString(), Mockito.anyMap()))
               .thenReturn(result);

        JaccardSimilarity jaccard = this.api.get("node:1", "node:2",
                                                 Direction.BOTH, "link",
                                                 10000L);

        Assert.assertEquals(0.5D, jaccard.get("node:2"));
        Assert.assertEquals(4L, jaccard.measure().edgeIters().longValue());
    }

    @Test
    public void testScalarSimilarityExposesMapCompatibilityMethods() {
        JaccardSimilarity jaccard = new JaccardSimilarity();
        jaccard.setJaccardSimilarity(0.25D);

        Assert.assertEquals(1, jaccard.size());
        Assert.assertTrue(jaccard.keySet().contains("jaccard_similarity"));
        Assert.assertEquals(0.25D, jaccard.get("jaccard_similarity"));
        Assert.assertEquals(0.25D, jaccard.getJaccardSimilarity());
    }

    @Test
    public void testMultiEntrySimilarityMapReturnsWholeMap() {
        JaccardSimilarity jaccard = new JaccardSimilarity();
        Map<Object, Object> similarities = new LinkedHashMap<>();
        similarities.put("node:2", 0.5D);
        similarities.put("node:3", 0.25D);
        jaccard.setSimilarsMap(similarities);

        Assert.assertEquals(2, jaccard.size());
        Assert.assertEquals(similarities, jaccard.getJaccardSimilarity());
    }
}
