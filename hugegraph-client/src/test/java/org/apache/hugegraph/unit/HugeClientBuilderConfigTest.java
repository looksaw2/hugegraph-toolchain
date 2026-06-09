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

package org.apache.hugegraph.unit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.HugeClientBuilder;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

import okhttp3.OkHttpClient;

public class HugeClientBuilderConfigTest {

    @Test
    public void testConfigMethodsExposeConfiguredValues() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Consumer<OkHttpClient.Builder> consumer = builder -> invoked.set(true);

        HugeClientBuilder builder = HugeClient.builder("http://localhost:8080",
                                                       "DEFAULT", "hugegraph")
                                              .configUrl("http://127.0.0.1:8080")
                                              .configGraphSpace("SPACE")
                                              .configGraph("graph")
                                              .configUser(null, null)
                                              .configToken(null)
                                              .configToken("token")
                                              .configTimeout(3)
                                              .configConnectTimeout(5)
                                              .configReadTimeout(7)
                                              .configPool(11, 13)
                                              .configIdleTime(17)
                                              .configSSL("trust.jks", "secret")
                                              .configHttpBuilder(consumer);

        Assert.assertEquals("http://127.0.0.1:8080", builder.url());
        Assert.assertEquals("SPACE", builder.graphSpace());
        Assert.assertEquals("graph", builder.graph());
        Assert.assertEquals("", builder.username());
        Assert.assertEquals("", builder.password());
        Assert.assertEquals("token", builder.token());
        Assert.assertEquals(3000, builder.timeout());
        Assert.assertEquals(Integer.valueOf(5000), builder.connectTimeout());
        Assert.assertEquals(Integer.valueOf(7000), builder.readTimeout());
        Assert.assertEquals(11, builder.maxConns());
        Assert.assertEquals(13, builder.maxConnsPerRoute());
        Assert.assertEquals(17, builder.idleTime());
        Assert.assertEquals("trust.jks", builder.trustStoreFile());
        Assert.assertEquals("secret", builder.trustStorePassword());
        Assert.assertSame(consumer, builder.httpBuilderConsumer());

        builder.httpBuilderConsumer().accept(new OkHttpClient.Builder());
        Assert.assertTrue(invoked.get());
    }

    @Test
    public void testZeroPoolAndTimeoutUseDefaults() {
        HugeClientBuilder builder = HugeClient.builder("http://localhost:8080",
                                                       "hugegraph")
                                              .configPool(0, 0)
                                              .configTimeout(0)
                                              .configConnectTimeout(null)
                                              .configReadTimeout(null);

        Assert.assertTrue(builder.maxConns() > 0);
        Assert.assertTrue(builder.maxConnsPerRoute() > 0);
        Assert.assertEquals(20000000, builder.timeout());
        Assert.assertNull(builder.connectTimeout());
        Assert.assertNull(builder.readTimeout());
    }

    @Test
    public void testConfigIdleTimeRejectsNonPositiveValue() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            HugeClient.builder("http://localhost:8080", "hugegraph")
                      .configIdleTime(0);
        });
    }
}
