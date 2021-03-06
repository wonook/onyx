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
package edu.snu.onyx.common.dag;

import edu.snu.onyx.common.ir.edge.IREdge;
import edu.snu.onyx.common.ir.edge.executionproperty.DataCommunicationPatternProperty;
//import edu.snu.onyx.common.ir.edge.executionproperty.MetricCollectionProperty;
import edu.snu.onyx.common.ir.edge.executionproperty.MetricCollectionProperty;
import edu.snu.onyx.common.ir.vertex.IRVertex;
import edu.snu.onyx.common.ir.vertex.OperatorVertex;
import edu.snu.onyx.common.ir.vertex.SourceVertex;
import edu.snu.onyx.common.ir.vertex.LoopVertex;
import edu.snu.onyx.common.ir.executionproperty.ExecutionProperty;
import edu.snu.onyx.common.ir.edge.executionproperty.DataFlowModelProperty;
import edu.snu.onyx.common.exception.IllegalVertexOperationException;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DAG Builder.
 * @param <V> the vertex type.
 * @param <E> the edge type.
 */
public final class DAGBuilder<V extends Vertex, E extends Edge<V>> implements Serializable {
  private final Set<V> vertices;
  private final Map<V, Set<E>> incomingEdges;
  private final Map<V, Set<E>> outgoingEdges;
  private final Map<V, LoopVertex> assignedLoopVertexMap;
  private final Map<V, Integer> loopStackDepthMap;

  /**
   * Constructor of DAGBuilder: it initializes everything.
   */
  public DAGBuilder() {
    this.vertices = new HashSet<>();
    this.incomingEdges = new HashMap<>();
    this.outgoingEdges = new HashMap<>();
    this.assignedLoopVertexMap = new HashMap<>();
    this.loopStackDepthMap = new HashMap<>();
  }

  /**
   * Constructor of DAGBuilder with a DAG to start from.
   * @param dag to start the builder from.
   */
  public DAGBuilder(final DAG<V, E> dag) {
    this();
    dag.getVertices().forEach(v -> this.addVertex(v, dag));
    vertices.forEach(v -> dag.getIncomingEdgesOf(v).forEach(this::connectVertices));
  }

  /**
   * Add vertex to the builder.
   * @param v vertex to add.
   * @return the builder.
   */
  public DAGBuilder<V, E> addVertex(final V v) {
    vertices.add(v);
    incomingEdges.putIfAbsent(v, new HashSet<>());
    outgoingEdges.putIfAbsent(v, new HashSet<>());
    return this;
  }
  /**
   * Add vertex to the builder, with assignedLoopVertex and stackDepth information.
   * @param v vertex to add.
   * @param assignedLoopVertex the assigned, wrapping loop vertex.
   * @param stackDepth the stack depth of the loop vertex.
   * @return the builder.
   */
  private DAGBuilder<V, E> addVertex(final V v, final LoopVertex assignedLoopVertex, final Integer stackDepth) {
    addVertex(v);
    this.assignedLoopVertexMap.put(v, assignedLoopVertex);
    this.loopStackDepthMap.put(v, stackDepth);
    return this;
  }
  /**
   * Add vertex to the builder, using the LoopVertex stack.
   * @param v vertex to add.
   * @param loopVertexStack LoopVertex stack to retrieve the information from.
   * @return the builder.
   */
  public DAGBuilder<V, E> addVertex(final V v, final Stack<LoopVertex> loopVertexStack) {
    if (!loopVertexStack.empty()) {
      addVertex(v, loopVertexStack.peek(), loopVertexStack.size());
    } else {
      addVertex(v);
    }
    return this;
  }
  /**
   * Add vertex to the builder, using the information from the given DAG.
   * @param v vertex to add.
   * @param dag DAG to observe and get the LoopVertex-related information from.
   * @return the builder.
   */
  public DAGBuilder<V, E> addVertex(final V v, final DAG<V, E> dag) {
    if (dag.isCompositeVertex(v)) {
      addVertex(v, dag.getAssignedLoopVertexOf(v), dag.getLoopStackDepthOf(v));
    } else {
      addVertex(v);
    }
    return this;
  }

