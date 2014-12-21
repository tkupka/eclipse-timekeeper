/*******************************************************************************
 * Copyright (c) 2014 Torkild U. Resheim.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Torkild U. Resheim - initial API and implementation
 *******************************************************************************/

package net.resheim.eclipse.timekeeper.ui.views;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.resheim.eclipse.timekeeper.TimekeeperPlugin;
import net.resheim.eclipse.timekeeper.ui.Activator;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.mylyn.commons.ui.CommonImages;
import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.internal.tasks.core.TaskCategory;
import org.eclipse.mylyn.internal.tasks.core.TaskGroup;
import org.eclipse.mylyn.internal.tasks.core.UncategorizedTaskContainer;
import org.eclipse.mylyn.internal.tasks.core.UnsubmittedTaskContainer;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.tasks.core.IRepositoryElement;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskActivationListener;
import org.eclipse.mylyn.tasks.core.ITaskContainer;
import org.eclipse.mylyn.tasks.ui.AbstractRepositoryConnectorUi;
import org.eclipse.mylyn.tasks.ui.TasksUiImages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

@SuppressWarnings("restriction")
public class TimeReportView extends ViewPart {

	private class CompositeImageDescriptor {

		ImageDescriptor icon;

		ImageDescriptor overlayKind;

	}

	private class LP extends ColumnLabelProvider {
		@Override
		public Image getImage(Object element) {
			CompositeImageDescriptor compositeDescriptor = getImageDescriptor(element);
			if (element instanceof ITask) {
				if (compositeDescriptor.overlayKind == null) {
					compositeDescriptor.overlayKind = CommonImages.OVERLAY_CLEAR;
				}
				return CommonImages.getCompositeTaskImage(compositeDescriptor.icon, compositeDescriptor.overlayKind,
						false);
			} else if (element instanceof ITaskContainer) {
				return CommonImages.getCompositeTaskImage(compositeDescriptor.icon, CommonImages.OVERLAY_CLEAR, false);
			} else {
				return CommonImages.getCompositeTaskImage(compositeDescriptor.icon, null, false);
			}
		}

		private CompositeImageDescriptor getImageDescriptor(Object object) {
			CompositeImageDescriptor compositeDescriptor = new CompositeImageDescriptor();
			if (object instanceof UncategorizedTaskContainer) {
				compositeDescriptor.icon = TasksUiImages.CATEGORY_UNCATEGORIZED;
				return compositeDescriptor;
			} else if (object instanceof UnsubmittedTaskContainer) {
				compositeDescriptor.icon = TasksUiImages.CATEGORY_UNCATEGORIZED;
				return compositeDescriptor;
			} else if (object instanceof TaskCategory) {
				compositeDescriptor.icon = TasksUiImages.CATEGORY;
			} else if (object instanceof TaskGroup) {
				compositeDescriptor.icon = CommonImages.GROUPING;
			}

			if (object instanceof ITaskContainer) {
				IRepositoryElement element = (IRepositoryElement) object;

				AbstractRepositoryConnectorUi connectorUi = null;
				if (element instanceof ITask) {
					ITask repositoryTask = (ITask) element;
					connectorUi = TasksUiPlugin.getConnectorUi(((ITask) element).getConnectorKind());
					if (connectorUi != null) {
						compositeDescriptor.overlayKind = connectorUi.getTaskKindOverlay(repositoryTask);
					}
				} else if (element instanceof IRepositoryQuery) {
					connectorUi = TasksUiPlugin.getConnectorUi(((IRepositoryQuery) element).getConnectorKind());
				}

				if (connectorUi != null) {
					compositeDescriptor.icon = connectorUi.getImageDescriptor(element);
					return compositeDescriptor;
				} else {
					compositeDescriptor.icon = TasksUiImages.TASK;
					return compositeDescriptor;
				}
			}
			return compositeDescriptor;
		}

		@Override
		public String getText(Object element) {
			if (element instanceof String) {
				return (String) element;
			}
			ITask task = ((ITask) element);
			StringBuilder sb = new StringBuilder();
			if (task.getTaskKey() != null) {
				sb.append(task.getTaskKey());
				sb.append(": ");
			}
			sb.append(task.getSummary());
			return sb.toString();
		}

	}

	class NameSorter extends ViewerSorter {
	}

