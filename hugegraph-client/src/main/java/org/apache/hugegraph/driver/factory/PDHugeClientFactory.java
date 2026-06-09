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

package org.apache.hugegraph.driver.factory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hugegraph.util.E;

import org.apache.hugegraph.driver.HugeClient;

import lombok.SneakyThrows;

/**
 * PD (Platform Discovery) based client factory.
 * Uses reflection to load PD classes at runtime, supporting both Java 8 and
 * Java 11 environments.
 */
public class PDHugeClientFactory {

    public static final String DEFAULT_GRAPHSPACE = "DEFAULT";
    public static final String DEFAULT_SERVICE = "DEFAULT";
    public static final String NAME = "hg";
    public static final String TOKEN =
            "$2a$04$i10KooNg6wLvIPVDh909n.RBYlZ/4pJo978nFK86nrqQiGIKV4UGS";

    protected static final String SERVICE_VERSION = "1.0.0";

    private static final String PD_CONFIG_CLASS =
            "org.apache.hugegraph.pd.client.PDConfig";
    private static final String DISCOVERY_CLIENT_IMPL_CLASS =
            "org.apache.hugegraph.pd.client.DiscoveryClientImpl";
    private static final String QUERY_CLASS =
            "org.apache.hugegraph.pd.grpc.discovery.Query";

    protected final String pdAddrs;
    protected final RouteType type;
    protected final Object client;

