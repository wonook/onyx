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
package edu.snu.onyx.common.ir.edge.executionproperty;

import edu.snu.onyx.common.ir.executionproperty.ExecutionProperty;

/**
 * DataStore ExecutionProperty.
 */
public final class DataStoreProperty extends ExecutionProperty<DataStoreProperty.Value> {
  /**
   * Constructor.
   * @param value value of the execution property.
   */
  private DataStoreProperty(final Value value) {
    super(Key.DataStore, value);
  }

  /**
   * Static method exposing the constructor.
   * @param value value of the new execution property.
   * @return the newly created execution property.
   */
  public static DataStoreProperty of(final Value value) {
    return new DataStoreProperty(value);
  }

  /**
   * Possible values of DataStore ExecutionProperty.
   */
  public enum Value {
    MemoryStore,
    SerializedMemoryStore,
    LocalFileStore,
    GlusterFileStore
  }
}
