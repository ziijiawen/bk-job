/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.execute.service.impl;

import com.tencent.bk.job.common.constant.ErrorCode;
import com.tencent.bk.job.common.exception.FailedPreconditionException;
import com.tencent.bk.job.common.exception.NotFoundException;
import com.tencent.bk.job.common.exception.ServiceException;
import com.tencent.bk.job.common.iam.exception.PermissionDeniedException;
import com.tencent.bk.job.common.iam.model.AuthResult;
import com.tencent.bk.job.common.model.BaseSearchCondition;
import com.tencent.bk.job.common.model.PageData;
import com.tencent.bk.job.common.model.dto.AppResourceScope;
import com.tencent.bk.job.common.model.dto.IpDTO;
import com.tencent.bk.job.execute.auth.ExecuteAuthService;
import com.tencent.bk.job.execute.common.constants.RunStatusEnum;
import com.tencent.bk.job.execute.common.constants.StepExecuteTypeEnum;
import com.tencent.bk.job.execute.common.constants.StepRunModeEnum;
import com.tencent.bk.job.execute.common.converter.StepTypeExecuteTypeConverter;
import com.tencent.bk.job.execute.common.util.TaskCostCalculator;
import com.tencent.bk.job.execute.constants.UserOperationEnum;
import com.tencent.bk.job.execute.dao.AgentTaskDAO;
import com.tencent.bk.job.execute.dao.FileSourceTaskLogDAO;
import com.tencent.bk.job.execute.dao.StepInstanceDAO;
import com.tencent.bk.job.execute.dao.TaskInstanceDAO;
import com.tencent.bk.job.execute.engine.consts.IpStatus;
import com.tencent.bk.job.execute.model.AgentTaskDTO;
import com.tencent.bk.job.execute.model.AgentTaskResultGroupDTO;
import com.tencent.bk.job.execute.model.ConfirmStepInstanceDTO;
import com.tencent.bk.job.execute.model.FileSourceTaskLogDTO;
import com.tencent.bk.job.execute.model.GseTaskDTO;
import com.tencent.bk.job.execute.model.OperationLogDTO;
import com.tencent.bk.job.execute.model.StepExecutionDTO;
import com.tencent.bk.job.execute.model.StepExecutionDetailDTO;
import com.tencent.bk.job.execute.model.StepExecutionRecordDTO;
import com.tencent.bk.job.execute.model.StepExecutionResultQuery;
import com.tencent.bk.job.execute.model.StepInstanceBaseDTO;
import com.tencent.bk.job.execute.model.StepInstanceRollingTaskDTO;
import com.tencent.bk.job.execute.model.TaskExecuteResultDTO;
import com.tencent.bk.job.execute.model.TaskExecutionDTO;
import com.tencent.bk.job.execute.model.TaskInstanceDTO;
import com.tencent.bk.job.execute.model.TaskInstanceQuery;
import com.tencent.bk.job.execute.model.TaskInstanceRollingConfigDTO;
import com.tencent.bk.job.execute.model.inner.CronTaskExecuteResult;
import com.tencent.bk.job.execute.model.inner.ServiceCronTaskExecuteResultStatistics;
import com.tencent.bk.job.execute.service.AgentTaskService;
import com.tencent.bk.job.execute.service.GseTaskService;
import com.tencent.bk.job.execute.service.HostService;
import com.tencent.bk.job.execute.service.LogService;
import com.tencent.bk.job.execute.service.RollingConfigService;
import com.tencent.bk.job.execute.service.StepInstanceRollingTaskService;
import com.tencent.bk.job.execute.service.TaskOperationLogService;
import com.tencent.bk.job.execute.service.TaskResultService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tencent.bk.job.common.constant.Order.DESCENDING;

/**
 * 作业执行结果查询Service
 */
@Service
@Slf4j
public class TaskResultServiceImpl implements TaskResultService {
    private final TaskInstanceDAO taskInstanceDAO;
    private final StepInstanceDAO stepInstanceDAO;
    private final GseTaskService gseTaskService;
    private final FileSourceTaskLogDAO fileSourceTaskLogDAO;
    private final AgentTaskDAO agentTaskDAO;
    private final HostService hostService;
    private final LogService logService;
    private final ExecuteAuthService executeAuthService;
    private final TaskOperationLogService operationLogService;
    private final AgentTaskService agentTaskService;
    private final RollingConfigService rollingConfigService;
    private final StepInstanceRollingTaskService stepInstanceRollingTaskService;

    @Autowired
    public TaskResultServiceImpl(TaskInstanceDAO taskInstanceDAO,
                                 StepInstanceDAO stepInstanceDAO,
                                 GseTaskService gseTaskService,
                                 FileSourceTaskLogDAO fileSourceTaskLogDAO,
                                 AgentTaskDAO agentTaskDAO,
                                 HostService hostService,
                                 LogService logService,
                                 ExecuteAuthService executeAuthService,
                                 TaskOperationLogService operationLogService,
                                 AgentTaskService agentTaskService,
                                 RollingConfigService rollingConfigService,
                                 StepInstanceRollingTaskService stepInstanceRollingTaskService) {
        this.taskInstanceDAO = taskInstanceDAO;
        this.stepInstanceDAO = stepInstanceDAO;
        this.gseTaskService = gseTaskService;
        this.fileSourceTaskLogDAO = fileSourceTaskLogDAO;
        this.agentTaskDAO = agentTaskDAO;
        this.hostService = hostService;
        this.logService = logService;
        this.executeAuthService = executeAuthService;
        this.operationLogService = operationLogService;
        this.agentTaskService = agentTaskService;
        this.rollingConfigService = rollingConfigService;
        this.stepInstanceRollingTaskService = stepInstanceRollingTaskService;
    }

