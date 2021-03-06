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
package edu.snu.onyx.tests.client;

import edu.snu.onyx.client.ClientEndpoint;
import edu.snu.onyx.client.DriverEndpoint;
import edu.snu.onyx.client.StateTranslator;
import edu.snu.onyx.common.dag.DAG;
import edu.snu.onyx.common.dag.DAGBuilder;
import edu.snu.onyx.common.ir.edge.IREdge;
import edu.snu.onyx.common.ir.vertex.IRVertex;
import edu.snu.onyx.conf.JobConf;
import edu.snu.onyx.runtime.common.message.MessageEnvironment;
import edu.snu.onyx.runtime.common.message.local.LocalMessageDispatcher;
import edu.snu.onyx.runtime.common.message.local.LocalMessageEnvironment;
import edu.snu.onyx.runtime.common.plan.physical.PhysicalPlan;
import edu.snu.onyx.runtime.common.plan.physical.PhysicalPlanGenerator;
import edu.snu.onyx.runtime.common.plan.physical.PhysicalStage;
import edu.snu.onyx.runtime.common.plan.physical.PhysicalStageEdge;
import edu.snu.onyx.runtime.common.state.JobState;
import edu.snu.onyx.runtime.master.MetricMessageHandler;
import edu.snu.onyx.runtime.master.BlockManagerMaster;
import edu.snu.onyx.runtime.master.JobStateManager;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.Tang;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test {@link ClientEndpoint}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(MetricMessageHandler.class)
public class ClientEndpointTest {
  private static final int MAX_SCHEDULE_ATTEMPT = 2;
  private final MetricMessageHandler metricMessageHandler = mock(MetricMessageHandler.class);

  @Test(timeout = 3000)
  public void testState() throws Exception {
    // Create a simple client endpoint that returns given job state.
    final StateTranslator stateTranslator = mock(StateTranslator.class);
    when(stateTranslator.translateState(any())).then(state -> state.getArgumentAt(0, JobState.State.class));
    final ClientEndpoint clientEndpoint = new TestClientEndpoint(stateTranslator);
    assertEquals(clientEndpoint.getJobState(), JobState.State.READY);

    // Wait for connection but not connected.
    assertEquals(clientEndpoint.waitUntilJobFinish(100, TimeUnit.MILLISECONDS), JobState.State.READY);

    // Create a JobStateManager of an empty dag and create a DriverEndpoint with it.
    final DAGBuilder<IRVertex, IREdge> irDagBuilder = new DAGBuilder<>();
    final DAG<IRVertex, IREdge> irDAG = irDagBuilder.build();
    final Injector injector = Tang.Factory.getTang().newInjector();
    injector.bindVolatileParameter(JobConf.DAGDirectory.class, "");
    final PhysicalPlanGenerator physicalPlanGenerator = injector.getInstance(PhysicalPlanGenerator.class);
    final DAG<PhysicalStage, PhysicalStageEdge> physicalDAG = irDAG.convert(physicalPlanGenerator);

    final LocalMessageDispatcher messageDispatcher = new LocalMessageDispatcher();
    final LocalMessageEnvironment messageEnvironment =
        new LocalMessageEnvironment(MessageEnvironment.MASTER_COMMUNICATION_ID, messageDispatcher);
    injector.bindVolatileInstance(MessageEnvironment.class, messageEnvironment);
    final BlockManagerMaster pmm = injector.getInstance(BlockManagerMaster.class);
    final JobStateManager jobStateManager = new JobStateManager(
        new PhysicalPlan("TestPlan", physicalDAG, physicalPlanGenerator.getTaskIRVertexMap()),
        pmm, metricMessageHandler, MAX_SCHEDULE_ATTEMPT);

    final DriverEndpoint driverEndpoint = new DriverEndpoint(jobStateManager, clientEndpoint);

    // Check the current state.
    assertEquals(clientEndpoint.getJobState(), JobState.State.EXECUTING);

    // Wait for the job to finish but not finished
    assertEquals(clientEndpoint.waitUntilJobFinish(100, TimeUnit.MILLISECONDS), JobState.State.EXECUTING);

    // Check finish.
    jobStateManager.onJobStateChanged(JobState.State.COMPLETE);
    assertEquals(clientEndpoint.waitUntilJobFinish(), JobState.State.COMPLETE);
  }

  /**
   * A simple {@link ClientEndpoint} for test.
   */
  private final class TestClientEndpoint extends ClientEndpoint {

    TestClientEndpoint(final StateTranslator stateTranslator) {
      super(stateTranslator);
    }
  }
}