	private final class TaskActivationListener implements ITaskActivationListener {
		@Override
		public void preTaskActivated(ITask task) {
			// Do nothing
		}

		@Override
		public void preTaskDeactivated(ITask task) {
			// Do nothing
		}

		@Override
		public void taskActivated(ITask task) {
			viewer.update(task, null);
		}

		@Override
		public void taskDeactivated(ITask task) {
			viewer.update(task, null);
		}
	}

	private class TimeEditingSupport extends EditingSupport {

		int weekday;

		public TimeEditingSupport(ColumnViewer viewer, int weekday) {
			super(viewer);
			this.weekday = weekday;
		}

		@Override
		protected boolean canEdit(Object element) {
			if (element instanceof ITask) {
				return true;
			}
			return false;
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return new TextCellEditor(viewer.getTree());
		}

		@Override
		protected Object getValue(Object element) {
			if (element instanceof ITask) {
				AbstractTask task = (AbstractTask) element;
				int seconds = TimekeeperPlugin.getIntValue(task, getDateString(weekday));
				if (seconds > 0) {
					return DurationFormatUtils.formatDuration(seconds * 1000, "H:mm", true);
				}
				return "0:00";
			}
			return "";
		}

		@Override
		protected void setValue(Object element, Object value) {
			if (element instanceof ITask) {
				ITask task = (ITask) element;
				if (value instanceof String) {
					String[] split = ((String) value).split(":");
					// Only minutes are given
					if (split.length == 1) {
						int newValue = Integer.parseInt(split[0]) * 60;
						TimekeeperPlugin.setValue(task, getDateString(weekday), Integer.toString(newValue));
					}
					if (split.length == 2) {
						int newValue = Integer.parseInt(split[0]) * 3600 + Integer.parseInt(split[1]) * 60;
						TimekeeperPlugin.setValue(task, getDateString(weekday), Integer.toString(newValue));
					}
					viewer.update(element, null);
				}
			}
		}

	}

	class ViewContentProvider implements ITreeContentProvider {
		public void dispose() {
			TasksUiPlugin.getTaskActivityManager().removeActivationListener(taskActivationListener);
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			Collection<AbstractTask> filteredTasks = new ArrayList<>();
			if (parentElement instanceof String) {
				Collection<AbstractTask> allTasks = TasksUiPlugin.getTaskList().getAllTasks();
				for (AbstractTask task : allTasks) {
					if (task.getAttribute(TimekeeperPlugin.ATTR_ID) != null) {
						if (parentElement.equals(TimekeeperPlugin.getProjectName(task))) {
							filteredTasks.add(task);
						}
					}
				}
			}
			return filteredTasks.toArray();
		}

		public Object[] getElements(Object parent) {
			Set<String> projects = new HashSet<>();
			Collection<AbstractTask> filteredTasks = new ArrayList<>();
			Collection<AbstractTask> allTasks = TasksUiPlugin.getTaskList().getAllTasks();
			for (AbstractTask task : allTasks) {
				if (task.getAttribute(TimekeeperPlugin.ATTR_ID) != null) {
					filteredTasks.add(task);
				}
			}
			for (AbstractTask task : filteredTasks) {
				projects.add(TimekeeperPlugin.getProjectName(task));
			}
			return projects.toArray();
		}

