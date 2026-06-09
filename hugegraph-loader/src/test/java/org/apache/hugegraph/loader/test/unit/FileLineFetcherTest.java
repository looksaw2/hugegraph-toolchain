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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hadoop.fs.Path;
import org.junit.Test;

import org.apache.hugegraph.loader.exception.LoadException;
import org.apache.hugegraph.loader.progress.FileItemProgress;
import org.apache.hugegraph.loader.progress.InputItemProgress;
import org.apache.hugegraph.loader.reader.Readable;
import org.apache.hugegraph.loader.reader.file.FileLineFetcher;
import org.apache.hugegraph.loader.reader.line.Line;
import org.apache.hugegraph.loader.source.file.FileSource;
import org.apache.hugegraph.loader.source.file.SkippedLine;
import org.apache.hugegraph.testutil.Assert;

public class FileLineFetcherTest {

    @Test
    public void testReadHeaderAndFetchSkipsCommentsAndRepeatedHeader()
            throws IOException {
        FileSource source = new FileSource();
        SkippedLine skipped = new SkippedLine();
        skipped.regex("^#.*");
        source.skippedLine(skipped);

        FileLineFetcher fetcher = new FileLineFetcher(source);
        MemoryReadable readable = new MemoryReadable("users.csv",
                                                     "id,name\n" +
                                                     "# ignored\n" +
                                                     "1,marko\n" +
                                                     "2,vadas\n");

        fetcher.readHeaderIfNeeded(Arrays.asList(readable));
        Assert.assertArrayEquals(new String[]{"id", "name"}, source.header());
        Assert.assertFalse(fetcher.ready());

        fetcher.openReader(readable);
        Line first = fetcher.fetch();
        Line second = fetcher.fetch();

        Assert.assertArrayEquals(new Object[]{"1", "marko"}, first.values());
        Assert.assertArrayEquals(new Object[]{"2", "vadas"}, second.values());
        Assert.assertNull(fetcher.fetch());
        Assert.assertEquals(4L, fetcher.offset());
        fetcher.closeReader();
    }

    @Test
    public void testReadHeaderFailsForEmptyReadables() {
        FileSource source = new FileSource();
        FileLineFetcher fetcher = new FileLineFetcher(source);

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            fetcher.readHeaderIfNeeded(Arrays.asList());
        });
    }

    @Test
    public void testSkipOffsetRequiresOpenReader() {
        FileSource source = new FileSource();
        source.header(new String[]{"id", "name"});
        FileLineFetcher fetcher = new FileLineFetcher(source);

        Assert.assertThrows(IllegalStateException.class, () -> {
            fetcher.skipOffset(new MemoryReadable("users.csv", "1,marko\n"),
                               1L);
        });
    }

    @Test
    public void testOpenReaderWrapsReadableOpenFailure() {
        FileSource source = new FileSource();
        FileLineFetcher fetcher = new FileLineFetcher(source);

        Assert.assertThrows(LoadException.class, () -> {
            fetcher.openReader(new BrokenReadable());
        });
    }

    private static class MemoryReadable implements Readable {

        private final String name;
        private final String content;

        public MemoryReadable(String name, String content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public Path path() {
            return new Path(this.name);
        }

        @Override
        public InputStream open() throws IOException {
            return new ByteArrayInputStream(this.content.getBytes(
                    StandardCharsets.UTF_8));
        }

        @Override
        public InputItemProgress inputItemProgress() {
            return new FileItemProgress(this.name, 1L, "checksum", 0L);
        }
    }

    private static class BrokenReadable extends MemoryReadable {

        public BrokenReadable() {
            super("broken.csv", "");
        }

        @Override
        public InputStream open() throws IOException {
            throw new IOException("broken");
        }
    }
}
