/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.onyx.common.ir.executionproperty;

import edu.snu.onyx.common.ir.edge.IREdge;
import edu.snu.onyx.common.ir.edge.executionproperty.DataFlowModelProperty;
import edu.snu.onyx.common.ir.edge.executionproperty.DataStoreProperty;
import edu.snu.onyx.common.ir.edge.executionproperty.PartitionerProperty;
import edu.snu.onyx.common.ir.vertex.IRVertex;
import edu.snu.onyx.common.ir.edge.executionproperty.DataCommunicationPatternProperty;
import edu.snu.onyx.common.ir.vertex.executionproperty.ExecutorPlacementProperty;
import edu.snu.onyx.common.ir.vertex.executionproperty.ParallelismProperty;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ExecutionPropertyMap Class, which uses HashMap for keeping track of ExecutionProperties for vertices and edges.
 */
public final class ExecutionPropertyMap implements Serializable {
  private final String id;
  private final Map<ExecutionProperty.Key, ExecutionProperty<?>> properties;

  /**
   * Constructor for ExecutionPropertyMap class.
   * @param id ID of the vertex / edge to keep the execution property of.
   */
  @VisibleForTesting
  public ExecutionPropertyMap(final String id) {
    this.id = id;
    properties = new EnumMap<>(ExecutionProperty.Key.class);
  }

  /**
   * Static initializer for irEdges.
   * @param irEdge irEdge to keep the execution property of.
   * @param commPattern Data communication pattern type of the edge.
   * @return The corresponding ExecutionPropertyMap.
   */
  public static ExecutionPropertyMap of(final IREdge irEdge,
                                        final DataCommunicationPatternProperty.Value commPattern) {
    final ExecutionPropertyMap map = new ExecutionPropertyMap(irEdge.getId());
    map.put(DataCommunicationPatternProperty.of(commPattern));
    map.put(DataFlowModelProperty.of(DataFlowModelProperty.Value.Pull));
    switch (commPattern) {
      case Shuffle:
        map.put(PartitionerProperty.of(PartitionerProperty.Value.HashPartitioner));
        map.put(DataStoreProperty.of(DataStoreProperty.Value.LocalFileStore));
        break;
      case BroadCast:
        map.put(PartitionerProperty.of(PartitionerProperty.Value.IntactPartitioner));
        map.put(DataStoreProperty.of(DataStoreProperty.Value.LocalFileStore));
        break;
      case OneToOne:
        map.put(PartitionerProperty.of(PartitionerProperty.Value.IntactPartitioner));
        map.put(DataStoreProperty.of(DataStoreProperty.Value.MemoryStore));
        break;
      default:
        map.put(PartitionerProperty.of(PartitionerProperty.Value.HashPartitioner));
        map.put(DataStoreProperty.of(DataStoreProperty.Value.LocalFileStore));
    }
    return map;
  }
  /**
   * Static initializer for irVertex.
   * @param irVertex irVertex to keep the execution property of.
   * @return The corresponding ExecutionPropertyMap.
   */
  public static ExecutionPropertyMap of(final IRVertex irVertex) {
    final ExecutionPropertyMap map = new ExecutionPropertyMap(irVertex.getId());
    map.put(ParallelismProperty.of(1));
    map.put(ExecutorPlacementProperty.of(ExecutorPlacementProperty.NONE));
    return map;
  }

  /**
   * ID of the item this ExecutionPropertyMap class is keeping track of.
   * @return the ID of the item this ExecutionPropertyMap class is keeping track of.
   */
  public String getId() {
    return id;
  }

  /**
   * Put the given execution property  in the ExecutionPropertyMap.
   * @param executionProperty execution property to insert.
   * @return the inserted execution property.
   */
  public ExecutionProperty<?> put(final ExecutionProperty<?> executionProperty) {
    return properties.put(executionProperty.getKey(), executionProperty);
  }

  /**
   * Get the value of the given execution property type.
   * @param <T> Type of the return value.
   * @param executionPropertyKey the execution property type to find the value of.
   * @return the value of the given execution property.
   */
  public <T> T get(final ExecutionProperty.Key executionPropertyKey) {
    ExecutionProperty<T> property = (ExecutionProperty<T>) properties.getOrDefault(executionPropertyKey,
     ExecutionProperty.<T>emptyExecutionProperty());
    return property.getValue();
  }

  /**
   * remove the execution property.
   * @param key key of the execution property to remove.
   * @return the removed execution property
   */
  public ExecutionProperty<?> remove(final ExecutionProperty.Key key) {
    return properties.remove(key);
  }

  /**
   * @param key key to look for.
   * @return whether or not the execution property map contains the key.
   */
  public boolean containsKey(final ExecutionProperty.Key key) {
    return properties.containsKey(key);
  }

  /**
   * Same as forEach function in Java 8, but for execution properties.
   * @param action action to apply to each of the execution properties.
   */
  public void forEachProperties(final Consumer<? super ExecutionProperty> action) {
    properties.values().forEach(action);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{");
    boolean isFirstPair = true;
    for (final Map.Entry<ExecutionProperty.Key, ExecutionProperty<?>> entry : properties.entrySet()) {
      if (!isFirstPair) {
        sb.append(", ");
      }
      isFirstPair = false;
      sb.append("\"");
      sb.append(entry.getKey());
      sb.append("\": \"");
      sb.append(entry.getValue().getValue());
      sb.append("\"");
    }
    sb.append("}");
    return sb.toString();
  }

  // Apache commons-lang 3 Equals/HashCodeBuilder template.
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    ExecutionPropertyMap that = (ExecutionPropertyMap) obj;

    return new EqualsBuilder()
        .append(properties.values().stream().map(ExecutionProperty::getValue).collect(Collectors.toSet()),
            that.properties.values().stream().map(ExecutionProperty::getValue).collect(Collectors.toSet()))
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(properties.values().stream().map(ExecutionProperty::getValue).collect(Collectors.toSet()))
        .toHashCode();
  }
}
