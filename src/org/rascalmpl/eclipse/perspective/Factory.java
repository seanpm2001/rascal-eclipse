/*******************************************************************************
 * Copyright (c) 2009-2012 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Emilie Balland - (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.eclipse.perspective;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;
import org.rascalmpl.eclipse.ambidexter.ReportView;
import org.rascalmpl.eclipse.console.internal.StdAndErrorViewPart;
import org.rascalmpl.eclipse.perspective.views.Tutor;

import static org.rascalmpl.eclipse.IRascalResources.*;

public class Factory implements IPerspectiveFactory {

	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();

		IFolderLayout folder = layout.createFolder("left", IPageLayout.LEFT, (float) 0.25, editorArea); 
		folder.addView("rascal.navigator");

		IFolderLayout replFolder = layout.createFolder("bottom", IPageLayout.BOTTOM, (float) 0.75, editorArea); 
		
		replFolder.addView(IConsoleConstants.ID_CONSOLE_VIEW);
		replFolder.addView(StdAndErrorViewPart.ID);
		replFolder.addView(IPageLayout.ID_PROGRESS_VIEW);
		replFolder.addView(IPageLayout.ID_PROBLEM_VIEW);
		replFolder.addView(Tutor.ID);
		
		IFolderLayout outlineFolder = layout.createFolder("outline", IPageLayout.RIGHT, (float) 0.75, editorArea);
		outlineFolder.addView(IPageLayout.ID_OUTLINE);
		outlineFolder.addView(ReportView.ID);
		
		layout.addActionSet(IPageLayout.ID_NAVIGATE_ACTION_SET);

		layout.addShowViewShortcut(JavaUI.ID_PACKAGES);
		layout.addShowViewShortcut(JavaUI.ID_SOURCE_VIEW);
		layout.addShowViewShortcut(IConsoleConstants.ID_CONSOLE_VIEW);
		layout.addShowViewShortcut(StdAndErrorViewPart.ID);

		layout.addShowViewShortcut(IPageLayout.ID_OUTLINE);
		layout.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);
		layout.addShowViewShortcut(IPageLayout.ID_TASK_LIST);
		layout.addShowViewShortcut(IPageLayout.ID_PROGRESS_VIEW);

		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file");
		layout.addNewWizardShortcut(ID_RASCAL_NEW_PROJECT_WIZARD);
		layout.addNewWizardShortcut(ID_RASCAL_NEW_FILE_WIZARD);
	}
	
}
