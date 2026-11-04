/*
 * Copyright (2023) The Delta Lake Project Authors.
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
package io.delta.kernel.internal.icebergcompat;

import static io.delta.kernel.internal.tablefeatures.TableFeatures.*;
import static java.util.stream.Collectors.toList;

import io.delta.kernel.exceptions.KernelException;
import io.delta.kernel.internal.DeltaErrors;
import io.delta.kernel.internal.TableConfig;
import io.delta.kernel.internal.actions.Metadata;
import io.delta.kernel.internal.actions.Protocol;
import io.delta.kernel.internal.columndefaults.ColumnDefaults;
import io.delta.kernel.internal.tablefeatures.TableFeature;
import io.delta.kernel.internal.util.SchemaIterable;
import io.delta.kernel.types.MapType;
import io.delta.kernel.types.StructField;
import io.delta.kernel.types.VoidType;
import io.delta.kernel.utils.DataFileStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Utility methods for validation and compatibility checks for Iceberg V3. */
public class IcebergCompatV3MetadataValidatorAndUpdater
    extends IcebergCompatMetadataValidatorAndUpdater {

  /**
   * Validates that any change to property {@link TableConfig#ICEBERG_COMPAT_V3_ENABLED} is valid.
   * Currently, the changes we support are
   *
   * <ul>
   *   <li>No change in enablement (true to true or false to false)
   * </ul>
   *
   * The changes that we do not support and for which we throw an {@link KernelException} are
   *
   * <ul>
   *   <li>Disabling on an existing table (true to false)
   *   <li>Enabling on an existing table (false to true)
   * </ul>
   */
  public static void validateIcebergCompatV3Change(
      Map<String, String> oldConfig, Map<String, String> newConfig) {
    blockConfigChangeOnExistingTable(TableConfig.ICEBERG_COMPAT_V3_ENABLED, oldConfig, newConfig);
  }

  /**
   * Validate and update the given Iceberg V3 metadata.
   *
   * @param newMetadata Metadata after the current updates
   * @param newProtocol Protocol after the current updates
   * @return The updated metadata if the metadata is valid and updated, otherwise empty.
   * @throws UnsupportedOperationException if the metadata is not compatible with Iceberg V3
   *     requirements
   */
  public static Optional<Metadata> validateAndUpdateIcebergCompatV3Metadata(
      boolean isCreatingNewTable,
      Metadata newMetadata,
      Protocol newProtocol,
      Optional<Protocol> prevProtocol) {
    return INSTANCE.validateAndUpdateMetadata(
        new IcebergCompatInputContext(
            INSTANCE.compatFeatureName(),
            isCreatingNewTable,
            newMetadata,
            newProtocol,
            prevProtocol));
  }

  /**
   * Validate the given {@link DataFileStatus} that is being added as a {@code add} action to Delta
   * Log. Currently, it checks that the statistics are not empty.
   *
   * @param dataFileStatus The {@link DataFileStatus} to validate.
   */
  public static void validateDataFileStatus(DataFileStatus dataFileStatus) {
    validateDataFileStatus(dataFileStatus, INSTANCE.compatFeatureName());
  }

  /// //////////////////////////////////////////////////////////////////////////////
  /// Define the compatibility and update checks for icebergCompatV3             ///
  /// //////////////////////////////////////////////////////////////////////////////

  private static final IcebergCompatV3MetadataValidatorAndUpdater INSTANCE =
      new IcebergCompatV3MetadataValidatorAndUpdater();

  @Override
  String compatFeatureName() {
    return "icebergCompatV3";
  }

  @Override
  TableConfig<Boolean> requiredDeltaTableProperty() {
    return TableConfig.ICEBERG_COMPAT_V3_ENABLED;
  }

  @Override
  List<IcebergCompatRequiredTablePropertyEnforcer> requiredDeltaTableProperties() {
    return Stream.of(COLUMN_MAPPING_REQUIREMENT, ROW_TRACKING_ENABLED).collect(toList());
  }

  @Override
  List<TableFeature> requiredDependencyTableFeatures() {
    return Stream.of(ICEBERG_COMPAT_V3_W_FEATURE, COLUMN_MAPPING_RW_FEATURE, ROW_TRACKING_W_FEATURE)
        .collect(toList());
  }

  @Override
  List<IcebergCompatCheck> icebergCompatChecks() {
    return Stream.of(
            V3_CHECK_HAS_SUPPORTED_TYPES,
            CHECK_ONLY_ICEBERG_COMPAT_V3_ENABLED,
            CHECK_HAS_ALLOWED_PARTITION_TYPES,
            CHECK_HAS_NO_PARTITION_EVOLUTION,
            CHECK_HAS_SUPPORTED_TYPE_WIDENING,
            CHECK_VOID_FIELDS_ARE_NULLABLE_WITHOUT_DEFAULTS,
            CHECK_LITERAL_DEFAULT_VALUE)
        .collect(toList());
  }

  /**
   * Void maps to Iceberg's {@code unknown} type, which the Iceberg spec requires to be optional, so
   * void fields must be nullable. Only the nullability of the field that holds the void counts, not
   * that of its parents. Void map keys are always rejected, because map keys can't be null. Any
   * default value on a void field is rejected, even {@code NULL}: the spec only allows a null
   * default for {@code unknown}, and a void field without a default is already null.
   */
  protected static IcebergCompatCheck CHECK_VOID_FIELDS_ARE_NULLABLE_WITHOUT_DEFAULTS =
      (inputContext) -> {
        for (SchemaIterable.SchemaElement element :
            new SchemaIterable(inputContext.newMetadata.getSchema())) {
          StructField field = element.getField();
          if (!(field.getDataType() instanceof VoidType)) {
            continue;
          }
          // Checked before nullability, since a map key built from a StructField can be nullable.
          boolean isMapKey =
              !element.isStructField() && field.getName().equals(MapType.MAP_KEY_NAME);
          if (isMapKey) {
            throw DeltaErrors.icebergCompatVoidMapKey(
                inputContext.compatFeatureName, element.getNamePath());
          }
          if (!field.isNullable()) {
            throw DeltaErrors.icebergCompatVoidFieldNotNullable(
                inputContext.compatFeatureName, element.getNamePath());
          }
          if (ColumnDefaults.getRawDefaultValue(field) != null) {
            throw DeltaErrors.icebergCompatVoidFieldWithDefault(
                inputContext.compatFeatureName, element.getNamePath());
          }
        }
      };

  protected static IcebergCompatCheck CHECK_LITERAL_DEFAULT_VALUE =
      (inputContext) ->
          ColumnDefaults.validateSchemaForIcebergCompat(
              inputContext.newMetadata.getSchema(), ICEBERG_COMPAT_V3_W_FEATURE.featureName());
}