  /**
   * Remove the vertex from the list.
   * @param v vertex to remove.
   * @return the builder.
   */
  public DAGBuilder<V, E> removeVertex(final V v) {
    vertices.remove(v);
    incomingEdges.get(v).forEach(e -> outgoingEdges.get(e.getSrc()).remove(e));
    outgoingEdges.get(v).forEach(e -> incomingEdges.get(e.getDst()).remove(e));
    incomingEdges.remove(v);
    outgoingEdges.remove(v);
    return this;
  }

  /**
   * Connect vertices at the edge.
   * @param edge edge to add.
   * Note: the two vertices of the edge should already be added to the DAGBuilder.
   * @return the builder.
   */
  public DAGBuilder<V, E> connectVertices(final E edge) {
    final V src = edge.getSrc();
    final V dst = edge.getDst();
    if (vertices.contains(src) && vertices.contains(dst)) {
      incomingEdges.get(dst).add(edge);
      outgoingEdges.get(src).add(edge);
    } else {
      throw new IllegalVertexOperationException("The DAG does not contain either src or dst of the edge: "
          + (src == null ? null : src.getId()) + " -> " + (dst == null ? null : dst.getId()));
    }
    return this;
  }

  /**
   * Checks whether the DAGBuilder is empty.
   * @return whether the DAGBuilder is empty or not.
   */
  public boolean isEmpty() {
    return vertices.isEmpty();
  }

  /**
   * check if the DAGBuilder contains the vertex.
   * @param vertex vertex that it searches for.
   * @return whether or not the builder contains it.
   */
  public boolean contains(final V vertex) {
    return vertices.contains(vertex);
  }

  /**
   * check if the DAGBuilder contains any vertex that satisfies the predicate.
   * @param predicate predicate to test each vertices with.
   * @return whether or not the builder contains it.
   */
  public boolean contains(final Predicate<V> predicate) {
    return vertices.stream().anyMatch(predicate);
  }

  ///////////////////////////INTEGRITY CHECK///////////////////////////
  /**
   * Helper method to guarantee that there are no cycles in the DAG.
   * @param stack stack to push the vertices to.
   * @param visited set to keep track of visited vertices.
   * @param vertex vertex to check.
   */
  private void cycleCheck(final Stack<V> stack, final Set<V> visited, final V vertex) {
    // We check in a DFS manner.
    visited.add(vertex);
    stack.push(vertex);
    // When we encounter a vertex that we've already gone through, then there is a cycle.
    if (outgoingEdges.get(vertex).stream().map(Edge::getDst).anyMatch(stack::contains)) {
      throw new RuntimeException("DAG contains a cycle");
    } else {
      outgoingEdges.get(vertex).stream().map(Edge::getDst)
          .filter(v -> !visited.contains(v))
          .forEachOrdered(v -> cycleCheck(stack, visited, v));
    }
    stack.pop();
  }

  /**
   * Helper method to guarantee that all vertices without incoming edges are sources.
   */
  private void sourceCheck() {
    // We observe IRVertex that do not have any incoming edges.
    final Supplier<Stream<V>> verticesToObserve = () -> vertices.stream().filter(v -> incomingEdges.get(v).isEmpty())
        .filter(v -> v instanceof IRVertex);
    // They should all match SourceVertex
    if (verticesToObserve.get().anyMatch(v -> !(v instanceof SourceVertex))) {
      final String problematicVertices = verticesToObserve.get()
          .filter(v -> !(v instanceof SourceVertex))
          .map(V::getId)
          .collect(Collectors.toList()).toString();
      throw new RuntimeException("DAG source check failed while building DAG. " + problematicVertices);
    }
  }

  /**
   * Helper method to guarantee that all vertices without outgoing edges are sink or DoTransform.
   */
  private void sinkCheck() {
    // We observe IRVertex that do not have any outgoing edges.
    final Supplier<Stream<V>> verticesToObserve = () -> vertices.stream()
        .filter(v -> outgoingEdges.get(v).isEmpty())
        .filter(v -> v instanceof IRVertex);
    // They should either be OperatorVertex or LoopVertex
    if (verticesToObserve.get().anyMatch(v -> !(v instanceof OperatorVertex || v instanceof LoopVertex))) {
      final String problematicVertices = verticesToObserve.get().filter(v ->
          !(v instanceof OperatorVertex || v instanceof LoopVertex))
          .map(V::getId).collect(Collectors.toList()).toString();
      throw new RuntimeException("DAG sink check failed while building DAG: " + problematicVertices);
    }
  }