    @Override
    public PageData<TaskInstanceDTO> listPageTaskInstance(TaskInstanceQuery taskQuery,
                                                          BaseSearchCondition baseSearchCondition) {
        PageData<TaskInstanceDTO> pageData = taskInstanceDAO.listPageTaskInstance(taskQuery, baseSearchCondition);
        if (pageData != null && pageData.getData() != null && !pageData.getData().isEmpty()) {
            pageData.getData().forEach(taskInstanceDTO -> {
                if (taskInstanceDTO.getTotalTime() == null) {
                    if (taskInstanceDTO.getStatus().equals(RunStatusEnum.RUNNING.getValue())
                        || taskInstanceDTO.getStatus().equals(RunStatusEnum.WAITING_USER.getValue())
                        || taskInstanceDTO.getStatus().equals(RunStatusEnum.STOPPING.getValue())) {
                        taskInstanceDTO.setTotalTime((TaskCostCalculator.calculate(taskInstanceDTO.getStartTime(),
                            taskInstanceDTO.getEndTime(), taskInstanceDTO.getTotalTime())));
                    }
                }
            });
        }
        return pageData;
    }

    @Override
    public TaskExecuteResultDTO getTaskExecutionResult(String username, Long appId,
                                                       Long taskInstanceId) throws ServiceException {
        TaskInstanceDTO taskInstance = taskInstanceDAO.getTaskInstance(taskInstanceId);
        if (taskInstance == null) {
            log.warn("Task instance is not exist, taskInstanceId={}", taskInstanceId);
            throw new NotFoundException(ErrorCode.TASK_INSTANCE_NOT_EXIST);
        }
        if (!taskInstance.getAppId().equals(appId)) {
            log.warn("Task instance is not in application, taskInstanceId={}, appId={}", taskInstanceId, appId);
            throw new NotFoundException(ErrorCode.TASK_INSTANCE_NOT_EXIST);
        }

        authViewTaskInstance(username, appId, taskInstance);

        TaskExecutionDTO taskExecution = buildTaskExecutionDTO(taskInstance);

        List<StepInstanceBaseDTO> stepInstanceList =
            stepInstanceDAO.listStepInstanceBaseByTaskInstanceId(taskInstanceId);
        List<StepExecutionDTO> stepExecutionList = new ArrayList<>();
        for (StepInstanceBaseDTO stepInstance : stepInstanceList) {
            stepExecutionList.add(buildStepExecutionDTO(stepInstance));
        }

        TaskExecuteResultDTO taskExecuteResult = new TaskExecuteResultDTO();
        taskExecuteResult.setFinished(RunStatusEnum.isFinishedStatus(RunStatusEnum.valueOf(taskInstance.getStatus())));
        taskExecuteResult.setTaskInstanceExecutionResult(taskExecution);
        taskExecuteResult.setStepInstanceExecutionResults(stepExecutionList);

        return taskExecuteResult;
    }

    private TaskExecutionDTO buildTaskExecutionDTO(TaskInstanceDTO taskInstance) {
        TaskExecutionDTO taskExecution = new TaskExecutionDTO();
        taskExecution.setTaskInstanceId(taskInstance.getId());
        taskExecution.setName(taskInstance.getName());
        taskExecution.setType(taskInstance.getType());
        taskExecution.setStatus(taskInstance.getStatus());
        taskExecution.setTotalTime(TaskCostCalculator.calculate(taskInstance.getStartTime(),
            taskInstance.getEndTime(), taskInstance.getTotalTime()));
        taskExecution.setStartTime(taskInstance.getStartTime());
        taskExecution.setEndTime(taskInstance.getEndTime());
        taskExecution.setTaskId(taskInstance.getTaskId());
        taskExecution.setTaskTemplateId(taskInstance.getTaskTemplateId());
        taskExecution.setDebugTask(taskInstance.isDebugTask());
        taskExecution.setCurrentStepInstanceId(taskInstance.getCurrentStepInstanceId());
        return taskExecution;
    }

    private StepExecutionDTO buildStepExecutionDTO(StepInstanceBaseDTO stepInstance) {
        StepExecutionDTO stepExecution = new StepExecutionDTO();
        stepExecution.setStepInstanceId(stepInstance.getId());
        stepExecution.setName(stepInstance.getName());
        stepExecution.setExecuteCount(stepInstance.getExecuteCount());
        stepExecution.setStatus(stepInstance.getStatus());
        stepExecution.setType(StepTypeExecuteTypeConverter.convertToStepType(stepInstance.getExecuteType()));
        stepExecution.setStartTime(stepInstance.getStartTime());
        stepExecution.setEndTime(stepInstance.getEndTime());
        stepExecution.setOperator(stepInstance.getOperator());
        stepExecution.setTotalTime(TaskCostCalculator.calculate(stepInstance.getStartTime(),
            stepInstance.getEndTime(), stepInstance.getTotalTime()));
        stepExecution.setLastStep(stepInstance.isLastStep());
        if (stepInstance.getExecuteType().equals(StepExecuteTypeEnum.MANUAL_CONFIRM.getValue())) {
            ConfirmStepInstanceDTO confirmStepInstance = stepInstanceDAO.getConfirmStepInstance(stepInstance.getId());
            if (confirmStepInstance != null) {
                stepExecution.setConfirmMessage(confirmStepInstance.getConfirmMessage());
                stepExecution.setConfirmReason(confirmStepInstance.getConfirmReason());
                stepExecution.setConfirmNotifyChannels(confirmStepInstance.getNotifyChannels());
                stepExecution.setConfirmUsers(confirmStepInstance.getConfirmUsers());
                stepExecution.setConfirmRoles(confirmStepInstance.getConfirmRoles());
            }
        }
        return stepExecution;
    }

