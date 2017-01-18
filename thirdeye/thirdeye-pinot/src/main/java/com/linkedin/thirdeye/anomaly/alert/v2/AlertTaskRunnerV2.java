package com.linkedin.thirdeye.anomaly.alert.v2;

import com.linkedin.thirdeye.anomaly.ThirdEyeAnomalyConfiguration;
import com.linkedin.thirdeye.anomaly.alert.AlertTaskInfo;
import com.linkedin.thirdeye.anomaly.alert.AlertTaskRunner;
import com.linkedin.thirdeye.anomaly.alert.template.pojo.MetricDimensionReport;
import com.linkedin.thirdeye.anomaly.alert.util.AnomalyReportGenerator;
import com.linkedin.thirdeye.anomaly.alert.util.DataReportHelper;
import com.linkedin.thirdeye.anomaly.alert.util.EmailHelper;
import com.linkedin.thirdeye.anomaly.task.TaskContext;
import com.linkedin.thirdeye.anomaly.task.TaskInfo;
import com.linkedin.thirdeye.anomaly.task.TaskResult;
import com.linkedin.thirdeye.anomaly.task.TaskRunner;
import com.linkedin.thirdeye.client.DAORegistry;
import com.linkedin.thirdeye.dashboard.views.contributor.ContributorViewResponse;
import com.linkedin.thirdeye.datalayer.bao.AlertConfigManager;
import com.linkedin.thirdeye.datalayer.bao.MergedAnomalyResultManager;
import com.linkedin.thirdeye.datalayer.bao.MetricConfigManager;
import com.linkedin.thirdeye.datalayer.dto.AlertConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.MetricConfigDTO;
import com.linkedin.thirdeye.datalayer.pojo.AlertConfigBean;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.joda.time.DateTimeZone;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertTaskRunnerV2 implements TaskRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AlertTaskRunner.class);
  public static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getTimeZone("America/Los_Angeles");
  public static final String CHARSET = "UTF-8";

  private final MergedAnomalyResultManager anomalyMergedResultDAO;
  private final AlertConfigManager alertConfigDAO;
  private final MetricConfigManager metricConfigManager;

  private AlertConfigDTO alertConfig;
  private ThirdEyeAnomalyConfiguration thirdeyeConfig;

  public AlertTaskRunnerV2() {
    anomalyMergedResultDAO = DAORegistry.getInstance().getMergedAnomalyResultDAO();
    alertConfigDAO = DAORegistry.getInstance().getAlertConfigDAO();
    metricConfigManager = DAORegistry.getInstance().getMetricConfigDAO();
  }

  @Override
  public List<TaskResult> execute(TaskInfo taskInfo, TaskContext taskContext) throws Exception {
    List<TaskResult> taskResult = new ArrayList<>();
    AlertTaskInfo alertTaskInfo = (AlertTaskInfo) taskInfo;
    alertConfig = alertTaskInfo.getAlertConfigDTO();
    thirdeyeConfig = taskContext.getThirdEyeAnomalyConfiguration();

    try {
      LOG.info("Begin executing task {}", taskInfo);
      runTask();
    } catch (Exception t) {
      LOG.error("Task failed with exception:", t);
      sendFailureEmail(t);
      // Let task driver mark this task failed
      throw t;
    }
    return taskResult;
  }

  // TODO : separate code path for new vs old alert config !
  private void runTask() throws Exception {
    LOG.info("Starting email report {}", alertConfig.getId());
    AlertConfigBean.EmailConfig emailConfig = alertConfig.getEmailConfig();
    if (emailConfig != null && emailConfig.getFunctionIds() != null) {
      List<Long> functionIds = alertConfig.getEmailConfig().getFunctionIds();
      List<MergedAnomalyResultDTO> mergedAnomaliesAllResults = new ArrayList<>();
      long lastNotifiedAnomaly = emailConfig.getLastNotifiedAnomalyId();
      for (Long functionId : functionIds) {
        // TODO : FIXME
        mergedAnomaliesAllResults.addAll(anomalyMergedResultDAO.findByFunctionIdAndIdGreaterThan(functionId, lastNotifiedAnomaly));
      }
      // apply filtration rule
      List<MergedAnomalyResultDTO> results = EmailHelper.applyFiltrationRule(mergedAnomaliesAllResults);

      if (results.isEmpty() && !alertConfig.getEmailConfig().isSendAlertOnZeroAnomaly()) {
        LOG.info("Zero anomalies found, skipping sending email");
        return;
      }
      AnomalyReportGenerator.getInstance()
          .buildReport(results, thirdeyeConfig, alertConfig.getRecipients(),
              alertConfig.getFromAddress());

      updateNotifiedStatus(results);

      // update anomaly watermark in alertConfig
      long lastNotifiedAlertId = emailConfig.getLastNotifiedAnomalyId();
      for (MergedAnomalyResultDTO anomalyResult : results) {
        if (anomalyResult.getId() > lastNotifiedAlertId) {
          lastNotifiedAlertId = anomalyResult.getId();
        }
      }
      if (lastNotifiedAlertId != emailConfig.getLastNotifiedAnomalyId()) {
        alertConfig.getEmailConfig().setLastNotifiedAnomalyId(lastNotifiedAlertId);
        alertConfigDAO.update(alertConfig);
      }
    }

    AlertConfigBean.ReportConfig reportConfig = alertConfig.getReportConfig();
    if (reportConfig != null && reportConfig.isEnabled()) {
      if (reportConfig.getMetricIds()!= null) {
        if (reportConfig.getMetricIds().size()  != reportConfig.getMetricDimensions().size()) {
          LOG.error("Metric List vs DimensionList size mismatch, please update the config");
        } else {

          long reportStartTs = 0;
          List<MetricDimensionReport> metricDimensionValueReports;
          List<ContributorViewResponse> reports = new ArrayList<>();
          for (int i = 0; i < reportConfig.getMetricIds().size(); i++) {
            MetricConfigDTO metricConfig =
                metricConfigManager.findById(reportConfig.getMetricIds().get(i));
            List<String> dimensions = reportConfig.getMetricDimensions().get(i);
            if (dimensions != null && dimensions.size() > 0) {
              for (String dimension : dimensions) {
                ContributorViewResponse report = EmailHelper
                    .getContributorData(metricConfig.getDataset(), metricConfig.getName(),
                        Arrays.asList(dimension));
                if (report != null) {
                  reports.add(report);
                }
              }
            }
          }
          reportStartTs = reports.get(0).getTimeBuckets().get(0).getCurrentStart();
          metricDimensionValueReports = DataReportHelper.getDimensionReportList(reports);
          Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_21);
          freemarkerConfig.setClassForTemplateLoading(getClass(), "/com/linkedin/thirdeye/detector/");
          freemarkerConfig.setDefaultEncoding(CHARSET);
          freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
          Map<String, Object> templateData = new HashMap<>();
          templateData.put("dashboardHost", thirdeyeConfig.getDashboardHost());
          templateData.put("fromEmail", alertConfig.getFromAddress());
          templateData.put("reportStartDateTime", reportStartTs);
          templateData.put("metricDimensionValueReports", metricDimensionValueReports);
          DateTimeZone timeZone = DateTimeZone.forTimeZone(DEFAULT_TIME_ZONE);
          DataReportHelper.DateFormatMethod dateFormatMethod = new DataReportHelper.DateFormatMethod(timeZone);
          templateData.put("timeZone", timeZone);
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          try (Writer out = new OutputStreamWriter(baos, CHARSET)) {
            Template template = freemarkerConfig.getTemplate("data-report-by-metric-dimension.ftl");
            template.process(templateData, out);
          } catch (Exception e) {
            throw new JobExecutionException(e);
          }
        }
      }
    }
  }

  private void updateNotifiedStatus(List<MergedAnomalyResultDTO> mergedResults) {
    for (MergedAnomalyResultDTO mergedResult : mergedResults) {
      mergedResult.setNotified(true);
      anomalyMergedResultDAO.update(mergedResult);
    }
  }

  private void sendFailureEmail(Throwable t) throws JobExecutionException {
    HtmlEmail email = new HtmlEmail();
    String subject = String
        .format("[ThirdEye Anomaly Detector] FAILED ALERT ID=%d for config %s", alertConfig.getId(),
            alertConfig.getName());
    String textBody = String
        .format("%s%n%nException:%s", alertConfig.toString(), ExceptionUtils.getStackTrace(t));
    try {
      EmailHelper
          .sendEmailWithTextBody(email, thirdeyeConfig.getSmtpConfiguration(), subject, textBody,
              thirdeyeConfig.getFailureFromAddress(), thirdeyeConfig.getFailureToAddress());
    } catch (EmailException e) {
      throw new JobExecutionException(e);
    }
  }
}