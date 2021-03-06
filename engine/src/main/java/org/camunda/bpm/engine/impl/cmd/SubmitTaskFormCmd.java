/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.cmd;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;

import java.io.Serializable;
import java.util.Map;

import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.form.handler.TaskFormHandler;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionVariableSnapshotObserver;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskManager;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.task.DelegationState;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;


/**
 * @author Tom Baeyens
 * @author Joram Barrez
 */
public class SubmitTaskFormCmd implements Command<VariableMap>, Serializable {

  private static final long serialVersionUID = 1L;

  protected String taskId;
  protected VariableMap properties;

  public SubmitTaskFormCmd(String taskId, Map<String, Object> properties) {
    this.taskId = taskId;
    this.properties = Variables.fromMap(properties);
  }

  public VariableMap execute(CommandContext commandContext) {
    ensureNotNull("taskId", taskId);
    TaskManager taskManager = commandContext.getTaskManager();
    TaskEntity task = taskManager.findTaskById(taskId);
    ensureNotNull("Cannot find task with id " + taskId, "task", task);

    for(CommandChecker checker : commandContext.getProcessEngineConfiguration().getCommandCheckers()) {
      checker.checkTaskWork(task);
    }

    TaskDefinition taskDefinition = task.getTaskDefinition();
    if(taskDefinition != null) {
      TaskFormHandler taskFormHandler = taskDefinition.getTaskFormHandler();
      taskFormHandler.submitFormVariables(properties, task);
    } else {
      // set variables on standalone task
      task.setVariables(properties);
    }

    ExecutionEntity execution = task.getProcessInstance();
    ExecutionVariableSnapshotObserver variablesListener = null;
    if(execution != null) {
      variablesListener = new ExecutionVariableSnapshotObserver(execution, false);
    }

    // complete or resolve the task
    if (DelegationState.PENDING.equals(task.getDelegationState())) {
      task.resolve();
      task.createHistoricTaskDetails(UserOperationLogEntry.OPERATION_TYPE_RESOLVE);
    } else {
      task.complete();
      task.createHistoricTaskDetails(UserOperationLogEntry.OPERATION_TYPE_COMPLETE);
    }

    if (variablesListener != null) {
      return variablesListener.getVariables();
    } else {
      return task.getCaseDefinitionId() == null ? null : task.getVariablesTyped(false);
    }
  }
}
