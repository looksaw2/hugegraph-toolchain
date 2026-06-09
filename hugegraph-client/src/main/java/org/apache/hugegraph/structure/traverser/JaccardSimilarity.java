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

package org.apache.hugegraph.structure.traverser;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JaccardSimilarity {

    private Object similarsMap;
    @JsonProperty
    private ApiMeasure measure;

    @SuppressWarnings("unchecked")
    public Map<Object, Object> similarsMap() {
        if (this.similarsMap == null) {
            return null;
        }
        if (this.similarsMap instanceof Map) {
            return (Map<Object, Object>) this.similarsMap;
        }
        return Collections.singletonMap("jaccard_similarity", this.similarsMap);
    }

    // TODO: Temp implementation?
    @JsonProperty("jaccard_similarity")
    @JsonAlias("similarsMap")
    public Object getJaccardSimilarity() {
        if (!(this.similarsMap instanceof Map)) {
            return this.similarsMap;
        }

        Map<Object, Object> map = this.similarsMap();
        if (map.size() == 1) {
            return map.entrySet().iterator().next().getValue();
        }
        return map;
    }

    public ApiMeasure measure() {
        return this.measure;
    }

    public int size() {
        return this.similarsMap().size();
    }

    public Set<Object> keySet() {
        return this.similarsMap().keySet();
    }

    public Object get(Object key) {
        return this.similarsMap().get(key);
    }

    @JsonProperty("jaccard_similarity")
    @JsonAlias("similarsMap")
    public void setJaccardSimilarity(Object jaccardSimilarity) {
        this.similarsMap = jaccardSimilarity;
    }

    @JsonIgnore
    public void setSimilarsMap(Map<Object, Object> similarsMap) {
        this.similarsMap = similarsMap;
    }

    public void setMeasure(ApiMeasure measure) {
        this.measure = measure;
    }
}
