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

package org.opensearch.performanceanalyzer.config;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;

import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.ScheduledMetricCollectorsExecutor;

public class PerformanceAnalyzerControllerTests {
    private static final int NUM_OF_SHARDS_PER_COLLECTION = 1;
    private Settings settings;
    private PerformanceAnalyzerController controller;

    @Before
    public void init() {
        initMocks(this);
        settings = Settings.builder().put("path.home", "./").build();
        OpenSearchResources.INSTANCE.setSettings(settings);
        OpenSearchResources.INSTANCE.setConfigPath(Paths.get("build/tmp/junit_metrics"));
        controller = new PerformanceAnalyzerController(new ScheduledMetricCollectorsExecutor());
    }

    @Test
    public void testGetNodeStatsShardsPerCollection() {
        Assert.assertEquals(
                PerformanceAnalyzerController.DEFAULT_NUM_OF_SHARDS_PER_COLLECTION,
                controller.getNodeStatsShardsPerCollection());

        controller.updateNodeStatsShardsPerCollection(NUM_OF_SHARDS_PER_COLLECTION);
        assertEquals(NUM_OF_SHARDS_PER_COLLECTION, controller.getNodeStatsShardsPerCollection());
    }
}
