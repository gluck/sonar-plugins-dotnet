/*
 * Sonar C# Plugin :: Gallio
 * Copyright (C) 2010 Jose Chillan, Alexandre Victoor and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.csharp.gallio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.Project;
import org.sonar.dotnet.tools.commons.visualstudio.VisualStudioProject;
import org.sonar.dotnet.tools.commons.visualstudio.VisualStudioSolution;
import org.sonar.plugins.csharp.api.CSharpConfiguration;
import org.sonar.plugins.csharp.api.MicrosoftWindowsEnvironment;
import org.sonar.plugins.csharp.gallio.results.coverage.CoverageResultParser;
import org.sonar.plugins.csharp.gallio.results.coverage.model.FileCoverage;
import org.sonar.plugins.csharp.gallio.results.coverage.model.ParserResult;
import org.sonar.plugins.csharp.gallio.results.coverage.model.ProjectCoverage;
import org.sonar.plugins.csharp.gallio.results.execution.GallioResultParser;
import org.sonar.plugins.csharp.gallio.results.execution.model.UnitTestReport;
import org.sonar.test.TestUtils;

import com.google.common.collect.Lists;

public class TestReportSensorTest {

  private VisualStudioSolution solution;
  private VisualStudioProject vsProject1;
  private VisualStudioProject vsTestProject2;
  private MicrosoftWindowsEnvironment microsoftWindowsEnvironment;
  private Project project;
  private GallioResultParser parser;

  @Before
  public void init() {
    vsProject1 = mock(VisualStudioProject.class);
    when(vsProject1.getName()).thenReturn("Project #1");
    vsTestProject2 = mock(VisualStudioProject.class);
    when(vsTestProject2.getName()).thenReturn("Project Test #2");
    when(vsTestProject2.isTest()).thenReturn(true);
    solution = mock(VisualStudioSolution.class);
    when(solution.getProjects()).thenReturn(Lists.newArrayList(vsProject1, vsTestProject2));
    when(solution.getTestProjects()).thenReturn(Lists.newArrayList(vsTestProject2));

    microsoftWindowsEnvironment = new MicrosoftWindowsEnvironment();
    microsoftWindowsEnvironment.setCurrentSolution(solution);

    project = mock(Project.class);
    when(project.getLanguageKey()).thenReturn("cs");
    when(project.getName()).thenReturn("Project Test #2");
    
    parser = mock(GallioResultParser.class); // create the parser before the sensor
  }
  
  @Test
  public void testAnalyseEmpty() {
    
    Configuration conf = new BaseConfiguration();
    TestReportSensor sensor = buildSensor(conf);
    
    microsoftWindowsEnvironment.setTestExecutionDone();
    File solutionDir = TestUtils.getResource("/Results/execution/");
    microsoftWindowsEnvironment.setWorkingDirectory("");
    when(solution.getSolutionDir()).thenReturn(solutionDir);
    when(solution.getProject("MyAssembly")).thenReturn(vsProject1);
    
    when(parser.parse(any(File.class))).thenReturn(Collections.EMPTY_SET);

    SensorContext context = mock(SensorContext.class);
    
    sensor.analyse(project, context);
    
    verify(parser).parse(any(File.class));
  }

  @Test
  public void testShouldExecuteOnProject() throws Exception {
    Configuration conf = new BaseConfiguration();
    TestReportSensor sensor = buildSensor(conf);
    assertTrue(sensor.shouldExecuteOnProject(project));
  }

  @Test
  public void testShouldNotExecuteOnProjectIfSkip() throws Exception {
    Configuration conf = new BaseConfiguration();
    conf.setProperty(GallioConstants.MODE, TestReportSensor.MODE_SKIP);
    TestReportSensor sensor = buildSensor(conf);
    assertFalse(sensor.shouldExecuteOnProject(project));
  }

  @Test
  public void testShouldNotExecuteOnProjectIfNotTestProject() throws Exception {
    Configuration conf = new BaseConfiguration();
    TestReportSensor sensor = buildSensor(conf);
    when(project.getName()).thenReturn("Project #1");
    assertFalse(sensor.shouldExecuteOnProject(project));
  }
  
  private TestReportSensor buildSensor(Configuration conf) {
    return new TestReportSensor(new CSharpConfiguration(conf), microsoftWindowsEnvironment, parser);
  }
}