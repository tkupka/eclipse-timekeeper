/*******************************************************************************
 * Copyright (c) 2015 Torkild U. Resheim
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Torkild U. Resheim - initial API and implementation
 *******************************************************************************/

package net.resheim.eclipse.timekeeper.internal;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;

import net.resheim.eclipse.timekeeper.db.TimekeeperPlugin;
import net.resheim.eclipse.timekeeper.db.TimekeeperService;
import net.resheim.eclipse.timekeeper.db.model.Task;
import net.resheim.eclipse.timekeeper.db.model.TaskLinkStatus;

public class DBConnector {

	private TimekeeperService getDBService() {
		return TimekeeperPlugin.getDefault().getTimekeeperService();
	}

	public Stream<Task> findTasksForWeek(LocalDate startDate) {
		return getDBService().findTasksForWeek(startDate).map(this::linkWithMylynTask);
	}

	public Task getTask(ITask task) {
		Task ttask = getDBService().getTask(task);
		return linkWithMylynTask(ttask);

	}

	/**
	 * Links the given task with a Mylyn task if found in any of the workspace
	 * task repositories. If a local task could not be found the tracked task
	 * will be flagged as unlinked for the current workspace.
	 *
	 * @param tt
	 *            the tracked task
	 * @return the modified tracked task
	 */
	private Task linkWithMylynTask(Task tt) {
		Optional<TaskRepository> tr = Optional.ofNullable(TasksUi.getRepositoryManager())
				.map(m -> m.getAllRepositories()).orElse(Collections.emptyList()).stream()
				.filter(r -> r.getRepositoryUrl().equals(tt.getRepositoryUrl())).findFirst();

		if (tr.isPresent()) {
			// tt.linkWithMylynTask(TasksUi.getRepositoryModel().getTask(tr.get(),
			// tt.getTaskId()));
			tt.setTaskLinkStatus(TaskLinkStatus.LINKED);
		} else {
			tt.setTaskLinkStatus(TaskLinkStatus.UNLINKED);
		}
		return tt;
	}

	public Task cleanUpTask(Task task, ITask mylyTask, BiFunction<Calendar, Calendar, Long> elapsedTimeProvider) {
		return getDBService().cleanUpTask(task, mylyTask, elapsedTimeProvider);
	}

	public Stream<Task> findAllTasks() {
		return getDBService().findAllTasks();
	}

}