    private void authViewTaskInstance(String username, Long appId, TaskInstanceDTO taskInstance) {

        AuthResult authResult = executeAuthService.authViewTaskInstance(
            username, new AppResourceScope(appId), taskInstance);
        if (!authResult.isPass()) {
            throw new PermissionDeniedException(authResult);
        }
    }

    private void authViewStepInstance(String username, Long appId, StepInstanceBaseDTO stepInstance) {
        String operator = stepInstance.getOperator();
        if (username.equals(operator)) {
            return;
        }

        AuthResult authResult = executeAuthService.authViewTaskInstance(
            username, new AppResourceScope(appId),
            stepInstance.getTaskInstanceId());
        if (!authResult.isPass()) {
            throw new PermissionDeniedException(authResult);
        }
    }

    /**
     * 加入文件源文件拉取所使用的时间
     *
     * @param stepExecutionDetail 步骤执行详情
     * @param fileSourceTaskLog   第三方文件源任务
     */
    private void involveFileSourceTaskLog(StepExecutionDetailDTO stepExecutionDetail,
                                          FileSourceTaskLogDTO fileSourceTaskLog) {
        List<AgentTaskResultGroupDTO> resultGroups = stepExecutionDetail.getResultGroups();
        for (AgentTaskResultGroupDTO resultGroup : resultGroups) {
            if (resultGroup == null) {
                continue;
            }
            List<AgentTaskDTO> agentTaskExecutionDetailList = resultGroup.getAgentTasks();
            if (agentTaskExecutionDetailList == null) {
                continue;
            }
            for (AgentTaskDTO agentTaskDetail : agentTaskExecutionDetailList) {
                if (agentTaskDetail == null) {
                    continue;
                }
                agentTaskDetail.setStartTime(fileSourceTaskLog.getStartTime());
                if (agentTaskDetail.getEndTime() == null || agentTaskDetail.getEndTime() == 0) {
                    agentTaskDetail.setEndTime(stepExecutionDetail.getEndTime());
                }
                agentTaskDetail.calculateTotalTime();
            }
        }
    }

    private StepInstanceBaseDTO checkGetStepExecutionDetail(String username, long appId, long stepInstanceId) {
        StepInstanceBaseDTO stepInstance = stepInstanceDAO.getStepInstanceBase(stepInstanceId);
        if (stepInstance == null) {
            log.warn("Step instance is not exist, stepInstanceId={}", stepInstanceId);
            throw new NotFoundException(ErrorCode.STEP_INSTANCE_NOT_EXIST);
        }

        authViewStepInstance(username, appId, stepInstance);

        if (stepInstance.getExecuteType().equals(StepExecuteTypeEnum.MANUAL_CONFIRM.getValue())) {
            log.warn("Manual confirm step does not support get-step-detail operation");
            throw new FailedPreconditionException(ErrorCode.UNSUPPORTED_OPERATION);
        }
        return stepInstance;
    }

    private boolean isMatchResultGroup(AgentTaskResultGroupDTO resultGroup, Integer resultType, String tag) {
        String matchTag = tag == null ? "" : tag;
        return resultType.equals(resultGroup.getStatus()) && matchTag.equals(resultGroup.getTag());
    }

    private <T> List<T> getLimitedSizedList(List<T> list, Integer maxSize) {
        if (maxSize == null) {
            return list;
        }
        int size = list.size();
        if (size <= maxSize) {
            return list;
        } else {
            return list.subList(0, maxSize);
        }
    }

    @Override
    public StepExecutionDetailDTO getFastTaskStepExecutionResult(String username, Long appId, Long taskInstanceId,
                                                                 StepExecutionResultQuery query) {
        List<StepInstanceBaseDTO> stepInstanceList =
            stepInstanceDAO.listStepInstanceBaseByTaskInstanceId(taskInstanceId);
        StepInstanceBaseDTO stepInstance = stepInstanceList.get(0);
        if (stepInstance == null) {
            log.warn("Step instance is not exist, taskInstanceId={}", taskInstanceId);
            throw new NotFoundException(ErrorCode.STEP_INSTANCE_NOT_EXIST);
        }
        if (!stepInstance.getAppId().equals(appId)) {
            log.warn("Step instance is not in application, stepInstanceId={}, appId={}", stepInstance.getId(), appId);
            throw new NotFoundException(ErrorCode.STEP_INSTANCE_NOT_EXIST);
        }

        return getStepExecutionResult(username, appId, query);
    }

    private void setAgentTasksForSpecifiedResultType(List<AgentTaskResultGroupDTO> resultGroups,
                                                     Integer status,
                                                     String tag,
                                                     List<AgentTaskDTO> agentTasksForResultType) {
        for (AgentTaskResultGroupDTO resultGroup : resultGroups) {
            if (status.equals(resultGroup.getStatus()) && (
                (StringUtils.isEmpty(tag) ? StringUtils.isEmpty(resultGroup.getTag()) :
                    tag.equals(resultGroup.getTag())))) {
                resultGroup.setAgentTasks(agentTasksForResultType);
                resultGroup.setTotalAgentTasks(agentTasksForResultType.size());
            }
        }
    }

    private void setAgentTasksForAnyResultType(List<AgentTaskResultGroupDTO> resultGroups,
                                               long stepInstanceId,
                                               int executeCount,
                                               Integer batch,
                                               Integer maxAgentTasksForResultGroup) {
        boolean isAgentTaskSet = false;
        for (AgentTaskResultGroupDTO resultGroup : resultGroups) {
            if (!isAgentTaskSet) {
                isAgentTaskSet = fillAgentTasksForResultGroup(resultGroup, stepInstanceId, executeCount,
                    batch, resultGroup.getStatus(), resultGroup.getTag(), maxAgentTasksForResultGroup);
            } else {
                return;
            }
        }
    }

