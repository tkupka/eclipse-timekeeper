/*******************************************************************************
 * Copyright © 2016-2020 Torkild U. Resheim
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Torkild U. Resheim - initial API and implementation
 *******************************************************************************/
package net.resheim.eclipse.timekeeper.db.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.tasks.core.ITask;

/**
 * A {@link Task} is the persisted link to an {@link AbstractTask}. It holds a
 * number of {@link Activity} instances which each represent a period of work on
 * the task.
 * 
 * @author Torkild U. Resheim
 */
@SuppressWarnings("restriction")
@Entity
@Table(name = "TASK")
@IdClass(value = GlobalTaskId.class)
@NamedQuery(name = "Task.findAll", query = "SELECT t FROM Task t")
public class Task implements Serializable {

	private static final long serialVersionUID = -2455754936217658613L;

	@Transient
	private transient Lock lock = new ReentrantLock();

	@Id
	@Column(name = "REPOSITORY_URL")
	private String repositoryUrl;

	@Id
	@Column(name = "TASK_ID")
	private String taskId;

	@ManyToOne
	@JoinColumn(name = "TASK_PROJECT")
	private Project taskProject;

	@Column(name = "TASK_URL")
	private String taskUrl;

	@Column(name = "TASK_SUMMARY")
	private String taskSummary;

	@OneToOne
	@JoinColumn(name = "CURRENTACTIVITY_ID")
	private Activity currentActivity;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Activity> activities = new ArrayList<>();


	/**
	 * Used to determine whether or not this task has been linked to a corresponding
	 * Mylyn task or if an attempt has been made.
	 */
	@Transient
	private transient TaskLinkStatus taskLinkStatus = TaskLinkStatus.UNDETERMINED;

	
	
	public Task() {
	}

	public Task(String taskId, String repositoryUrl) {
		this.taskId = taskId;
		this.repositoryUrl = repositoryUrl;
	}

	/**
	 * Creates a new tracked task and associates the instance with the given Mylyn
	 * task.
	 * 
	 * @param task the associated Mylyn task
	 */
//	public Task(ITask task) {
//		this();
//		linkWithMylynTask(task);
//		if (taskProject != null) {
//			taskProject.addTask(this);
//		}
//	}

	public void addActivity(Activity activity) {
		activities.add(activity);
	}

	/**
	 * Ends the current activity.
	 * 
	 * @return the current activity
	 * @see #startActivity()
	 * @see #endActivity(LocalDateTime)
	 */
	public Activity endActivity() {
		Activity returnActivity = null;
		if ((currentActivity != null) && (currentActivity.getEnd() == null)) {
			return endActivity(LocalDateTime.now());
		}
		currentActivity = null;
		return returnActivity;
	}

	/**
	 * Ends the current activity.
	 * 
	 * @param time the date and time the activity was ended
	 * @return the current activity
	 * @see #startActivity()
	 * @see #endActivity()
	 */
	public Activity endActivity(LocalDateTime time) {
		Activity returnActivity = null;
		if (currentActivity != null) {
			lock.lock();
			currentActivity.setEnd(LocalDateTime.now());
			returnActivity = currentActivity;
			currentActivity = null;
			lock.unlock();
		}
		return returnActivity;
	}

	/**
	 * Returns a list of all activities associated with this task. These may span
	 * over several days, months or be concentrated to one single day.
	 * 
	 * @return
	 */
	public List<Activity> getActivities() {
		return activities;
	}

	/**
	 * Returns the current activity
	 * 
	 * @return the current activity.
	 */
	public Optional<Activity> getCurrentActivity() {
		return Optional.ofNullable(currentActivity);
	}

	/**
	 * Returns the duration of work on this task at the given date. This is
	 * accumulated from all the recorded activities between 00:00 and 23:59 on that
	 * day.
	 * 
	 * @param date the date to get duration for
	 * @return the total duration of work on the date
	 */
	public Duration getDuration(LocalDate date) {
		Duration total = Duration.ZERO;
		// sum up the duration
		return getActivities().stream().map(a -> a.getDuration(date)).reduce(total, (t, u) -> t.plus(u));
	}

