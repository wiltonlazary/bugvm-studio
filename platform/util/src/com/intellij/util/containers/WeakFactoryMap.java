/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.containers;

import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ObjectUtils.NULL;

/**
 * @author peter
 */
public abstract class WeakFactoryMap<T,V> {
  private final ConcurrentMap<T, V> myMap = ContainerUtil.createConcurrentWeakKeyWeakValueMap();

  protected abstract V create(T key);

  public final V get(T key) {
    final V v = myMap.get(key);
    if (v != null) {
      return v == NULL ? null : v;
    }

    final V value = create(key);
    V prev = myMap.putIfAbsent(key, notNull(value));
    return prev == null || prev == NULL ? value : prev;
  }

  public final boolean containsKey(T key) {
    return myMap.containsKey(key);
  }

  public void clear() {
    myMap.clear();
  }

  private static <K> K notNull(K key) {
    //noinspection unchecked
    return key == null ? (K)NULL : key;
  }

  public void put(T key, V value) {
    myMap.put(notNull(key), notNull(value));
  }
}
