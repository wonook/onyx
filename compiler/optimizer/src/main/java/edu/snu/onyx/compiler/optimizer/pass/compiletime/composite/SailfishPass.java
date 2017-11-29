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
package edu.snu.onyx.compiler.optimizer.pass.compiletime.composite;

import edu.snu.onyx.compiler.optimizer.pass.compiletime.annotating.SailfishEdgeDataFlowModelPass;
import edu.snu.onyx.compiler.optimizer.pass.compiletime.annotating.SailfishEdgeDataStorePass;
import edu.snu.onyx.compiler.optimizer.pass.compiletime.reshaping.SailfishReshapingPass;

import java.util.Arrays;

/**
 * A series of passes to support Sailfish-like disk seek batching during shuffle.
 */
public final class SailfishPass extends CompositePass {
  public static final String SIMPLE_NAME = "SailfishPass";

  public SailfishPass() {
    super(Arrays.asList(
        new SailfishReshapingPass(),
        new SailfishEdgeDataStorePass(),
        new SailfishEdgeDataFlowModelPass()
    ));
  }
}