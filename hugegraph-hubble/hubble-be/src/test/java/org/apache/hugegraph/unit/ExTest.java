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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.exception.InternalException;
import org.apache.hugegraph.util.Ex;
import org.junit.Assert;
import org.junit.Test;

public class ExTest {

    @Test
    public void testCheckBooleanPassesAndFails() {
        Ex.check(true, "should.not.fail");

        ExternalException exception = assertThrows(
                ExternalException.class,
                () -> Ex.check(false, "invalid.%s", "name"));
        Assert.assertEquals(400, exception.status());
        Assert.assertEquals("invalid.name", exception.getMessage());
    }

    @Test
    public void testCheckBooleanWithStatus() {
        ExternalException exception = assertThrows(
                ExternalException.class,
                () -> Ex.check(false, 409, "conflict.%s", "graph"));

        Assert.assertEquals(409, exception.status());
        Assert.assertEquals("conflict.graph", exception.getMessage());
    }

    @Test
    public void testCheckCallableShortCircuitsWhenConditionIsFalse()
            throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);

        Ex.check(false, () -> {
            called.set(true);
            return false;
        }, "should.not.fail");

        Assert.assertFalse(called.get());
    }

    @Test
    public void testCheckCallablePassesAndFails() {
        Ex.check(true, () -> true, "should.not.fail");

        ExternalException exception = assertThrows(
                ExternalException.class,
                () -> Ex.check(true, () -> false, "missing.%s", "vertex"));
        Assert.assertEquals("missing.vertex", exception.getMessage());
    }

    @Test
    public void testCheckCallableWrapsPredicateError() {
        IllegalStateException cause = new IllegalStateException("boom");

        InternalException exception = assertThrows(
                InternalException.class,
                () -> Ex.check(true, () -> {
                    throw cause;
                }, "ignored"));

        Assert.assertEquals("execute.predication.error", exception.getMessage());
        Assert.assertSame(cause, exception.getCause());
    }

    @Test
    public void testCheckWithCause() {
        RuntimeException cause = new RuntimeException("cause");

        ExternalException exception = assertThrows(
                ExternalException.class,
                () -> Ex.check(false, "bad.%s", cause, "request"));

        Assert.assertEquals("bad.request", exception.getMessage());
        Assert.assertSame(cause, exception.getCause());
    }

    @Test
    public void testCheckCallableWithCause() {
        RuntimeException cause = new RuntimeException("cause");

        ExternalException exception = assertThrows(
                ExternalException.class,
                () -> Ex.check(true, () -> false, "bad.%s", cause, "request"));

        Assert.assertEquals("bad.request", exception.getMessage());
        Assert.assertSame(cause, exception.getCause());
    }

    @Test
    public void testRootCause() {
        IllegalArgumentException root = new IllegalArgumentException("root");
        RuntimeException middle = new RuntimeException("middle", root);
        RuntimeException top = new RuntimeException("top", middle);

        Assert.assertSame(root, Ex.rootCause(top));
        Assert.assertSame(root, Ex.rootCause(root));
    }

    private static <T extends Throwable> T assertThrows(Class<T> clazz,
                                                       ThrowingRunnable task) {
        try {
            task.run();
        } catch (Throwable e) {
            if (clazz.isInstance(e)) {
                return clazz.cast(e);
            }
            Assert.fail("Expected " + clazz.getName() + " but caught " +
                        e.getClass().getName());
        }
        Assert.fail("Expected " + clazz.getName());
        return null;
    }

    private interface ThrowingRunnable {

        void run() throws Throwable;
    }
}
