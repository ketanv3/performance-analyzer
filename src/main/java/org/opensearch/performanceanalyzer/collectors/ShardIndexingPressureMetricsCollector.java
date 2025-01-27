/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.tools.json.JSONObject;
import org.jooq.tools.json.JSONParser;
import org.jooq.tools.json.ParseException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerApp;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ShardIndexingPressureDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ShardIndexingPressureValue;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.rca.framework.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.rca.framework.metrics.WriterMetrics;

public class ShardIndexingPressureMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(ShardIndexingPressureMetricsCollector.class)
                    .samplingInterval;
    private static final int KEYS_PATH_LENGTH = 0;
    private static final Logger LOG =
            LogManager.getLogger(ShardIndexingPressureMetricsCollector.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JSONParser parser = new JSONParser();

    public static final String SHARD_INDEXING_PRESSURE_CLASS_NAME =
            "org.opensearch.index.ShardIndexingPressure";
    public static final String CLUSTER_SERVICE_CLASS_NAME =
            "org.opensearch.cluster.service.ClusterService";
    public static final String INDEXING_PRESSURE_CLASS_NAME =
            "org.opensearch.index.IndexingPressure";
    public static final String SHARD_INDEXING_PRESSURE_STORE_CLASS_NAME =
            "org.opensearch.index.ShardIndexingPressureStore";
    public static final String INDEXING_PRESSURE_FIELD_NAME = "indexingPressure";
    public static final String SHARD_INDEXING_PRESSURE_FIELD_NAME = "shardIndexingPressure";
    public static final String SHARD_INDEXING_PRESSURE_STORE_FIELD_NAME =
            "shardIndexingPressureStore";
    public static final String SHARD_INDEXING_PRESSURE_HOT_STORE_FIELD_NAME =
            "shardIndexingPressureHotStore";

    private static final Integer MAX_HOT_STORE_LIMIT = 50;

    private final ConfigOverridesWrapper configOverridesWrapper;
    private final PerformanceAnalyzerController controller;
    private StringBuilder value;

    public ShardIndexingPressureMetricsCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(SAMPLING_TIME_INTERVAL, "ShardIndexingPressureMetricsCollector");
        value = new StringBuilder();
        this.configOverridesWrapper = configOverridesWrapper;
        this.controller = controller;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void collectMetrics(long startTime) {
        if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
            return;
        }

        long mCurrT = System.currentTimeMillis();
        try {
            ClusterService clusterService = OpenSearchResources.INSTANCE.getClusterService();
            if (clusterService != null) {
                Object indexingPressure =
                        getField(CLUSTER_SERVICE_CLASS_NAME, INDEXING_PRESSURE_FIELD_NAME)
                                .get(clusterService);
                if (indexingPressure != null) {
                    Object shardIndexingPressure =
                            getField(
                                            INDEXING_PRESSURE_CLASS_NAME,
                                            SHARD_INDEXING_PRESSURE_FIELD_NAME)
                                    .get(indexingPressure);
                    Object shardIndexingPressureStore =
                            getField(
                                            SHARD_INDEXING_PRESSURE_CLASS_NAME,
                                            SHARD_INDEXING_PRESSURE_STORE_FIELD_NAME)
                                    .get(shardIndexingPressure);
                    Map<Long, Object> shardIndexingPressureHotStore =
                            (Map<Long, Object>)
                                    getField(
                                                    SHARD_INDEXING_PRESSURE_STORE_CLASS_NAME,
                                                    SHARD_INDEXING_PRESSURE_HOT_STORE_FIELD_NAME)
                                            .get(shardIndexingPressureStore);

                    value.setLength(0);
                    shardIndexingPressureHotStore.entrySet().stream()
                            .limit(MAX_HOT_STORE_LIMIT)
                            .forEach(
                                    storeObject -> {
                                        try {
                                            JSONObject tracker =
                                                    (JSONObject)
                                                            parser.parse(
                                                                    mapper.writeValueAsString(
                                                                            storeObject
                                                                                    .getValue()));
                                            JSONObject shardId =
                                                    (JSONObject)
                                                            parser.parse(
                                                                    mapper.writeValueAsString(
                                                                            tracker.get(
                                                                                    "shardId")));
                                            value.append(
                                                            PerformanceAnalyzerMetrics
                                                                    .getJsonCurrentMilliSeconds())
                                                    .append(
                                                            PerformanceAnalyzerMetrics
                                                                    .sMetricNewLineDelimitor);
                                            value.append(
                                                            new ShardIndexingPressureStatus(
                                                                            AllMetrics.IndexingStage
                                                                                    .COORDINATING
                                                                                    .toString(),
                                                                            shardId.get("indexName")
                                                                                    .toString(),
                                                                            shardId.get("id")
                                                                                    .toString(),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "coordinatingRejections")
                                                                                            .toString()),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "currentCoordinatingBytes")
                                                                                            .toString()),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "primaryAndCoordinatingLimits")
                                                                                            .toString()),
                                                                            Double.longBitsToDouble(
                                                                                    Long.parseLong(
                                                                                            tracker.get(
                                                                                                            "coordinatingThroughputMovingAverage")
                                                                                                    .toString())),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "lastSuccessfulCoordinatingRequestTimestamp")
                                                                                            .toString()))
                                                                    .serialize())
                                                    .append(
                                                            PerformanceAnalyzerMetrics
                                                                    .sMetricNewLineDelimitor);
                                            value.append(
                                                            new ShardIndexingPressureStatus(
                                                                            AllMetrics.IndexingStage
                                                                                    .PRIMARY
                                                                                    .toString(),
                                                                            shardId.get("indexName")
                                                                                    .toString(),
                                                                            shardId.get("id")
                                                                                    .toString(),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "primaryRejections")
                                                                                            .toString()),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "currentPrimaryBytes")
                                                                                            .toString()),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "primaryAndCoordinatingLimits")
                                                                                            .toString()),
                                                                            Double.longBitsToDouble(
                                                                                    Long.parseLong(
                                                                                            tracker.get(
                                                                                                            "primaryThroughputMovingAverage")
                                                                                                    .toString())),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "lastSuccessfulPrimaryRequestTimestamp")
                                                                                            .toString()))
                                                                    .serialize())
                                                    .append(
                                                            PerformanceAnalyzerMetrics
                                                                    .sMetricNewLineDelimitor);
                                            value.append(
                                                            new ShardIndexingPressureStatus(
                                                                            AllMetrics.IndexingStage
                                                                                    .REPLICA
                                                                                    .toString(),
                                                                            shardId.get("indexName")
                                                                                    .toString(),
                                                                            shardId.get("id")
                                                                                    .toString(),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "replicaRejections")
                                                                                            .toString()),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "currentReplicaBytes")
                                                                                            .toString()),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "replicaLimits")
                                                                                            .toString()),
                                                                            Double.longBitsToDouble(
                                                                                    Long.parseLong(
                                                                                            tracker.get(
                                                                                                            "replicaThroughputMovingAverage")
                                                                                                    .toString())),
                                                                            Long.parseLong(
                                                                                    tracker.get(
                                                                                                    "lastSuccessfulReplicaRequestTimestamp")
                                                                                            .toString()))
                                                                    .serialize())
                                                    .append(
                                                            PerformanceAnalyzerMetrics
                                                                    .sMetricNewLineDelimitor);
                                        } catch (JsonProcessingException | ParseException e) {
                                            LOG.debug(
                                                    "Exception raised while parsing string to json object. Skipping IndexingPressureMetricsCollector");
                                        }
                                    });
                }
            }
            if (value.length() != 0) {
                saveMetricValues(value.toString(), startTime);
                PerformanceAnalyzerApp.WRITER_METRICS_AGGREGATOR.updateStat(
                        WriterMetrics.SHARD_INDEXING_PRESSURE_COLLECTOR_EXECUTION_TIME,
                        "",
                        System.currentTimeMillis() - mCurrT);
            }
        } catch (Exception ex) {
            PerformanceAnalyzerApp.ERRORS_AND_EXCEPTIONS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.SHARD_INDEXING_PRESSURE_COLLECTOR_ERROR, "", 1);
            LOG.debug(
                    "Exception in Collecting Shard Indexing Pressure Metrics: {} for startTime {}",
                    () -> ex.toString(),
                    () -> startTime);
        }
    }

    Field getField(String className, String fieldName)
            throws NoSuchFieldException, ClassNotFoundException {
        Class<?> clusterServiceClass = Class.forName(className);
        Field indexingPressureField = clusterServiceClass.getDeclaredField(fieldName);
        indexingPressureField.setAccessible(true);
        return indexingPressureField;
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sShardIndexingPressurePath);
    }

    static class ShardIndexingPressureStatus extends MetricStatus {
        private final String indexingStage;
        private final String indexName;
        private final String shardId;
        private final long rejectionCount;
        private final long currentBytes;
        private final long currentLimits;
        private final double averageWindowThroughput;
        private final long lastSuccessfulTimestamp;

        public ShardIndexingPressureStatus(
                String indexingStage,
                String indexName,
                String shardId,
                long rejectionCount,
                long currentBytes,
                long currentLimits,
                double averageWindowThroughput,
                long lastSuccessfulTimestamp) {
            this.indexingStage = indexingStage;
            this.indexName = indexName;
            this.shardId = shardId;
            this.rejectionCount = rejectionCount;
            this.currentBytes = currentBytes;
            this.currentLimits = currentLimits;
            this.averageWindowThroughput = averageWindowThroughput;
            this.lastSuccessfulTimestamp = lastSuccessfulTimestamp;
        }

        @JsonProperty(ShardIndexingPressureDimension.Constants.INDEXING_STAGE)
        public String getIndexingStage() {
            return indexingStage;
        }

        @JsonProperty(ShardIndexingPressureDimension.Constants.INDEX_NAME_VALUE)
        public String getIndexName() {
            return indexName;
        }

        @JsonProperty(ShardIndexingPressureDimension.Constants.SHARD_ID_VALUE)
        public String getShardId() {
            return shardId;
        }

        @JsonProperty(ShardIndexingPressureValue.Constants.REJECTION_COUNT_VALUE)
        public long getRejectionCount() {
            return rejectionCount;
        }

        @JsonProperty(ShardIndexingPressureValue.Constants.CURRENT_BYTES)
        public long getCurrentBytes() {
            return currentBytes;
        }

        @JsonProperty(ShardIndexingPressureValue.Constants.CURRENT_LIMITS)
        public long getCurrentLimits() {
            return currentLimits;
        }

        @JsonProperty(ShardIndexingPressureValue.Constants.AVERAGE_WINDOW_THROUGHPUT)
        public double getAverageWindowThroughput() {
            return averageWindowThroughput;
        }

        @JsonProperty(ShardIndexingPressureValue.Constants.LAST_SUCCESSFUL_TIMESTAMP)
        public long getLastSuccessfulTimestamp() {
            return lastSuccessfulTimestamp;
        }
    }
}
