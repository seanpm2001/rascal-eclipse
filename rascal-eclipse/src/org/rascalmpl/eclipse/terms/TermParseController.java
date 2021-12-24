/*******************************************************************************
 * Copyright (c) 2009-2012 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Anya Helene Bagge - A.H.S.Bagge@cwi.nl (Univ. Bergen)
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.eclipse.terms;

import java.util.Iterator;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.editor.NodeLocator;
import org.rascalmpl.eclipse.editor.TokenIterator;
import org.rascalmpl.eclipse.nature.IWarningHandler;
import org.rascalmpl.eclipse.nature.RascalMonitor;
import org.rascalmpl.eclipse.nature.WarningsToMessageHandler;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.ProjectURIResolver;
import org.rascalmpl.uri.file.FileURIResolver;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;

import io.usethesource.impulse.language.Language;
import io.usethesource.impulse.model.ISourceProject;
import io.usethesource.impulse.parser.IMessageHandler;
import io.usethesource.impulse.parser.IParseController;
import io.usethesource.impulse.parser.ISourcePositionLocator;
import io.usethesource.impulse.services.IAnnotationTypeInfo;
import io.usethesource.impulse.services.ILanguageSyntaxProperties;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;

public class TermParseController implements IParseController {
	private ISourceProject project;
	private IConstructor parseTree;
	private IPath path;
	private Language language;
	private IDocument document;
	private ParseJob job;
	private final static IValueFactory VF = ValueFactoryFactory.getValueFactory(); 
	private final static AnnotatorExecutor executor = new AnnotatorExecutor();
	
	public Object getCurrentAst(){
		return parseTree;
	}
	
	public void setCurrentAst(IConstructor parseTree) {
		this.parseTree = parseTree;
	}
	
	public IAnnotationTypeInfo getAnnotationTypeInfo() {
		return null;
	}

	public Language getLanguage() {
		return language;
	}

	public IPath getPath() {
		return path;
	}

	public ISourceProject getProject() {
		return project;
	}

	public ISourcePositionLocator getSourcePositionLocator() {
		return new NodeLocator();
	}

	public ISourceLocation getSourceLocation() {
	    if (project != null && path != null) {
	        return ProjectURIResolver.constructProjectURI(project.getRawProject(), path);
	    }
	    else if (path != null) {
	        return FileURIResolver.constructFileURI(path.toString());
	    }
	    else {
	        return null;
	    }
	}
	
	public ILanguageSyntaxProperties getSyntaxProperties() {
		IConstructor syntaxProperties = TermLanguageRegistry.getInstance().getSyntaxProperties(getLanguage());
		return syntaxProperties != null ? new TermLanguageSyntaxProperties(syntaxProperties) : null;
	}

	public Iterator<Object> getTokenIterator(IRegion region) {
		return new TokenIterator(true, parseTree);
	}

	@Override
	public void initialize(IPath filePath, ISourceProject project, IMessageHandler handler) {
		Assert.isTrue(filePath.isAbsolute() && project == null
				|| !filePath.isAbsolute() && project != null);

		this.path = filePath;
		this.project = project;

		TermLanguageRegistry reg = TermLanguageRegistry.getInstance();
		this.language = reg.getLanguage(path.getFileExtension());

		ISourceLocation location = null;

		if (project != null) {
			location = ProjectURIResolver.constructProjectURI(project.getRawProject(), path);
		} else {
			location = FileURIResolver.constructFileURI(path.toOSString());
		}

		this.job = new ParseJob(language.getName() + " parser", location, handler);
	}
	
	public IDocument getDocument() {
		return document;
	}
	
	public Object parse(IDocument doc, IProgressMonitor monitor) {
		if (doc == null) {
			return null;
		}
		this.document = doc;
		return parse(doc.get(), monitor);
	}
	
	private class ParseJob extends Job {
		private final IMessageHandler handler;
		private final IWarningHandler warnings;
		private final ISourceLocation loc;
		
		private String input;
		public ITree parseTree = null;

		public ParseJob(String name, ISourceLocation loc, IMessageHandler handler) {
			super(name);
			
			this.loc = loc;
			this.handler = handler;
			this.warnings = new WarningsToMessageHandler(loc, handler);
		}
		
		public void initialize(String input) {
			this.input = input;
		}
		
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			RascalMonitor rm = new RascalMonitor(monitor, warnings);
			rm.jobStart("parsing", 105);
			
			try{
				handler.clearMessages();
				IFunction parser = getParser();
				if (parser != null) {
				    parseTree = (ITree) parser.call(VF.string(input), loc);
					IFunction annotator = getAnnotator();
					if (parseTree != null && annotator != null) {
						ITree newTree = executor.annotate(annotator, parseTree, handler);
						if (newTree != null) {
							parseTree = newTree;
						}
					}
				}
			}
			catch (ParseError pe){
				int offset = pe.getOffset();
				if(offset == input.length()) --offset;
				
				handler.handleSimpleMessage("parse error", offset, offset + pe.getLength(), pe.getBeginColumn(), pe.getEndColumn(), pe.getBeginLine(), pe.getEndLine());
			} 
			catch (Throw e) {
				IValue exc = e.getException();
				if (exc.getType() == RuntimeExceptionFactory.Exception) {
					if (((IConstructor) exc).getConstructorType() == RuntimeExceptionFactory.ParseError) {
						ISourceLocation loc = (ISourceLocation) ((IConstructor) e.getException()).get(0);
						parseTree = null;
						handler.handleSimpleMessage("parse error: " + loc, loc.getOffset(), loc.getOffset() + loc.getLength(), loc.getBeginColumn(), loc.getEndColumn(), loc.getBeginLine(), loc.getEndLine());
					}
					else {
						Activator.getInstance().logException(e.getMessage(), e);
					}
				}
			}
			catch (FactTypeUseException ftuex) {
				Activator.getInstance().logException("parsing " + language.getName() + " failed", ftuex);
			}
			catch (NullPointerException npex){
				Activator.getInstance().logException("parsing " + language.getName() + " failed", npex);
			} 
			catch (Throwable e) {
				Activator.getInstance().logException("parsing " + language.getName() + " failed: " + e.getMessage(), e);
			}
			finally {
				rm.jobEnd("parsing", true);
			}
			
			return Status.OK_STATUS;
		}

		private IFunction getAnnotator() {
			return TermLanguageRegistry.getInstance().getAnnotator(language);
		}
		

		private IFunction getParser() {
			return TermLanguageRegistry.getInstance().getParser(language);
		}
	}
	
	public synchronized Object parse(String input, IProgressMonitor monitor){
		parseTree = null;
		try {
			job.initialize(input);
			job.schedule();
			job.join();
			parseTree = job.parseTree;
			return parseTree;
		} catch (InterruptedException e) {
			Activator.getInstance().logException("parser interrupted", e);
		}
		return null;
	}
}
