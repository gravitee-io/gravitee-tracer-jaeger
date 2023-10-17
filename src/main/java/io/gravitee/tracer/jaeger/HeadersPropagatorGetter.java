/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.tracer.jaeger;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HeadersPropagatorGetter implements TextMapGetter<Iterable<Entry<String, String>>> {

    @Override
    public Iterable<String> keys(final Iterable<Entry<String, String>> carrier) {
        Set<String> keys = new HashSet<>();
        for (Entry<String, String> entry : carrier) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    @Override
    public String get(final Iterable<Entry<String, String>> carrier, final String key) {
        if (carrier == null) {
            return null;
        }
        for (Entry<String, String> entry : carrier) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
