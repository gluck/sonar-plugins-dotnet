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

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metric;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.ProjectFileSystem;
import org.sonar.api.utils.ParsingUtils;
import org.sonar.plugins.csharp.api.CSharpConfiguration;
import org.sonar.plugins.csharp.api.CSharpConstants;
import org.sonar.plugins.csharp.api.MicrosoftWindowsEnvironment;
import org.sonar.plugins.csharp.api.sensor.AbstractTestCSharpSensor;
import org.sonar.plugins.csharp.gallio.results.TestCaseDetail;
import org.sonar.plugins.csharp.gallio.results.TestStatus;
import org.sonar.plugins.csharp.gallio.results.UnitTestReport;

/**
 * Collects the FXCop reporting into sonar.
 */
@DependsUpon(CSharpConstants.CSHARP_CORE_EXECUTED)
public class GallioSensor extends AbstractTestCSharpSensor {

  private static final Logger LOG = LoggerFactory.getLogger(GallioSensor.class);

  private ProjectFileSystem fileSystem;
  private CSharpConfiguration configuration;
  private String executionMode;

  /**
   * Constructs a {@link GallioSensor}.
   * 
   * @param fileSystem
   * @param ruleFinder
   * @param fxCopRunner
   * @param profileExporter
   * @param rulesProfile
   */
  public GallioSensor(ProjectFileSystem fileSystem, CSharpConfiguration configuration,
      MicrosoftWindowsEnvironment microsoftWindowsEnvironment) {
    super(microsoftWindowsEnvironment);
    this.fileSystem = fileSystem;
    this.configuration = configuration;
    this.executionMode = configuration.getString(GallioConstants.MODE, "");
  }

  /**
   * {@inheritDoc}
   */
  public boolean shouldExecuteOnProject(Project project) {
    boolean skipMode = GallioConstants.MODE_SKIP.equalsIgnoreCase(executionMode);
    if (skipMode) {
      LOG.info("Gallio plugin won't execute as it is set to 'skip' mode.");
    }

    return super.shouldExecuteOnProject(project) && !skipMode;
  }

  @Override
  public void analyse(Project project, SensorContext context) {
    String workDir = fileSystem.getSonarWorkingDirectory().getAbsolutePath()
        .substring(fileSystem.getBasedir().getAbsolutePath().length() + 1);

    File reportFile = new File(getMicrosoftWindowsEnvironment().getCurrentSolution().getSolutionDir(), workDir + "/"
        + GallioConstants.GALLIO_REPORT_XML);

    if ( !reportFile.isFile()) {
      LOG.warn("No report file found for: " + reportFile.getAbsolutePath());
      return;
    }

    collect(project, reportFile, context);
  }

  private void collect(Project project, File report, SensorContext context) {
    GallioResultParser parser = new GallioResultParser();
    Collection<UnitTestReport> reports = parser.parse(report);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Found " + reports.size() + " test data");
    }

    Set<File> csFilesAlreadyTreated = new HashSet<File>();

    for (UnitTestReport testReport : reports) {
      File sourceFile = testReport.getSourceFile();
      if (sourceFile != null && sourceFile.exists() && !csFilesAlreadyTreated.contains(sourceFile)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Collecting test data for file " + sourceFile);
        }
        csFilesAlreadyTreated.add(sourceFile);
        int testsCount = testReport.getTests() - testReport.getSkipped();
        org.sonar.api.resources.File testFile = fromIOFile(sourceFile, project);
        if (testFile != null) {
          saveFileMeasure(testFile, context, CoreMetrics.SKIPPED_TESTS, testReport.getSkipped());
          saveFileMeasure(testFile, context, CoreMetrics.TESTS, testsCount);
          saveFileMeasure(testFile, context, CoreMetrics.TEST_ERRORS, testReport.getErrors());
          saveFileMeasure(testFile, context, CoreMetrics.TEST_FAILURES, testReport.getFailures());
          saveFileMeasure(testFile, context, CoreMetrics.TEST_EXECUTION_TIME, testReport.getTimeMS());
          saveFileMeasure(testFile, context, GallioMetrics.COUNT_ASSERTS, testReport.getAsserts());
          int passedTests = testsCount - testReport.getErrors() - testReport.getFailures();
          if (testsCount > 0) {
            double percentage = (float) passedTests * 100 / (float) testsCount;
            saveFileMeasure(testFile, context, CoreMetrics.TEST_SUCCESS_DENSITY, ParsingUtils.scaleValue(percentage));
          }
          saveTestsDetails(testFile, context, testReport);
        }
      } else {
        LOG.error("Source file not found for test report " + testReport);
      }
    }
  }

  /**
   * Stores the test details in XML format.
   * 
   * @param testFile
   * @param context
   * @param fileReport
   * @throws TransformerException
   */
  private void saveTestsDetails(org.sonar.api.resources.File testFile, SensorContext context, UnitTestReport fileReport) {
    StringBuilder testCaseDetails = new StringBuilder(256);
    testCaseDetails.append("<tests-details>");
    List<TestCaseDetail> details = fileReport.getDetails();
    for (TestCaseDetail detail : details) {
      testCaseDetails.append("<testcase status=\"").append(detail.getStatus().getSonarStatus()).append("\" time=\"")
          .append(detail.getTimeMillis()).append("\" name=\"").append(detail.getName()).append("\"");
      // .append("\" asserts=\"").append(detail.getCountAsserts())
      // .append("\"");
      boolean isError = (detail.getStatus() == TestStatus.ERROR);
      if (isError || (detail.getStatus() == TestStatus.FAILED)) {

        testCaseDetails.append(">").append(isError ? "<error message=\"" : "<failure message=\"").append(detail.getFormatedErrorMessage())
            .append("\">").append("<![CDATA[").append(detail.getFormatedStackTrace()).append("]]>")
            .append(isError ? "</error>" : "</failure>").append("</testcase>");
      } else {
        testCaseDetails.append("/>");
      }
    }
    testCaseDetails.append("</tests-details>");
    context.saveMeasure(testFile, new Measure(CoreMetrics.TEST_DATA, testCaseDetails.toString()));
    LOG.debug("test detail : {}", testCaseDetails);
  }

  /**
   * Saves the measure the a test file.
   * 
   * @param project
   * @param context
   * @param fileReport
   * @param metric
   * @param value
   */
  private void saveFileMeasure(org.sonar.api.resources.File testFile, SensorContext context, Metric metric, double value) {
    if ( !Double.isNaN(value)) {
      context.saveMeasure(testFile, metric, value);
    }
  }

}