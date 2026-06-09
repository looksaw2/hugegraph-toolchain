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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.hugegraph.loader.exception.LoadException;
import org.apache.hugegraph.loader.reader.InputReader;
import org.apache.hugegraph.loader.reader.file.LocalFileReader;
import org.apache.hugegraph.loader.source.file.FileSource;
import org.apache.hugegraph.testutil.Assert;

public class FileReaderFactoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testInputReaderCreateFileSource() {
        FileSource source = new FileSource();
        InputReader reader = InputReader.create(source);

        Assert.assertTrue(reader instanceof LocalFileReader);
        Assert.assertTrue(reader.multiReaders());
    }

    @Test
    public void testLocalFileReaderSplitSortsReadableFilesAndReadsHeader()
            throws Exception {
        File dir = this.tempFolder.newFolder("reader-dir");
        File second = new File(dir, "b.csv");
        File first = new File(dir, "a.csv");
        FileUtils.write(first, "id,name\n1,marko\n", StandardCharsets.UTF_8);
        FileUtils.write(second, "id,name\n2,vadas\n", StandardCharsets.UTF_8);

        FileSource source = new FileSource();
        source.path(dir.getAbsolutePath());

        LocalFileReader reader = new LocalFileReader(source);
        List<InputReader> readers = reader.split();

        Assert.assertEquals(2, readers.size());
        Assert.assertArrayEquals(new String[]{"id", "name"}, source.header());
        Assert.assertEquals("a.csv", ((LocalFileReader) readers.get(0)).readable().name());
        Assert.assertEquals("b.csv", ((LocalFileReader) readers.get(1)).readable().name());
    }

    @Test
    public void testLocalFileReaderSplitRejectsMissingPath() {
        FileSource source = new FileSource();
        source.path(new File(this.tempFolder.getRoot(), "missing.csv")
                    .getAbsolutePath());

        LocalFileReader reader = new LocalFileReader(source);
        Assert.assertThrows(LoadException.class, reader::split);
    }

    @Test
    public void testLocalFileReaderSplitReturnsEmptyForEmptyDirectory()
            throws Exception {
        File dir = this.tempFolder.newFolder("empty-dir");
        FileSource source = new FileSource();
        source.path(dir.getAbsolutePath());

        LocalFileReader reader = new LocalFileReader(source);

        Assert.assertEquals(0, reader.split().size());
        Assert.assertNull(source.header());
    }
}
