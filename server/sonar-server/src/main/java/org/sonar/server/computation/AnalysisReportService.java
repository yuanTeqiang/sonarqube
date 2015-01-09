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

package org.sonar.server.computation;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ServerComponent;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.issue.internal.FieldDiffs;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.Duration;
import org.sonar.api.utils.KeyValueFormat;
import org.sonar.batch.protocol.GsonHelper;
import org.sonar.batch.protocol.output.issue.ReportIssue;
import org.sonar.batch.protocol.output.resource.ReportComponent;
import org.sonar.batch.protocol.output.resource.ReportComponents;
import org.sonar.core.issue.db.IssueStorage;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AnalysisReportService implements ServerComponent {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisReportService.class);
  private static final int MAX_ISSUES_SIZE = 1000;
  private final ComputeEngineIssueStorageFactory issueStorageFactory;
  private final Gson gson;

  public AnalysisReportService(ComputeEngineIssueStorageFactory issueStorageFactory) {
    this.issueStorageFactory = issueStorageFactory;
    this.gson = GsonHelper.create();
  }

  public void digest(ComputationContext context) {
    loadResources(context);
    saveIssues(context);
  }

  @VisibleForTesting
  void loadResources(ComputationContext context) {
    File file = new File(context.getReportDirectory(), "components.json");

    try (InputStream resourcesStream = new FileInputStream(file)) {
      String json = IOUtils.toString(resourcesStream);
      ReportComponents reportComponents = ReportComponents.fromJson(json);
      context.addResources(reportComponents);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read issues", e);
    }

  }

  @VisibleForTesting
  void saveIssues(ComputationContext context) {
    IssueStorage issueStorage = issueStorageFactory.newComputeEngineIssueStorage(context.getProject());

    File issuesFile = new File(context.getReportDirectory(), "issues.json");
    List<DefaultIssue> issues = new ArrayList<>(MAX_ISSUES_SIZE);

    try (InputStream issuesStream = new FileInputStream(issuesFile);
         JsonReader reader = new JsonReader(new InputStreamReader(issuesStream))) {
      reader.beginArray();
      while (reader.hasNext()) {
        ReportIssue reportIssue = gson.fromJson(reader, ReportIssue.class);
        DefaultIssue defaultIssue = toIssue(context, reportIssue);
        issues.add(defaultIssue);
        if (shouldPersistIssues(issues, reader)) {
          issueStorage.save(issues);
          issues.clear();
        }
      }

      reader.endArray();
      reader.close();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read issues", e);
    }
  }

  private boolean shouldPersistIssues(List<DefaultIssue> issues, JsonReader reader) throws IOException {
    return issues.size() == MAX_ISSUES_SIZE || !reader.hasNext();
  }

  private DefaultIssue toIssue(ComputationContext context, ReportIssue issue) {
    DefaultIssue defaultIssue = new DefaultIssue();
    defaultIssue.setKey(issue.key());
    setComponentId(defaultIssue, context.getComponentByBatchId(issue.componentBatchId()));
    defaultIssue.setRuleKey(RuleKey.of(issue.ruleRepo(), issue.ruleKey()));
    defaultIssue.setSeverity(issue.severity());
    defaultIssue.setManualSeverity(issue.isManualSeverity());
    defaultIssue.setMessage(issue.message());
    defaultIssue.setLine(issue.line());
    defaultIssue.setEffortToFix(issue.effortToFix());
    setDebt(defaultIssue, issue.debt());
    setFieldDiffs(defaultIssue, issue.diffFields(), context.getAnalysisDate());
    defaultIssue.setStatus(issue.status());
    defaultIssue.setResolution(issue.resolution());
    defaultIssue.setReporter(issue.reporter());
    defaultIssue.setAssignee(issue.assignee());
    defaultIssue.setChecksum(issue.checksum());
    defaultIssue.setAttributes(KeyValueFormat.parse(issue.issueAttributes()));
    defaultIssue.setAuthorLogin(issue.authorLogin());
    defaultIssue.setActionPlanKey(issue.actionPlanKey());
    defaultIssue.setCreationDate(issue.creationDate());
    defaultIssue.setUpdateDate(issue.updateDate());
    defaultIssue.setCloseDate(issue.closeDate());
    defaultIssue.setChanged(issue.isChanged());
    defaultIssue.setNew(issue.isNew());
    defaultIssue.setSelectedAt(issue.selectedAt());
    return defaultIssue;
  }

  private DefaultIssue setFieldDiffs(DefaultIssue issue, String diffFields, Date analysisDate) {
    FieldDiffs fieldDiffs = FieldDiffs.parse(diffFields);
    fieldDiffs.setCreationDate(analysisDate);
    issue.setCurrentChange(fieldDiffs);

    return issue;
  }

  private DefaultIssue setComponentId(DefaultIssue issue, ReportComponent component) {
    if (component != null) {
      issue.setComponentId((long) component.id());
    }
    return issue;
  }

  private DefaultIssue setDebt(DefaultIssue issue, Long debt) {
    if (debt != null) {
      issue.setDebt(Duration.create(debt));
    }

    return issue;
  }

  public void deleteDirectory(@Nullable File directory) {
    if (directory == null) {
      return;
    }

    try {
      FileUtils.deleteDirectory(directory);
    } catch (IOException e) {
      LOG.warn(String.format("Failed to delete directory '%s'", directory.getPath()), e);
    }
  }
}