		@Override
		public Object getParent(Object element) {
			if (element instanceof ITask) {
				return TimekeeperPlugin.getProjectName((AbstractTask) element);
			}
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			// Projects are guaranteed to have tasks as children, tasks are
			// guaranteed to not have any children.
			if (element instanceof String) {
				return true;
			}
			return false;
		}

		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
			v.refresh();
		}
	}

	public static final String ID = "net.resheim.eclipse.timekeeper.ui.views.SampleView";

	private Action action1;

	private Action action2;

	private Action doubleClickAction;

	private final LocalDate firstDayOfWeek;

	private TaskActivationListener taskActivationListener;

	private TreeViewer viewer;

	/**
	 * The constructor.
	 */
	public TimeReportView() {
		// Determine the first date of the week
		LocalDate date = LocalDate.now();
		WeekFields weekFields = WeekFields.of(Locale.getDefault());
		int day = date.get(weekFields.dayOfWeek());
		firstDayOfWeek = date.minusDays(day - 1);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private static final DateTimeFormatter weekFormat = DateTimeFormatter.ofPattern("w");
	private static final DateTimeFormatter dateFormat = DateTimeFormatter.ISO_LOCAL_DATE;

	@Override
	public void createPartControl(Composite parent) {
		ScrolledComposite root = new ScrolledComposite(parent, SWT.V_SCROLL);
		root.setBackground(parent.getBackground());
		root.setExpandHorizontal(true);
		root.setExpandVertical(true);

		Composite main = new Composite(root, SWT.NONE);
		main.setBackground(parent.getBackground());
		main.setLayout(new GridLayout());
		root.setContent(main);

		Text dateTimeLabel = new Text(main, SWT.NONE);
		dateTimeLabel.setLayoutData(new GridData());
		StringBuilder sb = new StringBuilder();
		sb.append("Showing week ");
		sb.append(firstDayOfWeek.format(weekFormat));
		sb.append(" starting at ");
		sb.append(firstDayOfWeek.format(dateFormat));
		dateTimeLabel.setText(sb.toString());
		viewer = new TreeViewer(main, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		viewer.setContentProvider(new ViewContentProvider());
		GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
		viewer.getControl().setLayoutData(layoutData);

		createTitleColumn();
		String[] headings = Activator.getDefault().getHeadings(LocalDate.now());
		for (int i = 0; i < 7; i++) {
			createTimeColumn(i, headings[i]);
		}

		viewer.setSorter(new NameSorter());
		viewer.setInput(getViewSite());

		viewer.getTree().setHeaderVisible(true);

		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
		taskActivationListener = new TaskActivationListener();
		TasksUiPlugin.getTaskActivityManager().addActivationListener(taskActivationListener);
	}

	private TreeViewerColumn createTableViewerColumn(String title, int bound, final int colNumber) {
		final TreeViewerColumn viewerColumn = new TreeViewerColumn(viewer, SWT.NONE);
		TreeColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
		return viewerColumn;
	}

	private void createTimeColumn(int weekday, String title) {
		TreeViewerColumn keyColumn = createTableViewerColumn(title, 50, 1 + weekday);
		keyColumn.setEditingSupport(new TimeEditingSupport(keyColumn.getViewer(), weekday));
		keyColumn.setLabelProvider(new ColumnLabelProvider() {

			@Override
			public String getText(Object element) {
				if (element instanceof String) {
					// TODO: Return the sum for the project
					return "";
				}
				AbstractTask task = (AbstractTask) element;
				int seconds = TimekeeperPlugin.getIntValue(task, getDateString(weekday));
				if (seconds > 0) {
					return DurationFormatUtils.formatDuration(seconds * 1000, "H:mm", true);
				}
				return "";
			}
		});
	};

	private void createTitleColumn() {
		TreeViewerColumn keyColumn = createTableViewerColumn("Activity", 400, 1);
		keyColumn.setLabelProvider(new LP());
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(action1);
		manager.add(action2);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(action1);
		manager.add(new Separator());
		manager.add(action2);
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(action1);
		manager.add(action2);
	}

	/**
	 * Returns a string representation of the date.
	 *
	 * @param weekday
	 * @return
	 */
	private String getDateString(int weekday) {
		return firstDayOfWeek.plusDays(weekday).toString();
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				TimeReportView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}

	private void makeActions() {
		action1 = new Action() {
			@Override
			public void run() {
				Collection<AbstractTask> allTasks = TasksUiPlugin.getTaskList().getAllTasks();
				for (AbstractTask abstractTask : allTasks) {
					if (!abstractTask.isActive()) {
						// abstractTask.setAttribute(ACCUMULATED_ID, null);
					}
				}
				viewer.setInput(getViewSite());
			}
		};
		action1.setText("Action 1");
		action1.setToolTipText("Action 1 tooltip");
		action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));

		action2 = new Action() {
			@Override
			public void run() {
				showMessage("Action 2 executed");
			}
		};
		action2.setText("Action 2");
		action2.setToolTipText("Action 2 tooltip");
		action2.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		doubleClickAction = new Action() {
			@Override
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				showMessage("Double-click detected on " + obj.toString());
			}
		};
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	private void showMessage(String message) {
		MessageDialog.openInformation(viewer.getControl().getShell(), "Sample View", message);
	}
}