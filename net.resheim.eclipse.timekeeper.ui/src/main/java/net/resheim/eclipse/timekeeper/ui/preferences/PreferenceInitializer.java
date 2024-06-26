package net.resheim.eclipse.timekeeper.ui.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.swt.graphics.RGB;

import net.resheim.eclipse.timekeeper.db.TimekeeperPlugin;
import net.resheim.eclipse.timekeeper.db.model.ActivityLabel;
import net.resheim.eclipse.timekeeper.ui.TimekeeperUiPlugin;

/**
 * Class used to initialize default preference values for the UI.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = TimekeeperUiPlugin.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.MINUTES_IDLE, 5);
		store.setDefault(PreferenceConstants.MINUTES_AWAY, 30);
		store.setDefault(PreferenceConstants.DEACTIVATE_WHEN_AWAY, true);
		while (!TimekeeperPlugin.getDefault().isReady()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Add default activity labels if the database is empty
		if (TimekeeperPlugin.getDefault().getTimekeeperService().getLabels().count() == 0) {
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Production issue", StringConverter.asString(new RGB(244, 103, 88))));
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Testing", StringConverter.asString(new RGB(245, 166, 81))));
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Prototyping", StringConverter.asString(new RGB(246, 208, 90))));
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Programming", StringConverter.asString(new RGB(87, 206, 105))));
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Debugging", StringConverter.asString(new RGB(177, 111, 209))));
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Communication", StringConverter.asString(new RGB(66, 136, 243))));
			TimekeeperPlugin.getDefault().getTimekeeperService()
			.setLabel(new ActivityLabel("Meeting", StringConverter.asString(new RGB(156, 156, 160))));
		}

	}

}
