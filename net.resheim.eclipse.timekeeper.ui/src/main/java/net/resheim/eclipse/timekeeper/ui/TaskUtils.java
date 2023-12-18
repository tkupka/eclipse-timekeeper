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

package net.resheim.eclipse.timekeeper.ui;

import java.util.Optional;

import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;

import net.resheim.eclipse.timekeeper.db.model.Task;

public class TaskUtils {

	/**
	 * Returns the Mylyn {@link ITask} associated with the given {@link Task}
	 * task. If no such task exists <code>null</code> will be returned.
	 *
	 * @param task
	 *            the time tracked task
	 * @return a Mylyn task or <code>null</code>
	 */
	public static ITask resolveMylynTask(Task task) {
		// get the repository then find the task. Seems like the Mylyn API is
		// a bit limited in this area as I could not find something more usable
		Optional<TaskRepository> tr = TasksUi.getRepositoryManager().getAllRepositories().stream()
				.filter(r -> r.getRepositoryUrl().equals(task.getRepositoryUrl())).findFirst();
		if (tr.isPresent()) {
			return TasksUi.getRepositoryModel().getTask(tr.get(), task.getTaskId());
		}
		return null;
	}

	/**
	 * Returns the Mylyn {@link ITask} associated with the given {@link Task}
	 * task. If no such task exists <code>null</code> will be returned.
	 *
	 * @param task
	 *            the time tracked task
	 * @return a Mylyn task or <code>null</code>
	 */
	public static ITask getMylynTask(Task task) {
		// get the repository then find the task. Seems like the Mylyn API is
		// a bit limited in this area as I could not find something more usable
		Optional<TaskRepository> tr = TasksUi.getRepositoryManager().getAllRepositories().stream()
				.filter(r -> r.getRepositoryUrl().equals(task.getRepositoryUrl())).findFirst();
		if (tr.isPresent()) {
			return TasksUi.getRepositoryModel().getTask(tr.get(), task.getTaskId());
		}
		return null;
	}

}
