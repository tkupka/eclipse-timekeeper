package net.resheim.eclipse.timekeeper.db;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.internal.tasks.core.AbstractTaskContainer;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.resheim.eclipse.timekeeper.db.model.Activity;
import net.resheim.eclipse.timekeeper.db.model.ActivityLabel;
import net.resheim.eclipse.timekeeper.db.model.GlobalTaskId;
import net.resheim.eclipse.timekeeper.db.model.Project;
import net.resheim.eclipse.timekeeper.db.model.ProjectType;
import net.resheim.eclipse.timekeeper.db.model.Task;
import net.resheim.eclipse.timekeeper.db.report.ReportTemplate;

public class TimekeeperService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimekeeperService.class);

	/**
	 * Some features connected to Mylyn has no knowledge of Timekeeper tasks and in
	 * order to avoid excessive lookups in the database, we utilise a simple cache.
	 */
//	private static Map<ITask, Task> linkCache = new HashMap<>();

	private static final String LOCAL_REPO_ID = "local";

	private static final String PLUGIN_ID = "net.resheim.eclipse.timekeeper"; //$NON-NLS-1$
	private static final String KEY_UUID = "local-uuid"; //$NON-NLS-1$

	private ThreadLocal<EntityManager> entityManagers = new ThreadLocal<EntityManager>();
	private EntityManagerFactory entityManagerFactory;

	public TimekeeperService(String jdbcUrl, boolean extraThread) {
		connectToDatabase(jdbcUrl, extraThread);
	}

	private EntityManager resolveEntityManager() {
		EntityManager em = Optional.ofNullable(entityManagers.get()).orElseGet(() -> {
			LOGGER.debug("Creating new enetity manager");
			EntityManager entityManager = entityManagerFactory.createEntityManager();
			entityManagers.set(entityManager);
			return entityManager;
		});
		if (!em.isOpen()) {
			System.out.println();
		}
		return em;
	}

	private <T> T executeInTransaction(Function<EntityManager, T> function) {
		EntityManager em = resolveEntityManager();
		EntityTransaction transaction = em.getTransaction();
		boolean joinTx = transaction.isActive();
		if (!joinTx) {
			transaction.begin();
		}
		T entity = null;
		try {
			entity = function.apply(em);
		} catch (Exception e) {
			transaction.setRollbackOnly();
			throw new RuntimeException("Exception occured in TX", e);
		} finally {
			if (!joinTx) {
				if (transaction.getRollbackOnly()) {
					transaction.rollback();
				} else {
					transaction.commit();
				}
//				em.close();
			}
		}
		return entity;
	}

	boolean initialized() {
		return (entityManagerFactory != null && entityManagerFactory.isOpen());
	}

	private <T> T saveEntityInTransaction(final T entity) {
		return executeInTransaction(em -> {
			em.persist(entity);
			return entity;
		});

	}

	private <T> void deleteEntityInTransaction(T entity) {
		executeInTransaction(em -> {
			em.remove(entity);
			return entity;
		});
	}

	private <T> TypedQuery<T> createNamedQuery(String queryName, Class<T> entityType) {
		return executeInTransaction(em -> {
			return em.createNamedQuery(queryName, entityType);

		});
	}

	private <T> T findEntityByPk(Object pk, Class<T> entityType) {
		return executeInTransaction(em -> {
			return em.find(entityType, pk);

		});
	}

	private void createEntityManager(Map<String, Object> props) {
		try {
			entityManagerFactory = new PersistenceProvider()
					.createEntityManagerFactory("net.resheim.eclipse.timekeeper.db", props);
		} catch (Exception e) {
			LOGGER.error("Unable to create entity manager", e);
		}
	}

	public void closePersistence() {
		entityManagerFactory.close();
	}

	EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	/**
	 * Returns the Timekeeper {@link Task} associated with the given Mylyn task. If
	 * no such task exists it will be created.
	 * 
	 * @param task the Mylyn task
	 * @return a {@link Task} associated with the Mylyn task
	 * @throws InterruptedException
	 */
	public Task getTask(ITask mylynTask) {
		// the UI will typically attempt to get some task details before the database is
		LOGGER.debug("Loading task for mylyn task [{}]", mylynTask);
//		if (linkCache.containsKey(mylynTask)) {
//			return linkCache.get(mylynTask);
//		}
		return executeInTransaction(em -> {
			GlobalTaskId id = new GlobalTaskId(getRepositoryUrl(mylynTask), mylynTask.getTaskId());
			Task task = findEntityByPk(id, Task.class);
			// make sure there is a link between the two tasks, this would be the case if
			// the tracked task was just
			// loaded from the database

//			linkCache.put(mylynTask, task);
			Optional.ofNullable(task).ifPresent(t -> synchronizeTask(t, mylynTask));
			return task;
		});
	}

	private Task synchronizeTask(Task task, ITask mylynTask) {
		if (!task.getTaskSummary().equals(mylynTask.getSummary())) {
			task.setTaskSummary(mylynTask.getSummary());
			saveEntityInTransaction(task);
		}
		saveEntityInTransaction(task);
		return task;
	}

	public Task createTask(ITask task) {
		// the UI will typically attempt to get some task details before the database is
		LOGGER.debug("Creating task for mylyn task [{}]", task);
		return executeInTransaction(em -> {
			// no such tracked task exists, create one
			Task tt = createTaskInternal(task);
			saveEntityInTransaction(tt);
//			linkCache.put(task, tt);
			return tt;
		});
	}

	private Task createTaskInternal(ITask mylynTask) {
		// associate this tracked task with the Mylyn task
		Task task = new Task(mylynTask.getTaskId(), getRepositoryUrl(mylynTask));
		task.setTaskUrl(mylynTask.getUrl());
		task.setTaskSummary(mylynTask.getSummary());

		String projectName = getMylynProjectName(mylynTask);
		Project project = Optional.ofNullable(getProject(projectName))
				.orElseGet(() -> createAndSaveProject(projectName, mylynTask.getConnectorKind()));
		task.setProject(project);

		return task;
	}

	/**
	 * Returns the name of the container holding the supplied task.
	 *
	 * @param task task to find the name for
	 * @return the name of the task
	 */
	public String getMylynProjectName(ITask task) {
		if (task instanceof AbstractTask) {
			AbstractTask abstractTask = (AbstractTask) task;
			if (!abstractTask.getParentContainers().isEmpty()) {
				AbstractTaskContainer next = abstractTask.getParentContainers().iterator().next();
				return next.getSummary();
			}
		}
		return "[UNDETERMINED]";
	}

	/**
	 * This method will return the repository URL for tasks in repositories that are
	 * not local. If the task is in a local repository, the Timekeeper repository
	 * identifier is returned if it exists. If it does not exist, it will be
	 * created, associated with the repository and returned.
	 *
	 * @param task the task to get the repository URL for
	 * @return the repository URL or {@link UUID}
	 */
	private String getRepositoryUrl(ITask task) {
		String repositoryUrl = task.getRepositoryUrl();
		if (LOCAL_REPO_ID.equals(repositoryUrl)) {
			repositoryUrl = String.format("%s-%s", repositoryUrl, getWorkspaceUuid());
		}
		return repositoryUrl;
	}

	private String getWorkspaceUuid() {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		QualifiedName qname = new QualifiedName(PLUGIN_ID, KEY_UUID);
		try {
			String uuid = workspace.getRoot().getPersistentProperty(qname);
			if (uuid == null) {
				uuid = UUID.randomUUID().toString();
				workspace.getRoot().setPersistentProperty(qname, uuid);
			}
			return uuid;
		} catch (CoreException e) {
			LOGGER.error("Unable to set workspace UUID");
		}
		return "";
	}

	public Task persistTask(Task task) {
		LOGGER.debug("Persisting task [{}]", task);
		return saveEntityInTransaction(task);
	}

	public Task endTaskActivity(ITask mylynTask, LocalDateTime endTime, boolean reactivate) {
		LOGGER.debug("Finishing task for mylyn task[{}]", mylynTask);
		Task task = getTask(mylynTask);
		task.endActivity(endTime);
		if (reactivate) {
			task.startActivity();
		}
		persistTask(task);
		return task;

	}

	/**
	 * Exports Timekeeper related data to two separate CSV files. One for
	 * {@link Task}, another for {@link Activity} instances and yet another for the
	 * relations between these two.
	 * 
	 * TODO: Compress into zip
	 * 
	 * @param path the path to the directory
	 * @throws IOException
	 */
	public int exportTo(Path path) throws IOException {
		if (!path.toFile().exists()) {
			Files.createDirectory(path);
		}
		Path tasks = path.resolve("trackedtask.csv");
		Path activities = path.resolve("activity.csv");
		Path relations = path.resolve("trackedtask_activity.csv");

		return executeInTransaction(em -> {
			int tasksExported = em.createNativeQuery("CALL CSVWRITE('" + tasks + "', 'SELECT * FROM TRACKEDTASK');")
					.executeUpdate();
			int activitiesExported = em
					.createNativeQuery("CALL CSVWRITE('" + activities + "', 'SELECT * FROM ACTIVITY');")
					.executeUpdate();
			// relations are not automatically created, so we do this the easy way
			em.createNativeQuery("CALL CSVWRITE('" + relations + "', 'SELECT * FROM TRACKEDTASK_ACTIVITY');")
					.executeUpdate();
			return tasksExported + activitiesExported;

		});
	}

	/**
	 * Import and merge records from the specified location.
	 * 
	 * @param path root location of the
	 * @return
	 * @throws IOException
	 */
	public int importFrom(Path path) throws IOException {
		Path tasks = path.resolve("trackedtask.csv");
		Path activities = path.resolve("activity.csv");
		Path relations = path.resolve("trackedtask_activity.csv");
		if (!tasks.toFile().exists()) {
			throw new IOException("'trackedtask.csv' does not exist in the specified location.");
		}
		if (!activities.toFile().exists()) {
			throw new IOException("'activity.csv' does not exist in the specified location.");
		}
		if (!relations.toFile().exists()) {
			throw new IOException("'trackedtask_activity.csv' does not exist in the specified location.");
		}
		return executeInTransaction(em -> {
			em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE;").executeUpdate();
			int tasksImported = em.createNativeQuery("MERGE INTO TRACKEDTASK (SELECT * FROM CSVREAD('" + tasks + "'));")
					.executeUpdate();
			int activitiesImported = em
					.createNativeQuery("MERGE INTO ACTIVITY (SELECT * FROM CSVREAD('" + activities + "'));")
					.executeUpdate();
			em.createNativeQuery("MERGE INTO TRACKEDTASK_ACTIVITY (SELECT * FROM CSVREAD('" + relations + "'));")
					.executeUpdate();
			em.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE;").executeUpdate();
			// update all instances with potentially new content
			TypedQuery<Task> createQuery = em.createNamedQuery("Task.findAll", Task.class);
			List<Task> resultList = createQuery.getResultList();
			for (Task trackedTask : resultList) {
				em.refresh(trackedTask);
			}
			return tasksImported + activitiesImported;

		});
	}

	private void connectToDatabase(String jdbcUrl, boolean startInThread) {
		Runnable runnable = () -> {
			LOGGER.info("Connecting to Timekeeper database");
			Map<String, Object> props = new HashMap<String, Object>();
			try {
				LOGGER.info("Using database at '{}'", jdbcUrl);

				// baseline the database
				// Flyway flyway = Flyway.configure()
				// .dataSource(jdbc_url, "sa", "")
				// .baselineOnMigrate(false)
				// .locations("classpath:/db/").load();
				// flyway.migrate();
				// https://www.eclipse.org/forums/index.php?t=msg&goto=541155&
				props.put(PersistenceUnitProperties.CLASSLOADER, TimekeeperService.class.getClassLoader());
				props.put(PersistenceUnitProperties.JDBC_URL, jdbcUrl);
				props.put(PersistenceUnitProperties.JDBC_DRIVER, "org.h2.Driver");
				props.put(PersistenceUnitProperties.JDBC_USER, "sa");
				props.put(PersistenceUnitProperties.JDBC_PASSWORD, "");
				props.put(PersistenceUnitProperties.LOGGING_LEVEL, "fine"); // fine / fine
				// we want Flyway to create the database, it gives us better control over
				// migrating?
				// props.put(PersistenceUnitProperties.DDL_GENERATION, "create-tables");
				// props.put(PersistenceUnitProperties.JAVASE_DB_INTERACTION, "true");
				createEntityManager(props);
				LOGGER.info("Database connection established");
			} catch (Exception e) {
				throw new RuntimeException("Could not connect to Timekeeper database at " + jdbcUrl, e);
			}

		};
		if (startInThread) {
			Thread thread = new Thread(runnable);
			thread.start();
		} else {
			runnable.run();
		}
	}

	/**
	 * Return all tracked tasks, those that are associated with a Mylyn task will
	 * have the proper assignment.
	 * 
	 * @return a stream of tasks
	 */
	public Stream<Task> findTasksForWeek(LocalDate startDate) {
		LOGGER.debug("Loading all tasks from staring date [{}]", startDate);
		return executeInTransaction(em -> createNamedQuery("Task.findAll", Task.class).getResultStream()
//				// TODO: Move filtering to database
				.filter(tt -> hasData(tt, startDate)));
	}

	public Stream<Task> findAllTasks() {
		LOGGER.debug("Loading all tasks.");
		return executeInTransaction(em -> createNamedQuery("Task.findAll", Task.class).getResultStream());
	}

	/**
	 * Creates a new {@link Project} based on information obtained from the Mylyn
	 * task. A {@link ProjectType} will also be created if it does not already
	 * exist.
	 * 
	 * @param task
	 * @return
	 */
	public Project createAndSaveProject(String name, String type) {
		ProjectType projectType = findEntityByPk(type, ProjectType.class);
		if (projectType == null) {
			projectType = new ProjectType(type);
			saveEntityInTransaction(projectType);
		}
		Project project = new Project(projectType, name);
		saveEntityInTransaction(project);
		return project;

	}

	public Project getProject(String title) {
		return findEntityByPk(title, Project.class);
	}

	/**
	 * Finds and returns all activity label instances in the database.
	 * 
	 * @return a stream of labels
	 */
	public Stream<ActivityLabel> getLabels() {
		return createNamedQuery("ActivityLabel.findAll", ActivityLabel.class).getResultStream();
	}

	public void setLabel(ActivityLabel label) {
		saveEntityInTransaction(label);
	}

	public void removeLabel(ActivityLabel label) {
		deleteEntityInTransaction(label);
	}

	/**
	 * Ends the activity currently active on the given Mylyn task.
	 * 
	 * @param task the Mylyn task to start
	 */
	public void endMylynTask(ITask task) {
		LOGGER.debug("Ending activity for mylyn task [{}]", task);
		Task ttask = getTask(task);
		if (ttask != null) {
			Activity activity = ttask.endActivity();
			saveEntityInTransaction(activity);
			LOGGER.debug("Dectivating task '{}'", task);
		}
	}

	/**
	 * Creates a new tracked task associated with the Mylyn task if the prior is not
	 * present, and starts a new activity.
	 * 
	 * @param task the Mylyn task to start
	 */
	public void startMylynTask(ITask task) {
		LOGGER.debug("Starting mylyn task [{}]", task);

		Task ttask = Optional.ofNullable(getTask(task)).orElseGet(() -> createTask(task));
		if (ttask != null) {
			Activity activity = ttask.startActivity();
			saveEntityInTransaction(activity);
			LOGGER.debug("Activating task '{}'", task);
//			notifyListeners();
		}
	}

	/**
	 * Returns a list of all report templates stored in the preferences.
	 *
	 * @return a list of templates
	 */
	@SuppressWarnings("unchecked")
	public Map<String, ReportTemplate> getTemplates() {
		Map<String, ReportTemplate> templates = new HashMap<>();
		// and load the contents from the current preferences
		IPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, TimekeeperPlugin.BUNDLE_ID);
		byte[] decoded = Base64.getDecoder().decode(store.getString(TimekeeperPlugin.PREF_REPORT_TEMPLATES));
		ByteArrayInputStream bis = new ByteArrayInputStream(decoded);
		try {
			ObjectInputStream ois = new ObjectInputStream(bis);
			java.util.List<ReportTemplate> rt = (java.util.List<ReportTemplate>) ois.readObject();
			for (ReportTemplate t : rt) {
				templates.put(t.getName(), t);
			}
		} catch (IOException | ClassNotFoundException e) {
			LOGGER.error("Could not load report templates", e);
		}
		return templates;
	}

	private static boolean hasData/* this week */(Task task, LocalDate startDate) {
		// this should only be NULL if the database has not started yet. See
		// databaseStateChanged()
		if (task == null) {
			return false;
		}
		LocalDate endDate = startDate.plusDays(7);
		Stream<Activity> filter = task.getActivities().stream()
				.filter(a -> a.getDuration(startDate, endDate) != Duration.ZERO);
		boolean filtered = filter.count() > 0;
		return filtered;
	}

	public Task cleanUpTask(Task task, ITask mylynTask, BiFunction<Calendar, Calendar, Long> elapsedTimeProvider) {
		task.getCurrentActivity().ifPresent(activity -> {

			if (mylynTask != null && !mylynTask.isActive()) {
				// try to figure out when it was last active
				ZonedDateTime start = activity.getStart().atZone(ZoneId.systemDefault());
				ZonedDateTime end = start.plusMinutes(30);
				Calendar s = Calendar.getInstance();
				Calendar e = Calendar.getInstance();
				while (true) {
					s.setTime(Date.from(start.toInstant()));
					e.setTime(Date.from(end.toInstant()));
					Long elapsedTime = elapsedTimeProvider.apply(s, e);
					// update the end time on the activity
					if (elapsedTime == 0 || e.after(Calendar.getInstance())) {
						activity.setEnd(LocalDateTime.ofInstant(e.toInstant(), ZoneId.systemDefault()));
						task.endActivity();
						break;
					}
					start = start.plusMinutes(30);
					end = end.plusMinutes(30);
				}
			}
		});
		return task;
	}

}