    private boolean fillAgentTasksForResultGroup(AgentTaskResultGroupDTO resultGroup,
                                                 long stepInstanceId,
                                                 int executeCount,
                                                 Integer batch,
                                                 Integer status,
                                                 String tag,
                                                 Integer maxAgentTasksForResultGroup) {
        List<AgentTaskDTO> agentTasks = agentTaskDAO.listAgentTaskByResultGroup(stepInstanceId,
            executeCount, batch, status, tag, maxAgentTasksForResultGroup, null, null);
        if (CollectionUtils.isEmpty(agentTasks)) {
            return false;
        }
        resultGroup.setAgentTasks(agentTasks);
        resultGroup.setTotalAgentTasks(agentTasks.size());
        return true;
    }

    /**
     * 步骤未启动，AgentTask数据还未在DB初始化，构造初始任务结果
     *
     * @param appId                       业务ID
     * @param stepInstance                步骤实例
     * @param queryExecuteCount           执行次数
     * @param batch                       滚动批次
     * @param maxAgentTasksForResultGroup 任务分组下最大返回的AgentTask数量
     * @param fuzzySearchIp               模糊查询IP
     * @return 步骤执行结果
     */
    private StepExecutionDetailDTO buildNotStartStepExecutionResult(long appId,
                                                                    StepInstanceBaseDTO stepInstance,
                                                                    Integer queryExecuteCount,
                                                                    Integer batch,
                                                                    Integer maxAgentTasksForResultGroup,
                                                                    String fuzzySearchIp) {

        StepExecutionDetailDTO stepExecuteDetail = new StepExecutionDetailDTO(stepInstance);
        stepExecuteDetail.setExecuteCount(queryExecuteCount);

        List<AgentTaskResultGroupDTO> resultGroups = new ArrayList<>();
        AgentTaskResultGroupDTO resultGroup = new AgentTaskResultGroupDTO();
        resultGroup.setStatus(IpStatus.WAITING.getValue());
        resultGroup.setTag(null);

        List<IpDTO> targetServers = filterTargetServersByBatch(stepInstance, batch);

        List<AgentTaskDTO> agentTasks = new ArrayList<>();
        // 如果需要根据IP过滤，那么需要重新计算Agent任务总数
        boolean fuzzyFilterByIp = StringUtils.isNotEmpty(fuzzySearchIp);
        int agentTaskSize = targetServers.size();
        if (fuzzyFilterByIp) {
            agentTaskSize = (int) targetServers.stream()
                .filter(ipDTO -> ipDTO.getIp().contains(fuzzySearchIp)).count();
        }
        resultGroup.setTotalAgentTasks(agentTaskSize);

        if (CollectionUtils.isNotEmpty(targetServers)) {
            int maxAgentTasks = (maxAgentTasksForResultGroup != null ?
                Math.min(maxAgentTasksForResultGroup, targetServers.size()) : targetServers.size());
            for (IpDTO targetServer : targetServers) {
                String ip = targetServer.getIp();
                if (fuzzyFilterByIp && !ip.contains(fuzzySearchIp)) {
                    // 如果需要根据IP过滤，那么过滤掉不匹配的任务
                    continue;
                }
                if (maxAgentTasks-- > 0) {
                    AgentTaskDTO agentTask = new AgentTaskDTO();
                    agentTask.setCloudIp(targetServer.getCloudAreaId() + ":" + targetServer.getIp());
                    Long cloudAreaId = targetServer.getCloudAreaId();
                    agentTask.setCloudId(cloudAreaId);
                    agentTask.setCloudName(hostService.getCloudAreaName(cloudAreaId));
                    agentTask.setDisplayIp(targetServer.getIp());
                    agentTask.setStatus(IpStatus.WAITING.getValue());
                    agentTask.setTag(null);
                    agentTask.setErrorCode(0);
                    agentTask.setExitCode(0);
                    agentTask.setTotalTime(0L);
                    agentTasks.add(agentTask);
                }
            }
        }
        resultGroup.setAgentTasks(agentTasks);
        resultGroups.add(resultGroup);
        stepExecuteDetail.setResultGroups(resultGroups);
        return stepExecuteDetail;
    }

    private List<IpDTO> filterTargetServersByBatch(StepInstanceBaseDTO stepInstance, Integer batch) {
        List<IpDTO> targetServers;
        if (stepInstance.isRollingStep()) {
            targetServers = rollingConfigService.getRollingServers(stepInstance, batch);
        } else {
            targetServers = stepInstance.getTargetServers().getIpList();
        }

        return targetServers;
    }

