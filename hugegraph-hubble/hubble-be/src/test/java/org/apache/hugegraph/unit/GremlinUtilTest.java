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

import org.apache.hugegraph.util.GremlinUtil;
import org.junit.Assert;
import org.junit.Test;

public class GremlinUtilTest {

    @Test
    public void testEscapeId() {
        Assert.assertEquals("123", GremlinUtil.escapeId(123));
        Assert.assertEquals("'simple'", GremlinUtil.escapeId("simple"));
        Assert.assertEquals("'a\\\\b\\\"c\\'d\\ne'",
                            GremlinUtil.escapeId("a\\b\"c'd\ne"));
    }

    @Test
    public void testEscape() {
        Object object = new Object();

        Assert.assertSame(object, GremlinUtil.escape(object));
        Assert.assertEquals("'vertex'", GremlinUtil.escape("vertex"));
    }

    @Test
    public void testOptimizeLimitAppendsForSupportedSuffixes() {
        String gremlin = "g.V()\n" +
                         "g.E()\n" +
                         "g.V().out()\n" +
                         "g.V().hasLabel('person')\n" +
                         "g.V().hasLabel(\"software\")\n" +
                         "g.V().hasLabel(123)\n" +
                         "g.V().path()";

        String result = GremlinUtil.optimizeLimit(gremlin, 10);

        Assert.assertEquals("g.V().limit(10)\n" +
                            "g.E().limit(10)\n" +
                            "g.V().out().limit(10)\n" +
                            "g.V().hasLabel('person').limit(10)\n" +
                            "g.V().hasLabel(\"software\").limit(10)\n" +
                            "g.V().hasLabel(123).limit(10)\n" +
                            "g.V().path().limit(10)", result);
    }

    @Test
    public void testOptimizeLimitKeepsIgnoredAndUnsupportedLines() {
        String gremlin = "  // g.V()\n" +
                         "g.V().limit(1)\n" +
                         "g.V().has('name', 'marko')\n" +
                         "g.V().hasLabel(person)\n" +
                         "g.V().out().count()";

        String result = GremlinUtil.optimizeLimit(gremlin, 5);

        Assert.assertEquals(gremlin, result);
    }
}
