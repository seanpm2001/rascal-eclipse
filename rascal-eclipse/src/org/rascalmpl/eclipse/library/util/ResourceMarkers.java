/*******************************************************************************
 * Copyright (c) 2009-2023 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
*******************************************************************************/
package org.rascalmpl.eclipse.library.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.progress.IProgressService;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.IRascalResources;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIResourceResolver;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

public class ResourceMarkers {
	
	public void removeMessageMarkers(ISourceLocation loc) {
	  try { 
	    loc = URIResolverRegistry.getInstance().logicalToPhysical(loc);
	  }
	  catch (IOException e) {
		  // couldn't resolve it, must be a physical one already.
	  }
	  
	  IResource resource = URIResourceResolver.getResource(loc);
	  if (resource instanceof IFile) {
	    IFile file = (IFile) resource;
	    try {
	      file.deleteMarkers(IRascalResources.ID_RASCAL_MARKER, false, IResource.DEPTH_ZERO);
	    } catch (CoreException ce) {
	      throw RuntimeExceptionFactory.javaException(ce, null, null);
	    }
	  }
	  else if (resource instanceof IFolder) {
		  try {
			resource.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if (resource instanceof IFile) {
						IFile file = (IFile) resource;
						file.deleteMarkers(IRascalResources.ID_RASCAL_MARKER, false, IResource.DEPTH_ZERO);
						return false;
					}
					
					return true;
				}
			}, IResource.DEPTH_INFINITE, false);
		} catch (CoreException e) {
			Activator.log("could not remove markers", e);
		}
	  }
	}

	private void removeMessageMarkers(ISet markers) {
		for (IValue msg : markers) {
			IConstructor marker = (IConstructor) msg;
			if (! marker.getType().getName().equals("Message"))
				throw RuntimeExceptionFactory.illegalArgument(marker, null, null);
			removeMessageMarkers((ISourceLocation)marker.get(1));
		}
		return;
	}
	
	public void addMessageMarkers(final ISet markers) {
		// we need two locks, one for  the UI and one for the resources
		// because we know we might need to lock a lot of resources we lock the entire workspace here
		// and prevent lock starvation where for every file and every ui operation to add a marker we 
		// need to get a new lock. The resulting code is complex but its fast.
		
		final WorkspaceModifyOperation wmo = new WorkspaceModifyOperation(ResourcesPlugin.getWorkspace().getRoot()) {
			public void execute(IProgressMonitor monitor) {
				removeMessageMarkers(markers);
				
				for (IValue msg : markers) {
					IConstructor marker = (IConstructor) msg;
					
					if (! marker.getType().getName().equals("Message")) {
					  throw RuntimeExceptionFactory.illegalArgument(marker, null, null);
					}
					
					ISourceLocation loc = (ISourceLocation)marker.get(1);
					try { 
						loc = URIResolverRegistry.getInstance().logicalToPhysical(loc);
					}
					catch (IOException e) {
						// couldn't resolve it, must be a physical one already.
					}
					
					IResource resource = URIResourceResolver.getResource(loc);
					
					if (resource instanceof IFile) {
					  IFile file = (IFile) resource;
					  
					  try {
					    int severity = IMarker.SEVERITY_INFO;
					    if (marker.getName().equals("error")) {
					        severity = IMarker.SEVERITY_ERROR;
					    }
					    else if (marker.getName().equals("warning")) {
					        severity = IMarker.SEVERITY_WARNING;
					    }

					    IString markerMessage = (IString)marker.get(0);
					    ISourceLocation markerLocation = loc;

					    if (markerLocation.hasLineColumn()) {
					        String[] attributeNames = new String[] {
					                IMarker.LINE_NUMBER, 
					                IMarker.CHAR_START, 
					                IMarker.CHAR_END, 
					                IMarker.MESSAGE, 
					                IMarker.PRIORITY, 
					                IMarker.SEVERITY
					        };

					        Object[] values = new Object[] {
					                        markerLocation.getBeginLine(), 
					                        markerLocation.getOffset(), 
					                        markerLocation.getOffset() + markerLocation.getLength(), 
					                        markerMessage.getValue(), 
					                        IMarker.PRIORITY_HIGH, 
					                        severity
					                };

					        IMarker m = file.createMarker(IRascalResources.ID_RASCAL_MARKER);
					        m.setAttributes(attributeNames, values);
					    }
					    else if (markerLocation.hasOffsetLength()) {
                            String[] attributeNames = new String[] {
                                    IMarker.CHAR_START, 
                                    IMarker.CHAR_END, 
                                    IMarker.MESSAGE, 
                                    IMarker.PRIORITY, 
                                    IMarker.SEVERITY
                            };

                            Object[] values = new Object[] {
                                    markerLocation.getOffset(), 
                                    markerLocation.getOffset() + markerLocation.getLength(), 
                                    markerMessage.getValue(), 
                                    IMarker.PRIORITY_HIGH, 
                                    severity
                            };

                            IMarker m = file.createMarker(IRascalResources.ID_RASCAL_MARKER);
                            m.setAttributes(attributeNames, values);
                        }
					    else {
					        String[] attributeNames = new String[] {
                                    IMarker.MESSAGE, 
                                    IMarker.PRIORITY, 
                                    IMarker.SEVERITY
                            };

                            Object[] values = new Object[] {
                                            markerMessage.getValue(), 
                                            IMarker.PRIORITY_HIGH, 
                                            severity
                            };

                            IMarker m = file.createMarker(IRascalResources.ID_RASCAL_MARKER);
                            m.setAttributes(attributeNames, values);
					    }
					  } 
					  catch (CoreException ce) {
					    throw RuntimeExceptionFactory.illegalArgument(loc, ce.getMessage());
					  }
					}
				}
			}
		};
		
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				IProgressService ips = PlatformUI.getWorkbench().getProgressService();
				try {
					ips.run(false, true, wmo);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof Throw) {
						throw (Throw) e.getTargetException();
					}
					else {
						throw RuntimeExceptionFactory.javaException(e.getTargetException(), null, null);
					}
				} catch (InterruptedException e) {
					Activator.getInstance().logException("??", e);
				}
			}
		});
	}
}