    @Override
    public StepExecutionDetailDTO getStepExecutionResult(String username, Long appId,
                                                         StepExecutionResultQuery query) throws ServiceException {
        StopWatch watch = new StopWatch("getStepExecutionResult");
        try {
            Long stepInstanceId = query.getStepInstanceId();

            watch.start("checkGetStepExecutionDetail");
            StepInstanceBaseDTO stepInstance = checkGetStepExecutionDetail(username, appId, stepInstanceId);
            int queryExecuteCount = query.getExecuteCount() == null ? stepInstance.getExecuteCount() :
                query.getExecuteCount();
            query.setExecuteCount(queryExecuteCount);
            if (stepInstance.isRollingStep() && query.isFilterByLatestBatch()) {
                query.setBatch(stepInstance.getBatch());
            }
            watch.stop();

            if (stepInstance.getStatus().equals(RunStatusEnum.BLANK.getValue())) {
                // 步骤未启动，AgentTask数据还未在DB初始化，构造初始任务结果
                return buildNotStartStepExecutionResult(appId, stepInstance, queryExecuteCount, query.getBatch(),
                    query.getMaxAgentTasksForResultGroup(), query.getSearchIp());
            }

            StepExecutionDetailDTO stepExecutionDetail;
            // 如果步骤的目标服务器数量<100,或者通过IP匹配的方式过滤agent任务，为了提升性能，直接全量从DB查询数据，在内存进行处理
            if ((stepInstance.getTargetServerTotalCount() <= 100) || query.hasIpCondition()) {
                stepExecutionDetail = loadAllTasksFromDBAndBuildExecutionResultInMemory(watch, stepInstance, query);
            } else {
                stepExecutionDetail = filterAndSortExecutionResultInDB(watch, stepInstance, query);
            }

            if (stepInstance.isRollingStep()) {
                watch.start("setRollingTasksForStep");
                setRollingInfoForStep(stepInstance, stepExecutionDetail);
                watch.stop();
            } else {
                stepExecutionDetail.setRunMode(StepRunModeEnum.RUN_ALL);
            }


            if (stepInstance.isFileStep()) {
                watch.start("involveFileSourceTaskLog");
                FileSourceTaskLogDTO fileSourceTaskLog = fileSourceTaskLogDAO.getFileSourceTaskLog(stepInstance.getId(),
                    queryExecuteCount);
                if (fileSourceTaskLog != null) {
                    involveFileSourceTaskLog(stepExecutionDetail, fileSourceTaskLog);
                }
                watch.stop();
            }

            return stepExecutionDetail;
        } finally {
            if (watch.isRunning()) {
                watch.stop();
            }
            if (watch.getTotalTimeMillis() > 1000L) {
                log.info("Get step execution detail is slow, watch: {}", watch.prettyPrint());
            }
        }

    }

    private StepExecutionDetailDTO loadAllTasksFromDBAndBuildExecutionResultInMemory(StopWatch watch,
                                                                                     StepInstanceBaseDTO stepInstance,
                                                                                     StepExecutionResultQuery query) {
        try {
            if (query.hasIpCondition()) {
                watch.start("getMatchIps");
                Set<String> matchIps = getMatchIps(query);
                if (CollectionUtils.isEmpty(matchIps)) {
                    watch.stop();
                    return buildExecutionDetailWhenTaskAreEmpty(stepInstance, query.getBatch());
                } else {
                    query.setMatchIps(matchIps);
                }
                watch.stop();
            }

            watch.start("loadAllTasksFromDbAndGroup");
            List<AgentTaskResultGroupDTO> resultGroups =
                agentTaskService.listAndGroupAgentTasks(query.getStepInstanceId(), query.getExecuteCount(),
                    query.getBatch());
            resultGroups.forEach(
                resultGroup -> resultGroup.getAgentTasks().forEach(
                    agentTask -> agentTask.setCloudName(hostService.getCloudAreaName(agentTask.getCloudId()))));

            if (CollectionUtils.isNotEmpty(query.getMatchIps())) {
                filterAgentTasksByMatchIp(resultGroups, query.getMatchIps());
            }

            removeAgentTasksForNotSpecifiedResultGroup(resultGroups, query.getStatus(), query.getTag());
            watch.stop();


            watch.start("sortAndLimitTasks");
            sortAgentTasksAndLimitSize(resultGroups, query);
            watch.stop();

            StepExecutionDetailDTO executeDetail = new StepExecutionDetailDTO(stepInstance);
            executeDetail.setExecuteCount(query.getExecuteCount());
            executeDetail.setResultGroups(resultGroups);

            return executeDetail;
        } finally {
            if (watch.isRunning()) {
                watch.stop();
            }
        }

    }

    private void sortAgentTasksAndLimitSize(List<AgentTaskResultGroupDTO> resultGroups,
                                            StepExecutionResultQuery query) {
        resultGroups.stream()
            .filter(resultGroup -> CollectionUtils.isNotEmpty(resultGroup.getAgentTasks()))
            .forEach(resultGroup -> {
                // 排序
                if (StringUtils.isNotEmpty(query.getOrderField())) {
                    List<AgentTaskDTO> taskList = resultGroup.getAgentTasks();
                    if (StepExecutionResultQuery.ORDER_FIELD_TOTAL_TIME.equals(query.getOrderField())) {
                        taskList.sort(Comparator.comparingLong(task -> task.getTotalTime() == null ? 0L :
                            task.getTotalTime()));
                        if (query.getOrder() == DESCENDING) {
                            Collections.reverse(taskList);
                        }
                    } else if (StepExecutionResultQuery.ORDER_FIELD_EXIT_CODE.equals(query.getOrderField())) {
                        taskList.sort((o1, o2) -> {
                            if (o1.getExitCode() != null && o2.getExitCode() != null) {
                                if (o1.getExitCode().equals(o2.getExitCode())) {
                                    return 0;
                                } else {
                                    return o1.getExitCode() > o2.getExitCode() ? 1 : -1;
                                }
                            } else if (o1.getExitCode() == null) {
                                return -1;
                            } else if (o2.getExitCode() == null) {
                                return 1;
                            } else {
                                return 0;
                            }
                        });
                        if (query.getOrder() == DESCENDING) {
                            Collections.reverse(taskList);
                        }
                    } else if (StepExecutionResultQuery.ORDER_FIELD_CLOUD_AREA_ID.equals(query.getOrderField())) {
                        taskList.sort(Comparator.comparing(AgentTaskDTO::getCloudIp));
                        if (query.getOrder() == DESCENDING) {
                            Collections.reverse(taskList);
                        }
                    }
                }

                // 截断
                if (query.getMaxAgentTasksForResultGroup() != null) {
                    resultGroup.setAgentTasks(
                        getLimitedSizedList(resultGroup.getAgentTasks(), query.getMaxAgentTasksForResultGroup()));
                }
            });
    }