	public String getTaskUrl() {
		return taskUrl;
	}

	public void setTaskUrl(String taskUrl) {
		this.taskUrl = taskUrl;
	}

	public String getTaskSummary() {
		return taskSummary;
	}

	public void setTaskSummary(String taskSummary) {
		this.taskSummary = taskSummary;
	}

	/**
	 * Associates given Mylyn Task with this instance.
	 * 
	 * @param task the Mylyn task
	 */
//	public void linkWithMylynTask(ITask task) {
//		// associate this tracked task with the Mylyn task
//		this.mylynTask = task;
//		taskId = task.getTaskId();
//		repositoryUrl = TimekeeperPlugin.getRepositoryUrl(task);
//		taskUrl = task.getUrl();
//		taskSummary = task.getSummary();
//
//		// figure out the project name and set this
//		if (task instanceof AbstractTask) {
//			Set<AbstractTaskContainer> parentContainers = ((AbstractTask) task).getParentContainers();
//			parentContainers.forEach(p -> {
//				String projectName = null;
//				// it's a remote task
//				if (p instanceof RepositoryQuery) {
//					projectName = TimekeeperPlugin.getMylynProjectName(task);
//				}
//				// it's a local task
//				if (p instanceof AbstractTaskCategory) {
//					projectName = p.getSummary();
//				}
//				// the project is probably already in the database
//				Project project = TimekeeperPlugin.getDefault().getProject(projectName);
//				if (project == null) {
//					project = TimekeeperPlugin.getDefault().createAndSaveProject(task);
//				}
//				this.setProject(project);
//			});
//		}
//	}

	/**
	 * Starts a new activity and sets this as the current activity. If there is
	 * already another activity active this will be returned.
	 * 
	 * @return the current activity
	 * @see #getCurrentActivity()
	 */
	public Activity startActivity() {
		if (currentActivity == null) {
			lock.lock();
			currentActivity = new Activity(this, LocalDateTime.now());
			addActivity(currentActivity);
			lock.unlock();
			return currentActivity;
		}
		return currentActivity;
	}

	/**
	 * Returns the <i>Mylyn</i> repository URL. Internally an UUID for each Eclipse
	 * workspace is postfixed the local repository URL in order to keep them apart.
	 * This method will only return "local" for local repositories.
	 * 
	 * @return the repository URL or "local"
	 */
	public String getRepositoryUrl() {
//		if (repositoryUrl.startsWith("local-")) {
//			return TimekeeperService.KIND_LOCAL;
//		}
		return repositoryUrl;
	}

	/**
	 * Returns the tasks identifier associated with this tracked task. If it's a
	 * local task, only the number will be returned and one would have to use the
	 * repository to correctly identify the {@link ITask} instance. If the task is
	 * linked to a Mylyn task, this task's identifier will be returned.
	 * 
	 * @return the task identifier
	 */
	public String getTaskId() {
		return taskId;
	}



	public Project getProject() {
		return taskProject;
	}

	public void setProject(Project project) {
		this.taskProject = project;
		this.taskProject.addTask(this);
	}

	public TaskLinkStatus getTaskLinkStatus() {
		return taskLinkStatus;
	}

	public void setTaskLinkStatus(TaskLinkStatus taskLinkStatus) {
		this.taskLinkStatus = taskLinkStatus;
	}

	@Override
	public String toString() {
		return "Task [repositoryUrl=" + repositoryUrl + ", taskId=" + taskId + ", taskProject=" + taskProject
				+ ", taskUrl=" + taskUrl + ", taskSummary=" + taskSummary + ", currentActivity=" + currentActivity
				+ "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(repositoryUrl, taskId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Task other = (Task) obj;
		return Objects.equals(repositoryUrl, other.repositoryUrl) && Objects.equals(taskId, other.taskId);
	}

	
	

}
