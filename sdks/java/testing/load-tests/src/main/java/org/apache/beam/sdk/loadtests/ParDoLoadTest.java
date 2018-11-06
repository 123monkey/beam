/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.loadtests;

import static org.apache.beam.sdk.loadtests.SyntheticUtils.fromJsonString;

import java.io.IOException;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.synthetic.SyntheticBoundedIO;
import org.apache.beam.sdk.io.synthetic.SyntheticBoundedIO.SyntheticSourceOptions;
import org.apache.beam.sdk.io.synthetic.SyntheticStep;
import org.apache.beam.sdk.loadtests.metrics.MetricsMonitor;
import org.apache.beam.sdk.loadtests.metrics.MetricsPublisher;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

/**
 * Load test for {@link ParDo} operation.
 *
 * <p>The purpose of this test is to measure {@link ParDo}'s behaviour in stressful
 * conditions. It uses {@link SyntheticBoundedIO} and {@link SyntheticStep} which both can be
 * parametrized to generate keys and values of various size, impose delay (sleep or cpu burnout) in
 * various moments during the pipeline execution and provide some other performance challenges.
 *
 * @see SyntheticStep
 * @see SyntheticBoundedIO
 *
 * <p>To run it manually, use the following command:
 *
 * <pre>
 *    ./gradlew :beam-sdks-java-load-tests:run -PloadTest.args='
 *      --numberOfCounterOperations=1
 *      --sourceOptions={"numRecords":1000,...}
 *      --stepOptions={"outputRecordsPerInputRecord":2...}'
 *      -PloadTest.mainClass="org.apache.beam.sdk.loadtests.ParDoLoadTest"
 * </pre>
 */
public class ParDoLoadTest {

  private static final String METRICS_NAMESPACE = "pardo";

  /** Pipeline options specific for this test. */
  public interface Options extends LoadTestOptions {

    @Description("Number consequent of ParDo operations (SyntheticSteps) to be performed.")
    @Default.Integer(1)
    Integer getNumberOfCounterOperations();

    void setNumberOfCounterOperations(Integer count);

    @Override
    @Description("Options for synthetic step")
    @Validation.Required
    String getStepOptions();
  }

  public static void main(String[] args) throws IOException {
    Options options = LoadTestOptions.readFromArgs(args, Options.class);

    SyntheticSourceOptions sourceOptions = fromJsonString(options.getSourceOptions(),
        SyntheticSourceOptions.class);

    SyntheticStep.Options stepOptions = fromJsonString(options.getStepOptions(),
        SyntheticStep.Options.class);

    Pipeline pipeline = Pipeline.create(options);

    PCollection<KV<byte[], byte[]>> input = pipeline
        .apply("Read input", SyntheticBoundedIO.readFrom(sourceOptions));

    for(int i = 0; i < options.getNumberOfCounterOperations(); i++) {
      input = input.apply(String.format("Step: %d", i), ParDo.of(new SyntheticStep(stepOptions)));
    }

    input.apply("Collect metrics", ParDo.of(new MetricsMonitor(METRICS_NAMESPACE)));

    PipelineResult result = pipeline.run();
    result.waitUntilFinish();
    MetricsPublisher.toConsole(result, METRICS_NAMESPACE);
  }
}
