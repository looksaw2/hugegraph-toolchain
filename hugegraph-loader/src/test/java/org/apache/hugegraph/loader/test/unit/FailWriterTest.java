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

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import org.apache.hugegraph.loader.constant.Constants;
import org.apache.hugegraph.loader.exception.InsertException;
import org.apache.hugegraph.loader.exception.LoadException;
import org.apache.hugegraph.loader.exception.ParseException;
import org.apache.hugegraph.loader.exception.ReadException;
import org.apache.hugegraph.loader.failure.FailWriter;
import org.apache.hugegraph.testutil.Assert;

public class FailWriterTest {

    @Test
    public void testWriteAllFailureTypes() throws Exception {
        File file = new File("target/fail-writer-test/errors.log");
        FileUtils.deleteQuietly(file);

        FailWriter writer = new FailWriter(file, Constants.CHARSET.name(),
                                           false);
        writer.write(new ReadException("read-line", "read failed"));
        writer.write(new ParseException("parse-line", "parse failed"));
        writer.write(new InsertException("insert-line", "insert failed"));
        writer.close();

        String content = FileUtils.readFileToString(file, Constants.CHARSET);
        Assert.assertContains("#### READ ERROR: read failed", content);
        Assert.assertContains("read-line", content);
        Assert.assertContains("#### PARSE ERROR: parse failed", content);
        Assert.assertContains("parse-line", content);
        Assert.assertContains("#### INSERT ERROR: insert failed", content);
        Assert.assertContains("insert-line", content);
    }

    @Test
    public void testAppendModeKeepsExistingContent() throws Exception {
        File file = new File("target/fail-writer-test/append.log");
        FileUtils.deleteQuietly(file);
        FileUtils.writeStringToFile(file, "existing\n", Constants.CHARSET);

        FailWriter writer = new FailWriter(file, Constants.CHARSET.name(),
                                           true);
        writer.write(new ParseException("line", "failed"));
        writer.close();

        String content = FileUtils.readFileToString(file, Constants.CHARSET);
        Assert.assertTrue(content.startsWith("existing\n"));
        Assert.assertContains("#### PARSE ERROR: failed", content);
    }

    @Test
    public void testRejectDirectoryWithSameName() throws Exception {
        File directory = new File("target/fail-writer-test/directory.log");
        FileUtils.deleteQuietly(directory);
        FileUtils.forceMkdir(directory);

        Assert.assertThrows(LoadException.class, () -> {
            new FailWriter(directory, Constants.CHARSET.name(), false);
        });
    }
}