    /**
     * Check if PD dependencies are available on the classpath.
     */
    public static boolean isAvailable() {
        try {
            Class.forName(DISCOVERY_CLIENT_IMPL_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public PDHugeClientFactory(String pdAddrs) {
        this(pdAddrs, null);
    }

    @SneakyThrows
    public PDHugeClientFactory(String pdAddrs, String type) {
        this.pdAddrs = pdAddrs;
        this.type = type != null ? RouteType.valueOf(type) : RouteType.BOTH;

        E.checkArgument(isAvailable(),
                        "PD (Platform Discovery) client is not available. " +
                        "Ensure hg-pd-client and hg-pd-grpc dependencies " +
                        "are on the classpath.");

        Class<?> pdConfigClass = Class.forName(PD_CONFIG_CLASS);
        // Validate PDConfig is loadable
        pdConfigClass.getMethod("of", String.class);

        Class<?> discoveryClientImplClass =
                Class.forName(DISCOVERY_CLIENT_IMPL_CLASS);
        Object builder = discoveryClientImplClass
                .getMethod("newBuilder")
                .invoke(null);
        builder = builder.getClass()
                .getMethod("setCenterAddress", String.class)
                .invoke(builder, pdAddrs);
        this.client = builder.getClass()
                .getMethod("build")
                .invoke(builder);

        // TODO: Not support now: pdConfig.setAuthority(NAME, TOKEN);
    }

    public HugeClient createUnauthClient(String cluster, String graphSpace,
                                         String graph) {
        E.checkArgument(cluster != null,
                        "create unauth client: cluster must not null");

        return createUnauthClient(cluster, graphSpace, graph, 60);
    }

    public HugeClient createUnauthClient(String cluster, String graphSpace,
                                         String graph, int timeout) {
        E.checkArgument(cluster != null,
                        "create unauth client: cluster must not null");

        return createClient(cluster, graphSpace, graph, null, null, null,
                            timeout);
    }

    public HugeClient createAuthClient(String cluster, String graphSpace,
                                       String graph, String token,
                                       String username, String password) {

        return createAuthClient(cluster, graphSpace, graph, token, username,
                                password, 60);
    }

    public HugeClient createAuthClient(String cluster, String graphSpace,
                                       String graph, String token,
                                       String username, String password,
                                       int timeout) {
        E.checkArgument(cluster != null,
                        "create auth client: cluster must not null");

        E.checkArgument(token != null || (username != null && password != null),
                        "create auth client: token must not null or " +
                                "username/password must not null");

        return createClient(cluster, graphSpace, graph, token, username,
                            password, timeout);
    }

    protected HugeClient createClient(String cluster, String graphSpace,
                                      String graph, String token,
                                      String username, String password,
                                      int timeout) {

        List<String> urls = getAutoURLs(cluster, graphSpace, graph);

        int r = (int) Math.floor(Math.random() * urls.size());
        String address = urls.get(r);
        if (!address.startsWith("http")) {
            address = "http://" + address;
        }

        HugeClient client = HugeClient.builder(address, graphSpace, graph)
                                      .configToken(token)
                                      .configUser(username, password)
                                      .configTimeout(timeout)
                                      .build();
        return client;
    }

    public List<String> getAutoURLs(String cluster, String graphSpace,
                                    String service) {
        // if no urls under graphspace/service
        // use DEFAULT/DEFAULT

        List<String> urls = null;
        if (StringUtils.isNotEmpty(graphSpace)) {
            if (StringUtils.isNotEmpty(service)) {
                urls = getURLs(cluster, graphSpace, service);
            }

            if (CollectionUtils.isNotEmpty(urls)) {
                return urls;
            }

            urls = getURLs(cluster, graphSpace, null);
            if (CollectionUtils.isNotEmpty(urls)) {
                return urls;
            }
        }

        urls = getURLs(cluster, DEFAULT_GRAPHSPACE, DEFAULT_SERVICE);

        return urls;
    }

    @SneakyThrows
    public List<String> getURLs(String cluster, String graphSpace,
                                String service) {

        E.checkArgument(StringUtils.isNotEmpty(graphSpace), "list urls" +
                " error, cluster must not null");

        Map<String, String> configs = new HashMap<>();
        if (StringUtils.isNotEmpty(graphSpace)) {
            configs.put("GRAPHSPACE", graphSpace);
        }
        if (StringUtils.isNotEmpty(service)) {
            configs.put("SERVICE_NAME", service);
        }

        if (!RouteType.BOTH.equals(this.type)) {
            configs.put("REGISTER_TYPE", this.type.name());
        }

        Class<?> queryClass = Class.forName(QUERY_CLASS);
        Object queryBuilder = queryClass.getMethod("newBuilder").invoke(null);
        queryBuilder = queryBuilder.getClass()
                .getMethod("setAppName", String.class)
                .invoke(queryBuilder, cluster);
        queryBuilder = queryBuilder.getClass()
                .getMethod("putAllLabels", Map.class)
                .invoke(queryBuilder, configs);
        Object query = queryBuilder.getClass()
                .getMethod("build")
                .invoke(queryBuilder);

        Object nodeInfos = this.client.getClass()
                .getMethod("getNodeInfos", query.getClass())
                .invoke(this.client, query);

        @SuppressWarnings("unchecked")
        List<Object> infoList = (List<Object>) nodeInfos.getClass()
                .getMethod("getInfoList")
                .invoke(nodeInfos);

        return infoList.stream()
                .map(nodeInfo -> {
                    try {
                        return (String) nodeInfo.getClass()
                                .getMethod("getAddress")
                                .invoke(nodeInfo);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to get address from NodeInfo", e);
                    }
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    protected List<String> getURLsWithConfig(String cluster,
                                             Map<String, String> configs) {

        if (null == configs) {
            configs = new HashMap<>();
        }

        Class<?> queryClass = Class.forName(QUERY_CLASS);
        Object queryBuilder = queryClass.getMethod("newBuilder").invoke(null);
        queryBuilder = queryBuilder.getClass()
                .getMethod("setAppName", String.class)
                .invoke(queryBuilder, cluster);
        queryBuilder = queryBuilder.getClass()
                .getMethod("setVersion", String.class)
                .invoke(queryBuilder, SERVICE_VERSION);
        queryBuilder = queryBuilder.getClass()
                .getMethod("putAllLabels", Map.class)
                .invoke(queryBuilder, configs);
        Object query = queryBuilder.getClass()
                .getMethod("build")
                .invoke(queryBuilder);

        Object nodeInfos = this.client.getClass()
                .getMethod("getNodeInfos", query.getClass())
                .invoke(this.client, query);

        List<Object> infoList = (List<Object>) nodeInfos.getClass()
                .getMethod("getInfoList")
                .invoke(nodeInfos);

        return infoList.stream()
                .map(nodeInfo -> {
                    try {
                        return (String) nodeInfo.getClass()
                                .getMethod("getAddress")
                                .invoke(nodeInfo);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to get address from NodeInfo", e);
                    }
                })
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public void close() {
        this.client.getClass()
                .getMethod("close")
                .invoke(this.client);
    }

    public enum RouteType {
        BOTH,
        NODE_PORT,
        DDS
    }
}