  /**
   * Helper method to check that all execution properties are correct and makes sense.
   */
  private void executionPropertyCheck() {
    // SideInput edge must be one-to-one
    vertices.forEach(v -> incomingEdges.get(v).stream().filter(e -> e instanceof IREdge).map(e -> (IREdge) e)
        .filter(e -> Boolean.TRUE.equals(e.isSideInput()))
        .filter(e -> !(e.getProperty(ExecutionProperty.Key.DataCommunicationPattern))
            .equals(DataCommunicationPatternProperty.Value.OneToOne))
        .forEach(e -> {
          throw new RuntimeException("DAG execution property check: "
              + "SideInput edge must be one-to-one: " + e.getId());
        }));
    // SideInput is not compatible with Push
    vertices.forEach(v -> incomingEdges.get(v).stream().filter(e -> e instanceof IREdge).map(e -> (IREdge) e)
        .filter(e -> Boolean.TRUE.equals(e.isSideInput()))
        .filter(e -> DataFlowModelProperty.Value.Push.equals(e.getProperty(ExecutionProperty.Key.DataFlowModel)))
        .forEach(e -> {
          throw new RuntimeException("DAG execution property check: "
              + "SideInput edge is not compatible with push" + e.getId());
        }));
    // DataSizeMetricCollection is not compatible with Push (All data have to be stored before the data collection)
    vertices.forEach(v -> incomingEdges.get(v).stream().filter(e -> e instanceof IREdge).map(e -> (IREdge) e)
        .filter(e -> MetricCollectionProperty.Value.DataSkewRuntimePass
                      .equals(e.getProperty(ExecutionProperty.Key.MetricCollection)))
        .filter(e -> DataFlowModelProperty.Value.Push.equals(e.getProperty(ExecutionProperty.Key.DataFlowModel)))
        .forEach(e -> {
          throw new RuntimeException("DAG execution property check: "
              + "DataSizeMetricCollection edge is not compatible with push" + e.getId());
        }));
    // All vertices with same Stage Id should have identical Parallelism execution property.
    final HashMap<Integer, Integer> stageIdToParallelismMap = new HashMap<>();
    vertices.stream().filter(v -> v instanceof IRVertex)
        .map(v -> (IRVertex) v)
        .forEach(v -> {
          final Integer stageId = v.getProperty(ExecutionProperty.Key.StageId);
          if (stageId != null) {
            if (!stageIdToParallelismMap.containsKey(stageId)) {
              stageIdToParallelismMap.put(stageId, v.getProperty(ExecutionProperty.Key.Parallelism));
            } else if (!stageIdToParallelismMap.get(stageId).equals(v.getProperty(ExecutionProperty.Key.Parallelism))) {
              throw new RuntimeException("DAG execution property check: vertices are in a same stage, "
                  + "but has different parallelism execution properties: Stage" + stageId + ": " + v.getId());
            }
          }
        });
  }

  /**
   * DAG integrity check function, that keeps DAG in shape.
   * @param cycle whether or not to check for cycles.
   * @param source whether or not to check sources.
   * @param sink whether or not to check sink.
   * @param executionProperty whether or not to check execution property.
   */
  private void integrityCheck(final boolean cycle,
                              final boolean source,
                              final boolean sink,
                              final boolean executionProperty) {
    if (cycle) {
      final Stack<V> stack = new Stack<>();
      final Set<V> visited = new HashSet<>();
      vertices.stream().filter(v -> incomingEdges.get(v).isEmpty()) // source operators
          .forEachOrdered(v -> cycleCheck(stack, visited, v));
    }
    if (source) {
      sourceCheck();
    }
    if (sink) {
      sinkCheck();
    }
    if (executionProperty) {
      executionPropertyCheck();
    }
  }

  ///////////////////////////BUILD///////////////////////////
  /**
   * Build the DAG for LoopVertex.
   * @return the DAG contained by the builder.
   */
  public DAG<V, E> buildWithoutSourceSinkCheck() {
    integrityCheck(true, false, false, true);
    return new DAG<>(vertices, incomingEdges, outgoingEdges, assignedLoopVertexMap, loopStackDepthMap);
  }

  /**
   * Build the DAG.
   * @return the DAG contained by the builder.
   */
  public DAG<V, E> build() {
    integrityCheck(true, true, true, true);
    return new DAG<>(vertices, incomingEdges, outgoingEdges, assignedLoopVertexMap, loopStackDepthMap);
  }
}
