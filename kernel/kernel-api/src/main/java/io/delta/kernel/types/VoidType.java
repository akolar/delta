/*
 * Copyright (2026) The Delta Lake Project Authors.
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
package io.delta.kernel.types;

import io.delta.kernel.annotation.Evolving;

/**
 * The data type of a column that contains only {@code null} values. Void columns are never written
 * to data files, and readers return them as all-{@code null} columns.
 *
 * <p>See the Void Type section of the Delta protocol:
 * https://github.com/delta-io/delta/blob/master/PROTOCOL.md#void-type.
 *
 * @since 4.5.0
 */
@Evolving
public class VoidType extends BasePrimitiveType {
  public static final VoidType VOID = new VoidType();

  private VoidType() {
    super("void");
  }
}
