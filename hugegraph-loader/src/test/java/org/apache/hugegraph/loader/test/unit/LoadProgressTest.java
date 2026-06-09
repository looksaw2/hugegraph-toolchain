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

import java.io.File;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

import org.apache.hugegraph.loader.constant.Constants;
import org.apache.hugegraph.loader.executor.LoadOptions;
import org.apache.hugegraph.loader.progress.LoadProgress;
import org.apache.hugegraph.loader.util.JsonUtil;

public class LoadProgressTest {

    @Test
    public void testTotalLoaded() {
        String json = "{" +
                      "\"vertex_progress\": 16," +
                      "\"edge_progress\": 12," +
                      "\"input_progress\": {" +
                      "    \"1\":{" +
                      "        \"type\":\"FILE\"," +
                      "        \"loaded_items\":{" +
                      "            \"vertex_person.csv\":{" +
                      "                \"name\":\"vertex_person.csv\"," +
                      "                \"last_modified\":1574346235000," +
                      "                \"checksum\":\"4250397517\"," +
                      "                \"offset\":6" +
                      "            }" +
                      "        }," +
                      "        \"loading_items\":{}" +
                      "    }," +
                      "    \"2\":{" +
                      "        \"type\":\"FILE\"," +
                      "        \"loaded_items\":{" +
                      "            \"vertex_software.txt\":{" +
                      "                \"name\":\"vertex_software.txt\"," +
                      "                \"last_modified\":1575427304000," +
                      "                \"checksum\":\"2992253526\"," +
                      "                \"offset\":2" +
                      "            }" +
                      "        }," +
                      "        \"loading_items\":{}" +
                      "    }," +
                      "    \"3\":{" +
                      "        \"type\":\"FILE\"," +
                      "        \"loaded_items\":{" +
                      "            \"edge_knows.json\":{" +
                      "                \"name\":\"edge_knows.json\"," +
                      "                \"last_modified\":1576658150000," +
                      "                \"checksum\":\"3108779382\"," +
                      "                \"offset\":2" +
                      "            }" +
                      "        }," +
                      "        \"loading_items\":{}" +
                      "    }," +
                      "    \"4\":{" +
                      "        \"type\":\"FILE\"," +
                      "        \"loaded_items\":{" +
                      "            \"edge_created.json\":{" +
                      "                \"name\":\"edge_created.json\"," +
                      "                \"last_modified\":1576659393000," +
                      "                \"checksum\":\"1026646359\"," +
                      "                \"offset\":4" +
                      "            }" +
                      "        }," +
                      "        \"loading_items\":{}" +
                      "    }" +
                      "}}";
        LoadProgress progress = JsonUtil.fromJson(json, LoadProgress.class);
        Assert.assertEquals(16, progress.vertexLoaded());
        Assert.assertEquals(12, progress.edgeLoaded());
    }

    @Test
    public void testTotalInputRead() {
        String json = "{" +
                      "\"vertex_progress\": 0," +
                      "\"edge_progress\": 0," +
                      "\"input_progress\": {" +
                      "    \"1\":{" +
                      "        \"type\":\"FILE\"," +
                      "        \"loaded_items\":{" +
                      "            \"vertex_person.csv\":{" +
                      "                \"name\":\"vertex_person.csv\"," +
                      "                \"last_modified\":1574346235000," +
                      "                \"checksum\":\"4250397517\"," +
                      "                \"offset\":6" +
                      "            }" +
                      "        }," +
                      "        \"loading_items\":{" +
                      "            \"vertex_software.txt\":{" +
                      "                \"name\":\"vertex_software.txt\"," +
                      "                \"last_modified\":1575427304000," +
                      "                \"checksum\":\"2992253526\"," +
                      "                \"offset\":2" +
                      "            }" +
                      "        }" +
                      "    }," +
                      "    \"2\":{" +
                      "        \"type\":\"FILE\"," +
                      "        \"loaded_items\":{}," +
                      "        \"loading_items\":{" +
                      "            \"edge_knows.json\":{" +
                      "                \"name\":\"edge_knows.json\"," +
                      "                \"last_modified\":1576658150000," +
                      "                \"checksum\":\"3108779382\"," +
                      "                \"offset\":4" +
                      "            }" +
                      "        }" +
                      "    }" +
                      "}}";

        LoadProgress progress = JsonUtil.fromJson(json, LoadProgress.class);

        Assert.assertEquals(12, progress.totalInputRead());
    }

