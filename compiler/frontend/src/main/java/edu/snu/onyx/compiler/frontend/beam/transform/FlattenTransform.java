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
package edu.snu.onyx.compiler.frontend.beam.transform;

import edu.snu.onyx.common.ir.OutputCollector;
import edu.snu.onyx.common.ir.Transform;
import org.apache.beam.sdk.util.WindowedValue;

import java.util.ArrayList;

/**
 * Flatten transform implementation.
 * @param <T> input/output type.
 */
public final class FlattenTransform<T> implements Transform<WindowedValue<T>, WindowedValue<T>> {
  private final ArrayList<WindowedValue<T>> collectedElements;
  private OutputCollector<WindowedValue<T>> outputCollector;

  /**
   * FlattenTransform Constructor.
   */
  public FlattenTransform() {
    this.collectedElements = new ArrayList<>();
  }

  @Override
  public void prepare(final Context context, final OutputCollector<WindowedValue<T>> oc) {
    this.outputCollector = oc;
  }

  @Override
  public void onData(final WindowedValue<T> element) {
    collectedElements.add(element);
  }

  @Override
  public void close() {
    collectedElements.forEach(outputCollector::emit);
    collectedElements.clear();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("FlattenTransform:");
    sb.append(super.toString());
    return sb.toString();
  }
}
