package org.rascalmpl.eclipse.tutor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.PlatformUI;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.editor.IDEServicesModelProvider;
import org.rascalmpl.eclipse.library.util.HtmlDisplay;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.NoSuchRascalFunction;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.ideservices.BasicIDEServices;
import org.rascalmpl.library.experiments.tutor3.CourseCompiler;
import org.rascalmpl.library.experiments.tutor3.TutorCommandExecutor;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.ProjectURIResolver;
import org.rascalmpl.uri.URIResourceResolver;

import io.usethesource.impulse.builder.BuilderBase;
import io.usethesource.impulse.runtime.PluginBase;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

public class Builder extends BuilderBase {
    private final PrintWriter err = new PrintWriter(getConsoleStream());
    private PathConfig cachedConfig;
    private TutorCommandExecutor cachedExecutor;

    public Builder() {
    }

    @Override
    protected PluginBase getPlugin() {
        return Activator.getInstance();
    }

    @Override
    protected boolean isSourceFile(IFile file) {
        return "concept".equals(file.getFileExtension());
    }

    @Override
    protected boolean isNonRootSourceFile(IFile file) {
        return false;
    }

    @Override
    protected boolean isOutputFolder(IResource resource) {
        PathConfig pcfg = getPathConfig(resource);
        ISourceLocation binURI = ProjectURIResolver.constructProjectURI(resource.getProject(), resource.getProjectRelativePath());
        
        return pcfg.getBin().equals(binURI);
    }

    private PathConfig getPathConfig(IResource resource) {
        return IDEServicesModelProvider.getInstance().getPathConfig(resource.getProject());
    }

    private static Path loc2path(ISourceLocation loc) {
        return Paths.get(URIResourceResolver.getResource(loc).getLocation().toFile().toURI());
    }
    
    private static Path file2path(IFile file) {
        return Paths.get(file.getLocation().toFile().toURI());
    }
    
    @Override
    protected void compile(IFile file, IProgressMonitor monitor) {
        PathConfig pcfg = getPathConfig(file);
        
        IList courseList = pcfg.getCourses();
        if (courseList.length() == 0) {
            Activator.getInstance().logException("no courses configured in META-INF/RASCAL.MF", null);
            return;
        }
        else if (courseList.length() > 1) {
            Activator.getInstance().logException("ignoring all but the first Courses from META-INF/RASCAL.MF", null);
        }
        
        // TODO: project should be able to have multiple courses directories
        Path coursesSrcPath = loc2path((ISourceLocation) courseList.get(0));
        
        // TODO: a project may have multiple source paths
        Path libSrcPath = loc2path((ISourceLocation)pcfg.getSrcs().get(0));
        Path destPath = loc2path((ISourceLocation)pcfg.getBin()).resolve("courses");
        String courseName  = getCourseName(pcfg, file, coursesSrcPath);
        
        try {
            CourseCompiler.copyStandardFiles(coursesSrcPath, destPath.resolve(courseName));

            TutorCommandExecutor executor = getCommandExecutor(pcfg);
           
            if (courseName != null) {
                String anchor = getConceptAnchor(coursesSrcPath, file);
                URL url = new URL(destPath.resolve(courseName).toUri() + "/index.html#" + anchor);

                CourseCompiler.compileCourseCommand(getDoctorClasspath(), coursesSrcPath, courseName, destPath, libSrcPath, pcfg, executor);
                err.flush();

                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                            System.err.println(url);
                            HtmlDisplay.browse(url);
                    }
                });
               
                return;
            }
            else {
                Activator.log("could not find course name for " + file, null);
            }
        } catch (IOException | NoSuchRascalFunction | URISyntaxException e) {
            Activator.log("unexpected error during course compilation for " + file, e);
            return;
        }
    }

    private static String getConceptAnchor(Path coursesSrcPath, IFile file) {
        Path name = coursesSrcPath.relativize(file2path(file)).getParent();
        int n = name.getNameCount();
        return n >= 2 ? name.getName(n-2) + "-" + name.getName(n-1) : name.getFileName().toString();
    }
    
    private static String getDoctorClasspath() throws IOException {
        return libLocation("lib/jruby.jar") + File.pathSeparator
                + libLocation("lib/jcommander.jar") + File.pathSeparator
                + libLocation("lib/asciidoctor.jar")
                ;
    }

    private static String libLocation(String lib) throws IOException {
        return FileLocator.resolve(Activator.getInstance().getBundle().getEntry(lib)).getPath();
    }
    
    private TutorCommandExecutor getCommandExecutor(PathConfig pcfg)
            throws IOException, NoSuchRascalFunction, URISyntaxException {
        if (this.cachedConfig != null && !freshConfig(pcfg)) {
            return cachedExecutor;
        }
         
        cachedConfig = pcfg;
        cachedExecutor = new TutorCommandExecutor(pcfg, err, new BasicIDEServices(err));
        
        return this.cachedExecutor;
    }
    
    private boolean freshConfig(PathConfig pcfg) {
        return !this.cachedConfig.toString().equals(pcfg.toString());
    }

    private String getCourseName(PathConfig pcfg, IFile file, Path coursesSrcPath) {
        String filePath = file.getLocation().toFile().getAbsolutePath();
        
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(coursesSrcPath)) {
            for (Path course : dirs) {
                if (filePath.startsWith(course.toAbsolutePath().toFile().getAbsolutePath())) {
                    return course.getFileName().toString();
                }
            }
        } 
        catch (IOException e) {
            Activator.log("could not compute course name", e);
        }
        
        return null;
    }

    @Override
    protected void collectDependencies(IFile file) {

    }

    @Override
    protected String getErrorMarkerID() {
        return "tutor3.error";
    }

    @Override
    protected String getWarningMarkerID() {
        return "tutor3.warning";
    }

    @Override
    protected String getInfoMarkerID() {
        return "tutor3.info";
    }
}