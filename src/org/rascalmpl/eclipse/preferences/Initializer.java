package org.rascalmpl.eclipse.preferences;

import org.eclipse.imp.preferences.IPreferencesService;
import org.eclipse.imp.preferences.PreferenceInitializer;
import org.rascalmpl.eclipse.Activator;

public class Initializer extends PreferenceInitializer {

	public void initializeDefaultPreferences() {
		IPreferencesService service = Activator.getInstance().getPreferencesService();
		service.setBooleanPreference(IPreferencesService.DEFAULT_LEVEL, RascalPreferences.enableStaticChecker, false);
	}

	public void clearPreferencesOnLevel(String level) {
		IPreferencesService service = Activator.getInstance().getPreferencesService();
		service.clearPreferencesAtLevel(level);
	}
}