    private Set<String> getMatchIps(StepExecutionResultQuery query) {
        long stepInstanceId = query.getStepInstanceId();
        int executeCount = query.getExecuteCount();

        Set<String> matchIpsByLogKeywordSearch = null;
        if (StringUtils.isNotBlank(query.getLogKeyword())) {
            List<IpDTO> matchHosts = getHostsByLogContentKeyword(stepInstanceId, executeCount, query.getLogKeyword());
            if (CollectionUtils.isNotEmpty(matchHosts)) {
                matchIpsByLogKeywordSearch = matchHosts.stream().map(IpDTO::convertToStrIp).collect(Collectors.toSet());
            }
        }
        Set<String> matchIpsByIpSearch = null;
        if (StringUtils.isNotBlank(query.getSearchIp())) {
            List<IpDTO> matchHosts = fuzzySearchHostsByIp(stepInstanceId, executeCount, query.getSearchIp());
            if (CollectionUtils.isNotEmpty(matchHosts)) {
                matchIpsByIpSearch = matchHosts.stream().map(IpDTO::convertToStrIp).collect(Collectors.toSet());
            }
        }

        if (matchIpsByLogKeywordSearch != null && matchIpsByIpSearch != null) {
            return new HashSet<>(CollectionUtils.intersection(matchIpsByLogKeywordSearch, matchIpsByIpSearch));
        } else if (matchIpsByLogKeywordSearch != null) {
            return matchIpsByLogKeywordSearch;
        } else if (matchIpsByIpSearch != null) {
            return matchIpsByIpSearch;
        } else {
            return Collections.emptySet();
        }
    }

    private StepExecutionDetailDTO buildExecutionDetailWhenTaskAreEmpty(StepInstanceBaseDTO stepInstance,
                                                                        Integer batch) {
        List<AgentTaskResultGroupDTO> resultGroups = agentTaskDAO
            .listResultGroups(stepInstance.getId(), stepInstance.getExecuteCount(), batch);
        StepExecutionDetailDTO executeDetail = new StepExecutionDetailDTO(stepInstance);
        executeDetail.setResultGroups(resultGroups);
        return executeDetail;
    }

    private void filterAgentTasksByMatchIp(List<AgentTaskResultGroupDTO> resultGroups, Set<String> matchIps) {
        for (AgentTaskResultGroupDTO resultGroup : resultGroups) {
            List<AgentTaskDTO> agentTasks = resultGroup.getAgentTasks();
            if (CollectionUtils.isNotEmpty(agentTasks)) {
                agentTasks = agentTasks.stream().filter(
                    agentTask -> matchIps.contains(agentTask.getCloudIp())).collect(
                    Collectors.toList());
                resultGroup.setAgentTasks(agentTasks);
            }
        }
    }

    private void removeAgentTasksForNotSpecifiedResultGroup(List<AgentTaskResultGroupDTO> resultGroups, Integer status,
                                                            String tag) {
        if (status != null && resultGroups.contains(new AgentTaskResultGroupDTO(status, tag))) {
            resultGroups.forEach(resultGroup -> {
                if (!isMatchResultGroup(resultGroup, status, tag)) {
                    resultGroup.setAgentTasks(Collections.emptyList());
                }
            });
        } else {
            int i = 0;
            for (AgentTaskResultGroupDTO resultGroup : resultGroups) {
                i++;
                if (i != 1) {
                    resultGroup.setAgentTasks(Collections.emptyList());
                }
            }
        }
    }

    private StepExecutionDetailDTO filterAndSortExecutionResultInDB(StopWatch watch,
                                                                    StepInstanceBaseDTO stepInstance,
                                                                    StepExecutionResultQuery query) {
        query.transformOrderFieldToDbField();
        long stepInstanceId = query.getStepInstanceId();
        int queryExecuteCount = query.getExecuteCount();
        Integer status = query.getStatus();
        String tag = query.getTag();

        StepExecutionDetailDTO executeDetail = new StepExecutionDetailDTO(stepInstance);
        executeDetail.setExecuteCount(queryExecuteCount);

        watch.start("getBaseResultGroups");
        List<AgentTaskResultGroupDTO> resultGroups = agentTaskDAO.listResultGroups(stepInstanceId, queryExecuteCount,
            query.getBatch());
        watch.stop();

        watch.start("setAgentTasks");
        if (status != null) {
            List<AgentTaskDTO> tasks = agentTaskDAO.listAgentTaskByResultGroup(stepInstanceId, queryExecuteCount,
                query.getBatch(), status, tag, query.getMaxAgentTasksForResultGroup(), query.getOrderField(),
                query.getOrder());
            if (CollectionUtils.isNotEmpty(tasks)) {
                setAgentTasksForSpecifiedResultType(resultGroups, status, tag, tasks);
            } else {
                setAgentTasksForAnyResultType(resultGroups, stepInstanceId, queryExecuteCount,
                    query.getBatch(), query.getMaxAgentTasksForResultGroup());
            }
        } else {
            setAgentTasksForAnyResultType(resultGroups, stepInstanceId, queryExecuteCount,
                query.getBatch(), query.getMaxAgentTasksForResultGroup());
        }
        watch.stop();


        executeDetail.setResultGroups(resultGroups);
        return executeDetail;
    }

