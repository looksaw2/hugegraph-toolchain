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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hadoop.fs.Path;
import org.junit.Test;

import org.apache.hugegraph.loader.mapping.InputStruct;
import org.apache.hugegraph.loader.progress.FileItemProgress;
import org.apache.hugegraph.loader.progress.InputItemProgress;
import org.apache.hugegraph.loader.progress.InputProgress;
import org.apache.hugegraph.loader.reader.Readable;
import org.apache.hugegraph.loader.source.SourceType;
import org.apache.hugegraph.loader.source.file.FileSource;
import org.apache.hugegraph.testutil.Assert;

public class InputProgressTest {

    @Test
    public void testConstructFromInputStruct() {
        InputStruct struct = new InputStruct(null, null);
        FileSource source = new FileSource();
        source.path("vertex.csv");
        struct.input(source);

        InputProgress progress = new InputProgress(struct);

        Assert.assertTrue(progress.loadedItems().isEmpty());
        Assert.assertTrue(progress.loadingItems().isEmpty());
    }

    @Test
    public void testAddAndLookupItems() {
        InputProgress progress = newProgress();
        FileItemProgress loaded = item("loaded.csv", 100L, "a", 3L);
        FileItemProgress loading = item("loading.csv", 200L, "b", 5L);

        progress.addLoadedItem(loaded.name(), loaded);
        progress.addLoadingItem(loading.name(), loading);

        Assert.assertSame(loaded, progress.loadedItem("loaded.csv"));
        Assert.assertSame(loading, progress.loadingItem("loading.csv"));
        Assert.assertSame(loaded,
                          progress.matchLoadedItem(item("loaded.csv", 100L,
                                                        "a", 99L)));
        Assert.assertSame(loading,
                          progress.matchLoadingItem(item("loading.csv", 200L,
                                                         "b", 99L)));
        Assert.assertNull(progress.matchLoadedItem(item("missing.csv", 100L,
                                                       "a", 3L)));
        Assert.assertNull(progress.matchLoadingItem(item("missing.csv", 200L,
                                                        "b", 5L)));
    }

    @Test
    public void testMarkLoadedIgnoresWhenMarkAllIsFalse() {
        InputProgress progress = newProgress();
        FileItemProgress item = item("loading.csv", 200L, "b", 5L);
        progress.addLoadingItem(item.name(), item);

        progress.markLoaded(readable("loading.csv"), false);

        Assert.assertNull(progress.loadedItem("loading.csv"));
        Assert.assertSame(item, progress.loadingItem("loading.csv"));
    }

    @Test
    public void testMarkLoadedMovesReadableItem() {
        InputProgress progress = newProgress();
        FileItemProgress moved = item("loading.csv", 200L, "b", 5L);
        FileItemProgress kept = item("other.csv", 300L, "c", 7L);
        progress.addLoadingItem(moved.name(), moved);
        progress.addLoadingItem(kept.name(), kept);

        progress.markLoaded(readable("loading.csv"), true);

        Assert.assertSame(moved, progress.loadedItem("loading.csv"));
        Assert.assertNull(progress.loadingItem("loading.csv"));
        Assert.assertSame(kept, progress.loadingItem("other.csv"));
    }

    @Test
    public void testMarkLoadedMovesAllItemsWhenReadableIsNull() {
        InputProgress progress = newProgress();
        FileItemProgress first = item("first.csv", 100L, "a", 3L);
        FileItemProgress second = item("second.csv", 200L, "b", 5L);
        progress.addLoadingItem(first.name(), first);
        progress.addLoadingItem(second.name(), second);

        progress.markLoaded(null, true);

        Assert.assertSame(first, progress.loadedItem("first.csv"));
        Assert.assertSame(second, progress.loadedItem("second.csv"));
        Assert.assertTrue(progress.loadingItems().isEmpty());
    }

    @Test
    public void testConfirmOffsetOnlyUpdatesLoadingItems() {
        InputProgress progress = newProgress();
        FileItemProgress loaded = item("loaded.csv", 100L, "a", 3L);
        FileItemProgress loading = item("loading.csv", 200L, "b", 5L);
        progress.addLoadedItem(loaded.name(), loaded);
        progress.addLoadingItem(loading.name(), loading);
        loaded.offset(30L);
        loading.offset(50L);

        progress.confirmOffset();

        Assert.assertEquals(3L, loaded.offset());
        Assert.assertEquals(50L, loading.offset());
    }

    @Test
    public void testFileItemProgressEqualityAndString() {
        FileItemProgress item = item("vertex.csv", 100L, "abc", 3L);

        Assert.assertEquals("vertex.csv", item.name());
        Assert.assertEquals("vertex.csv", item.toString());
        Assert.assertEquals(item, item("vertex.csv", 100L, "abc", 99L));
        Assert.assertEquals(item.hashCode(),
                            item("vertex.csv", 100L, "abc", 99L).hashCode());
        Assert.assertNotEquals(item, item("vertex.csv", 101L, "abc", 3L));
        Assert.assertNotEquals(item, item("vertex.csv", 100L, "def", 3L));
        Assert.assertNotEquals(item, "vertex.csv");
    }

    private static InputProgress newProgress() {
        Map<String, InputItemProgress> loaded = new LinkedHashMap<>();
        Map<String, InputItemProgress> loading = new LinkedHashMap<>();
        return new InputProgress(SourceType.FILE, loaded, loading);
    }

    private static FileItemProgress item(String name, long timestamp,
                                         String checksum, long offset) {
        return new FileItemProgress(name, timestamp, checksum, offset);
    }

    private static Readable readable(String name) {
        return new Readable() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Path path() {
                return new Path(name);
            }

            @Override
            public InputStream open() throws IOException {
                return null;
            }

            @Override
            public InputItemProgress inputItemProgress() {
                return item(name, 0L, "", 0L);
            }
        };
    }
}
