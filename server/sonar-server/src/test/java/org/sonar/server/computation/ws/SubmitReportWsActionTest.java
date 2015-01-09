/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.computation.ws;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.server.computation.AnalysisReportQueue;
import org.sonar.server.computation.ComputationWorkerLauncher;

import java.io.InputStream;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SubmitReportWsActionTest {

  private static final String DEFAULT_PROJECT_KEY = "123456789-987654321";
  private SubmitReportWsAction sut;

  private ComputationWorkerLauncher analysisTaskLauncher;
  private AnalysisReportQueue queue;

  @Before
  public void before() {
    analysisTaskLauncher = mock(ComputationWorkerLauncher.class);
    queue = mock(AnalysisReportQueue.class);

    sut = new SubmitReportWsAction(queue, analysisTaskLauncher);
  }

  @Test
  public void define_metadata() throws Exception {
    WebService.Context context = new WebService.Context();
    WebService.NewController controller = context.createController("api/computation");
    sut.define(controller);
    controller.done();

    WebService.Action action = context.controller("api/computation").action("submit_report");
    assertThat(action).isNotNull();
    assertThat(action.params()).hasSize(3);
  }

  @Test
  public void add_element_to_queue_and_launch_analysis_task() throws Exception {
    Response response = mock(Response.class);
    Request request = mock(Request.class);

    when(request.mandatoryParam(SubmitReportWsAction.PARAM_PROJECT_KEY)).thenReturn(DEFAULT_PROJECT_KEY);
    when(request.mandatoryParamAsLong(SubmitReportWsAction.PARAM_SNAPSHOT)).thenReturn(123L);
    InputStream reportData = IOUtils.toInputStream("report-data");
    when(request.paramAsInputStream(SubmitReportWsAction.PARAM_REPORT_DATA)).thenReturn(reportData);

    sut.handle(request, response);

    verify(queue).add(DEFAULT_PROJECT_KEY, 123L, reportData);
    verify(analysisTaskLauncher).startAnalysisTaskNow();
  }

}