    private void setRollingInfoForStep(StepInstanceBaseDTO stepInstance,
                                       StepExecutionDetailDTO stepExecutionDetail) {
        TaskInstanceRollingConfigDTO rollingConfig =
            rollingConfigService.getRollingConfig(stepInstance.getRollingConfigId());

        Map<Integer, StepInstanceRollingTaskDTO> latestStepInstanceRollingTasks =
            stepInstanceRollingTaskService.listLatestRollingTasks(
                stepExecutionDetail.getStepInstanceId(),
                stepExecutionDetail.getExecuteCount())
                .stream()
                .collect(Collectors.toMap(StepInstanceRollingTaskDTO::getBatch,
                    stepInstanceRollingTask -> stepInstanceRollingTask));

        // 如果滚动任务还未调度，那么需要在结果中补充
        int totalBatch = rollingConfig.getConfig().getTotalBatch();
        List<StepInstanceRollingTaskDTO> stepInstanceRollingTasks = new ArrayList<>();
        for (int batch = 1; batch <= totalBatch; batch++) {
            StepInstanceRollingTaskDTO stepInstanceRollingTask = latestStepInstanceRollingTasks.get(batch);
            if (stepInstanceRollingTask == null) {
                stepInstanceRollingTask = new StepInstanceRollingTaskDTO();
                stepInstanceRollingTask.setStepInstanceId(stepExecutionDetail.getStepInstanceId());
                stepInstanceRollingTask.setExecuteCount(stepInstance.getExecuteCount());
                stepInstanceRollingTask.setBatch(batch);
                stepInstanceRollingTask.setStatus(RunStatusEnum.BLANK.getValue());
                if (RunStatusEnum.WAITING_USER.getValue().equals(stepInstance.getStatus())
                    && stepInstance.getBatch() + 1 == batch) {
                    // 如果当前步骤状态为"等待用户"，那么需要设置下一批次的滚动任务状态为WAITING_USER
                    stepInstanceRollingTask.setStatus(RunStatusEnum.WAITING_USER.getValue());
                } else {
                    stepInstanceRollingTask.setStatus(RunStatusEnum.BLANK.getValue());
                }
            }
            stepInstanceRollingTasks.add(stepInstanceRollingTask);
        }

        stepExecutionDetail.setRollingTasks(stepInstanceRollingTasks);
        stepExecutionDetail.setLatestBatch(stepInstance.getBatch());
        stepExecutionDetail.setRunMode(rollingConfig.isBatchRollingStep(stepInstance.getId()) ?
            StepRunModeEnum.ROLLING_IN_BATCH : StepRunModeEnum.ROLLING_ALL);
    }

    private List<IpDTO> getHostsByLogContentKeyword(long stepInstanceId, int executeCount, String keyword) {
        String searchKey = keyword.replaceAll("'", "").replaceAll("\\$", "")
            .replaceAll("&", "").replaceAll("\\$", "")
            .replaceAll("\\|", "").replaceAll("`", "")
            .replaceAll(";", "");

        return logService.getIpsByContentKeyword(stepInstanceId, executeCount, searchKey);
    }

    private List<IpDTO> fuzzySearchHostsByIp(long stepInstanceId, int executeCount, String searchIp) {
        return agentTaskDAO.fuzzySearchTargetIpsByIp(stepInstanceId, executeCount, searchIp)
            .stream().map(IpDTO::fromCloudAreaIdAndIpStr).collect(Collectors.toList());
    }

