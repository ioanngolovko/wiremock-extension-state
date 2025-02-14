/*
 * Copyright (C) 2023 Dirk Bolte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wiremock.extensions.state.internal;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class Context {

    private static final int MAX_IDS = 10;
    private final String contextName;
    private final Map<String, String> properties = new HashMap<>();
    private final LinkedList<Map<String, String>> list = new LinkedList<>();
    private final LinkedList<String> requests = new LinkedList<>();
    private Long updateCount = 1L;
    private Long matchCount = 0L;

    public Context(Context other) {
        this.contextName = other.contextName;
        this.properties.putAll(other.properties);
        this.list.addAll(other.list.stream().map(HashMap::new).collect(Collectors.toList()));
        this.requests.addAll(other.requests);
        this.updateCount = other.updateCount;
        this.matchCount = other.matchCount;
    }

    public Context(String contextName) {
        this.contextName = contextName;
    }

    public String getContextName() {
        return contextName;
    }

    public Long getUpdateCount() {
        return updateCount;
    }

    public Long getMatchCount() {
        return matchCount;
    }

    public Long incUpdateCount() {
        updateCount = updateCount + 1;
        return updateCount;
    }

    public Long incMatchCount(String requestId) {
        if (requests.contains(requestId)) {
            return matchCount;
        } else {
            requests.add(requestId);
            if (requests.size() > MAX_IDS) {
                requests.removeFirst();
            }
            matchCount = matchCount + 1;
            return matchCount;
        }
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public LinkedList<Map<String, String>> getList() {
        return list;
    }

    @Override
    public String toString() {
        return "Context{" +
            "contextName='" + contextName + '\'' +
            ", properties=" + properties +
            ", list=" + list +
            ", updateCount=" + updateCount +
            '}';
    }
}
