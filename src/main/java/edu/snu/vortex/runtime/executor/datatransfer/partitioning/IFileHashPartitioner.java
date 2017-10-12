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
package edu.snu.vortex.runtime.executor.datatransfer.partitioning;

import edu.snu.vortex.compiler.ir.Element;
import edu.snu.vortex.runtime.executor.data.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * An implementation of {@link Partitioner} which hashes output data from a source task for I-File write.
 * It hashes data finer than {@link HashPartitioner}.
 * The {@link Element}s will be hashed by their key, and applied "modulo" operation.
 *
 * When we need to split or recombine the output data from a task after it is stored,
 * we multiply the hash range with a multiplier, which is commonly-known by the source and destination tasks,
 * to prevent the extra deserialize - rehash - serialize process.
 * For more information, please check {@link edu.snu.vortex.client.JobConf.HashRangeMultiplier}.
 */
public final class IFileHashPartitioner implements Partitioner {
  public static final String SIMPLE_NAME = "FinerHash";
  private final int hashRangeMultiplier; // Hash range multiplier.

  public IFileHashPartitioner(final int hashRangeMultiplier) {
    this.hashRangeMultiplier = hashRangeMultiplier;
  }

  @Override
  public List<Block> partition(final Iterable<Element> elements,
                               final int dstParallelism) {
    // For this hash range, please check the description of HashRangeMultiplier in JobConf.
    final int hashRange = hashRangeMultiplier * dstParallelism;

    // Separate the data into blocks according to the hash value of their key.
    final List<List<Element>> elementsByKey = new ArrayList<>(hashRange);
    IntStream.range(0, hashRange).forEach(hashVal -> elementsByKey.add(new ArrayList<>()));
    elements.forEach(element -> {
      // Hash the data by its key, and "modulo" by the hash range.
      final int hashVal = Math.abs(element.getKey().hashCode() % hashRange);
      elementsByKey.get(hashVal).add(element);
    });

    final List<Block> blocks = new ArrayList<>(hashRange);
    for (int hashIdx = 0; hashIdx < hashRange; hashIdx++) {
      final int blockHashIdx = Math.abs(hashIdx % hashRangeMultiplier);
      blocks.add(new Block(blockHashIdx, elementsByKey.get(hashIdx)));
    }
    return blocks;
  }
}