    @Override
    public Map<Long, ServiceCronTaskExecuteResultStatistics> getCronTaskExecuteResultStatistics(long appId,
                                                                                                List<Long> cronTaskIdList) {
        if (cronTaskIdList == null || cronTaskIdList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ServiceCronTaskExecuteResultStatistics> statisticsMap = new HashMap<>();
        StopWatch watch = new StopWatch("cron-task-statistics");
        for (Long cronTaskId : cronTaskIdList) {
            watch.start("get-last24h-tasks-" + cronTaskId);
            List<TaskInstanceDTO> last24HourTaskInstances = taskInstanceDAO.listLatestCronTaskInstance(appId,
                cronTaskId, 86400L, null, null);

            boolean isGe10Within24Hour = false;
            // 已执行完成任务计数
            int doneTaskCount = 0;
            for (TaskInstanceDTO taskInstance : last24HourTaskInstances) {
                if (!taskInstance.getStatus().equals(RunStatusEnum.RUNNING.getValue())
                    && !taskInstance.getStatus().equals(RunStatusEnum.STOPPING.getValue())) {
                    doneTaskCount++;
                }
                if (doneTaskCount >= 10) {
                    isGe10Within24Hour = true;
                    break;
                }
            }
            watch.stop();

            // 24小时内执行超过10次，统计24小时内所有的数据
            if (isGe10Within24Hour) {
                ServiceCronTaskExecuteResultStatistics statistic = new ServiceCronTaskExecuteResultStatistics();
                statistic.setCronTaskId(cronTaskId);
                statistic.setLast24HourExecuteRecords(convertToCronTaskExecuteResult(last24HourTaskInstances));
                statisticsMap.put(cronTaskId, statistic);
            } else {
                watch.start("get-last10-tasks-" + cronTaskId);
                // 如果24小时内执行次数少于10次，那么统计最近10次的数据。由于可能存在正在运行任务，所以默认返回最近11次的数据
                List<TaskInstanceDTO> last10TaskInstances = taskInstanceDAO.listLatestCronTaskInstance(appId,
                    cronTaskId, null, null, 11);
                ServiceCronTaskExecuteResultStatistics statistic = new ServiceCronTaskExecuteResultStatistics();
                statistic.setCronTaskId(cronTaskId);
                statistic.setLast10ExecuteRecords(convertToCronTaskExecuteResult(last10TaskInstances));
                statisticsMap.put(cronTaskId, statistic);
                watch.stop();
            }
        }
        if (watch.getTotalTimeMillis() > 1000) {
            log.warn("Get cron task statistics is slow, cost: {}", watch.prettyPrint());
        }
        return statisticsMap;
    }

    private List<CronTaskExecuteResult> convertToCronTaskExecuteResult(List<TaskInstanceDTO> taskInstances) {
        return taskInstances.stream().map(taskInstance -> {
            CronTaskExecuteResult cronTaskExecuteResult = new CronTaskExecuteResult();
            cronTaskExecuteResult.setCronTaskId(taskInstance.getCronTaskId());
            cronTaskExecuteResult.setPlanId(taskInstance.getTaskId());
            cronTaskExecuteResult.setStatus(taskInstance.getStatus());
            cronTaskExecuteResult.setExecuteTime(taskInstance.getCreateTime());
            return cronTaskExecuteResult;
        }).collect(Collectors.toList());
    }

    @Override
    public List<IpDTO> getHostsByResultType(String username,
                                            Long appId,
                                            Long stepInstanceId,
                                            Integer executeCount,
                                            Integer batch,
                                            Integer resultType,
                                            String tag,
                                            String keyword) {
        StepInstanceBaseDTO stepInstance = checkGetStepExecutionDetail(username, appId, stepInstanceId);

        if (!stepInstance.getAppId().equals(appId)) {
            log.warn("Step instance is not in application, stepInstanceId={}, appId={}", stepInstanceId, appId);
            throw new NotFoundException(ErrorCode.STEP_INSTANCE_NOT_EXIST);
        }

        StepExecutionResultQuery query = StepExecutionResultQuery.builder()
            .stepInstanceId(stepInstanceId)
            .executeCount(executeCount)
            .batch(batch)
            .logKeyword(keyword)
            .build();

        Set<String> matchIps = null;
        boolean filterByKeyword = StringUtils.isNotEmpty(keyword);
        if (filterByKeyword) {
            matchIps = getMatchIps(query);
            if (CollectionUtils.isEmpty(matchIps)) {
                return Collections.emptyList();
            }
        }

        // TODO Rolling
        GseTaskDTO gseTask = gseTaskService.getGseTask(stepInstance.getId(),
            stepInstance.getExecuteCount(), 0);
        if (gseTask == null) {
            if (stepInstance.getTargetServers().getIpList() != null) {
                return stepInstance.getTargetServers().getIpList();
            } else {
                return Collections.emptyList();
            }
        }

        List<AgentTaskDTO> agentTaskGroupByResultType = agentTaskDAO.listAgentTaskByResultGroup(stepInstanceId,
            executeCount, batch, resultType, tag);
        if (CollectionUtils.isEmpty(agentTaskGroupByResultType)) {
            return Collections.emptyList();
        }
        List<IpDTO> hosts = agentTaskGroupByResultType.stream()
            .map(gseTaskIpLog -> new IpDTO(gseTaskIpLog.getCloudId(), gseTaskIpLog.getIp()))
            .collect(Collectors.toList());
        if (filterByKeyword && CollectionUtils.isNotEmpty(matchIps)) {
            List<IpDTO> finalHosts = new ArrayList<>();
            for (IpDTO host : hosts) {
                if (matchIps.contains(host.convertToStrIp())) {
                    finalHosts.add(host);
                }
            }
            return finalHosts;
        } else {
            return hosts;
        }
    }

    @Override
    public List<StepExecutionRecordDTO> listStepExecutionHistory(String username, Long appId, Long stepInstanceId) {
        StepInstanceBaseDTO stepInstance = checkGetStepExecutionDetail(username, appId, stepInstanceId);
        int latestExecuteCount = stepInstance.getExecuteCount();
        if (latestExecuteCount == 0) {
            StepExecutionRecordDTO record = new StepExecutionRecordDTO();
            record.setStepInstanceId(stepInstanceId);
            record.setRetryCount(latestExecuteCount);
            record.setCreateTime(stepInstance.getCreateTime());
            return Collections.singletonList(record);
        }

        List<OperationLogDTO> operationLogs = operationLogService.listOperationLog(stepInstance.getTaskInstanceId());
        List<StepExecutionRecordDTO> records = new ArrayList<>();
        if (CollectionUtils.isEmpty(operationLogs)) {
            for (int executeCount = latestExecuteCount; executeCount >= 0; executeCount--) {
                StepExecutionRecordDTO record = new StepExecutionRecordDTO();
                record.setStepInstanceId(stepInstanceId);
                record.setRetryCount(executeCount);
                record.setCreateTime(stepInstance.getCreateTime());
                records.add(record);
            }
            return records;
        }

        Map<Integer, Long> executeCountAndCreateTimeMap = new HashMap<>();
        operationLogs.forEach(opLog -> {
            UserOperationEnum operation = opLog.getOperationEnum();
            if (UserOperationEnum.START == operation) {
                executeCountAndCreateTimeMap.put(0, opLog.getCreateTime());
            } else if ((UserOperationEnum.RETRY_STEP_ALL == operation || UserOperationEnum.RETRY_STEP_FAIL == operation)
                && (opLog.getDetail() != null && stepInstanceId.equals(opLog.getDetail().getStepInstanceId()))) {
                // 操作记录保存的是重试前的任务信息，所以executeCount需要+1
                executeCountAndCreateTimeMap.put(opLog.getDetail().getExecuteCount() + 1, opLog.getCreateTime());
            }
        });

        for (int executeCount = latestExecuteCount; executeCount >= 0; executeCount--) {
            StepExecutionRecordDTO record = new StepExecutionRecordDTO();
            record.setStepInstanceId(stepInstanceId);
            record.setRetryCount(executeCount);
            record.setCreateTime(executeCountAndCreateTimeMap.get(executeCount));
            records.add(record);
        }
        return records;
    }
}