    @Test
    public void testPlusLoaded() {
        LoadProgress progress = new LoadProgress();

        progress.plusVertexLoaded(3L);
        progress.plusVertexLoaded(5L);
        progress.plusEdgeLoaded(7L);
        progress.plusEdgeLoaded(11L);

        Assert.assertEquals(8L, progress.vertexLoaded());
        Assert.assertEquals(18L, progress.edgeLoaded());
    }

    @Test
    public void testReadProgressFile() throws Exception {
        File file = File.createTempFile("load-progress-", ".json",
                                        new File("."));
        try {
            FileUtils.writeStringToFile(file, minimalProgressJson(4L, 9L),
                                        Constants.CHARSET);

            LoadProgress progress = LoadProgress.read(file);

            Assert.assertEquals(4L, progress.vertexLoaded());
            Assert.assertEquals(9L, progress.edgeLoaded());
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testParseReturnsEmptyProgressWhenIncrementalDisabled() {
        LoadOptions options = new LoadOptions();
        options.incrementalMode = false;

        LoadProgress progress = LoadProgress.parse(options);

        Assert.assertEquals(0L, progress.vertexLoaded());
        Assert.assertEquals(0L, progress.edgeLoaded());
        Assert.assertTrue(progress.inputProgress().isEmpty());
    }

    @Test
    public void testParseReturnsEmptyProgressWhenDirectoryMissing()
                                                     throws Exception {
        File mapping = File.createTempFile("load-progress-mapping-", ".json",
                                           new File("."));
        try {
            LoadOptions options = new LoadOptions();
            options.incrementalMode = true;
            options.file = mapping.getPath();

            LoadProgress progress = LoadProgress.parse(options);

            Assert.assertEquals(0L, progress.vertexLoaded());
            Assert.assertEquals(0L, progress.edgeLoaded());
            Assert.assertTrue(progress.inputProgress().isEmpty());
        } finally {
            FileUtils.deleteQuietly(mapping);
        }
    }

    @Test
    public void testParseReadsLatestProgressFile() throws Exception {
        File mapping = File.createTempFile("load-progress-mapping-", ".json",
                                           new File("."));
        String prefix = mapping.getPath().substring(0,
                                                    mapping.getPath()
                                                           .lastIndexOf('.'));
        File progressDir = new File(prefix);
        try {
            FileUtils.forceMkdir(progressDir);
            File oldProgress = new File(progressDir, "load-progress_20240101");
            File newProgress = new File(progressDir, "load-progress_20250101");
            File unrelated = new File(progressDir, "other-progress_20260101");
            FileUtils.writeStringToFile(oldProgress,
                                        minimalProgressJson(1L, 2L),
                                        Constants.CHARSET);
            FileUtils.writeStringToFile(newProgress,
                                        minimalProgressJson(5L, 8L),
                                        Constants.CHARSET);
            FileUtils.writeStringToFile(unrelated,
                                        minimalProgressJson(13L, 21L),
                                        Constants.CHARSET);

            LoadOptions options = new LoadOptions();
            options.incrementalMode = true;
            options.file = mapping.getPath();
            LoadProgress progress = LoadProgress.parse(options);

            Assert.assertEquals(5L, progress.vertexLoaded());
            Assert.assertEquals(8L, progress.edgeLoaded());
        } finally {
            FileUtils.deleteQuietly(mapping);
            FileUtils.deleteQuietly(progressDir);
        }
    }

    @Test
    public void testFormatUsesMappingFilePrefix() {
        LoadOptions options = new LoadOptions();
        options.file = Paths.get("target", "progress-case",
                                 "mapping.json").toString();

        String fileName = LoadProgress.format(options, "20260609195512");

        String expected = Paths.get("target", "progress-case", "mapping",
                                    "load-progress_20260609195512").toString();
        Assert.assertEquals(expected, fileName);
    }

    private static String minimalProgressJson(long vertexLoaded,
                                              long edgeLoaded) {
        return "{" +
               "\"vertex_progress\":" + vertexLoaded + "," +
               "\"edge_progress\":" + edgeLoaded + "," +
               "\"input_progress\":{}" +
               "}";
    }
}
