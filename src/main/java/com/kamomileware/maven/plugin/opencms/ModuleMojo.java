package com.kamomileware.maven.plugin.opencms;

import com.kamomileware.maven.plugin.opencms.packaging.*;
import com.kamomileware.maven.plugin.opencms.util.ClassesPackager;
import com.kamomileware.maven.plugin.opencms.util.ModuleStructure;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Build opencms module. The process includes the manifest generation if
 * <code>${generate.manifest}</code> and module properties descriptor are set.
 * This goal is binded to <code>package</code> building phase for the
 * opencms-module projects.
 *
 * @author jagarcia
 */
@Mojo(name= "module", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ModuleMojo extends AbstractModuleMojo {

	/**
	 * The directory for the generated module.
	 */
  @Parameter (property="project.build.directory",required = true)
	private String outputDirectory;

	/**
	 * The name of the generated module.
	 */
  @Parameter( property="project.build.finalName", required = true)
	private String moduleName;

	/**
	 * Classifier to add to the generated module file. If given, the artifact
	 * will be an attachment instead. The classifier will not be applied to the
	 * jar file of the project - only to the module file.
	 */
  @Parameter
	private String classifier;

	/**
	 * The comma separated list of tokens to exclude from the module before
	 * packaging.
	 */
  @Parameter(alias = "packagingExcludes")
	private String packagingExcludes;

	/**
	 * The comma separated list of tokens to include in the module before
	 * packaging. By default everything is included.
	 */
  @Parameter (alias="packagingIncludes")
	private String packagingIncludes;

	/**
	 * Whether this is the main artifact being built. Set to <code>false</code>
	 * if you don't want to install or deploy it to the local repository instead
	 * of the default one in an execution.
	 */
  @Parameter (property="primaryArtifact", defaultValue = "true")
	private boolean primaryArtifact = true;

	/**
	 * Whether or not to fail the build is the <code>manifest.xml</code> file is
	 * missing. Set to <code>false</code> if you want you module built without a
	 * <code>manifest.xml</code> file, besides the module won't be installable
	 * on OpenCms system.
	 */
  @Parameter (property="failOnMissingManifestXml", defaultValue="true")
	private boolean failOnMissingManifestXml = true;

	/**
	 * The classifier to use for the attached classes artifact.
	 */
  @Parameter (defaultValue="")
	private String classesClassifier = "";

	/**
	 * The archiver.
	 */
  @Component (role=org.codehaus.plexus.archiver.Archiver.class, hint="jar")
	private JarArchiver moduleArchiver;

	/**
	 *
	 */
  @Component
	private MavenProjectHelper projectHelper;

	/**
	 * Executes the ModuleMojo on the current project.
	 *
	 * @throws MojoExecutionException
	 *             if an error occurred while building the module
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		File moduleFile = getTargetModuleFile();

		try {
			performPackaging(moduleFile);
		} catch (DependencyResolutionRequiredException e) {
			throw new MojoExecutionException("Error assembling Module: " + e.getMessage(), e);
		} catch (ManifestException e) {
			throw new MojoExecutionException("Error assembling Module", e);
		} catch (IOException e) {
			throw new MojoExecutionException("Error assembling Module", e);
		} catch (ArchiverException e) {
			throw new MojoExecutionException("Error assembling Module: " + e.getMessage(), e);
		}
	}

	/**
	 * Generates the module according to the <tt>mode</tt> attribute.
	 *
	 * @param moduleFile
	 *            the target module file
	 * @throws IOException
	 *             if an error occurred while copying files
	 * @throws ArchiverException
	 *             if the archive could not be created
	 * @throws ManifestException
	 *             if the manifest could not be created
	 * @throws DependencyResolutionRequiredException
	 *             if an error occurred while resolving the dependencies
	 * @throws MojoExecutionException
	 *             if the execution failed
	 * @throws MojoFailureException
	 *             if a fatal exception occurred
	 */
	private void performPackaging(File moduleFile) throws IOException, ArchiverException, ManifestException,
			DependencyResolutionRequiredException, MojoExecutionException, MojoFailureException {
		getLog().info("Packaging OpenCms Module");

		buildExplodedModule(getModuleDirectory());

		MavenArchiver archiver = new MavenArchiver();

		archiver.setArchiver(moduleArchiver);

		archiver.setOutputFile(moduleFile);

		getLog().debug("Excluding " + Arrays.asList(getPackagingExcludes()) + " from the generated webapp archive.");
		getLog().debug("Including " + Arrays.asList(getPackagingIncludes()) + " in the generated webapp archive.");

		moduleArchiver.addDirectory(getModuleDirectory(), getPackagingIncludes(), getPackagingExcludes());

		final File manifestXmlFile = new File(getModuleDirectory(), MANIFEST_NAME);
		if (!manifestXmlFile.exists()) {
			if (!failOnMissingManifestXml) {
				getLog().warn("Build won't fail if manifest.xml file is missing.");
				// // The flag is wrong in plexus-archiver so it will need to be
				// fixed at some point
				// zipArchiver.setIgnoreWebxml( false );
			} else {
				throw new ManifestException("Module Manifest missing.");
			}
		}

		// create archive
		archiver.createArchive(getProject(), getArchive());

		// create the classes to be attached if necessary
		ClassesPackager packager = new ClassesPackager();
		final File classesDirectory = getClassesDirectory();
		if (classesDirectory.exists()) {
			getLog().info("Packaging classes");
			packager.packageClasses(classesDirectory, getTargetClassesFile(), getModuleArchiver(), getProject(), getArchive());
			projectHelper.attachArtifact(getProject(), "jar", getClassesClassifier(), getTargetClassesFile());
		}

		String classifier = this.classifier;
		if (classifier != null) {
			projectHelper.attachArtifact(getProject(), "zip", classifier, moduleFile);
		} else {
			Artifact artifact = getProject().getArtifact();
			if (primaryArtifact) {
				artifact.setFile(moduleFile);
			} else if (artifact.getFile() == null || artifact.getFile().isDirectory()) {
				artifact.setFile(moduleFile);
			}
		}
	}

	protected static File getTargetFile(File basedir, String finalName, String classifier, String type) {
		if (classifier == null) {
			classifier = "";
		} else if (classifier.trim().length() > 0 && !classifier.startsWith("-")) {
			classifier = "-" + classifier;
		}

		return new File(basedir, finalName + classifier + "." + type);
	}

	protected File getTargetModuleFile() {
		return getTargetFile(new File(getOutputDirectory()), getModuleName(), getClassifier(), "zip");

	}

	protected File getTargetClassesFile() {
		return getTargetFile(new File(getOutputDirectory()), getModuleName(), getClassesClassifier(), "jar");
	}

	// Getters and Setters

	public String getClassifier() {
		return classifier;
	}

	public void setClassifier(String classifier) {
		this.classifier = classifier;
	}

	public String[] getPackagingExcludes() {
		if (StringUtils.isEmpty(packagingExcludes)) {
			return new String[] { "**/__properties", "**/__properties/**" };
		} else {
			return StringUtils.split(packagingExcludes, ",");
		}
	}

	public void setPackagingExcludes(String packagingExcludes) {
		this.packagingExcludes = packagingExcludes;
	}

	public String[] getPackagingIncludes() {
		if (StringUtils.isEmpty(packagingIncludes)) {
			return new String[] { "**" };
		} else {
			return StringUtils.split(packagingIncludes, ",");
		}
	}

    /**
     *
     * @param moduleDirectory
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    public void buildExplodedModule(File moduleDirectory) throws MojoExecutionException, MojoFailureException {
        moduleDirectory.mkdirs();

        try {
            buildModule(project, moduleDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not build module", e);
        }
    }

    /**
     * Builds the module for the specified project with the new packaging task
     * thingy
     *
     * Classes, libraries and tld files are copied to the
     * <tt>webappDirectory</tt> during this phase.
     *
     * @param project
     *            the maven project
     * @param moduleDirectory
     *            the target directory
     * @throws MojoExecutionException
     *             if an error occurred while packaging the webapp
     * @throws MojoFailureException
     *             if an unexpected error occurred while packaging the webapp
     * @throws IOException
     *             if an error occurred while copying the files
     */
    @SuppressWarnings("unchecked")
    public void buildModule(MavenProject project, File moduleDirectory) throws MojoExecutionException, MojoFailureException, IOException {
        ModuleStructure cache;
        if (useCache && cacheFile.exists()) {
            cache = new ModuleStructure(project.getDependencies(), moduleStructureSerialier.fromXml(cacheFile));
        } else {
            cache = new ModuleStructure(project.getDependencies(), null);
        }

        final long startTime = System.currentTimeMillis();
        getLog().info("Assembling module [" + project.getArtifactId() + "] in [" + moduleDirectory + "]");

        List<FileUtils.FilterWrapper> defaultFilterWrappers = null;
        try {
            MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution();
            mavenResourcesExecution.setEscapeString(escapeString);

            defaultFilterWrappers = mavenFileFilter.getDefaultFilterWrappers(project, filters, escapedBackslashesInFilePath, this.session,
                    mavenResourcesExecution);

        } catch (MavenFilteringException e) {
            getLog().error("fail to build filering wrappers " + e.getMessage());
            throw new MojoExecutionException(e.getMessage(), e);
        }

        final ModulePackagingContext context = new DefaultModulePackagingContext(moduleDirectory, cache, defaultFilterWrappers,
                getNonFilteredFileExtensions(), filteringDeploymentDescriptors, this.artifactFactory);

        ModulePackagingTask modulePackagingTask = new ModuleProjectPackagingTask(moduleResources, manifestXml, generateManifestXml);

        modulePackagingTask.performPackaging(context);

        // Post packaging
        final List<ModulePostPackagingTask> postPackagingTasks = getPostPackagingTasks();
        final Iterator<ModulePostPackagingTask> it2 = postPackagingTasks.iterator();
        while (it2.hasNext()) {
            ModulePostPackagingTask task = it2.next();
            task.performPostPackaging(context);
        }

        getLog().info("OpenCms Module assembled in [" + (System.currentTimeMillis() - startTime) + " msecs]");

    }

    /**
     * Returns a <tt>List</tt> of the
     * {@link com.kamomileware.maven.plugin.opencms.packaging.ModulePostPackagingTask}
     * instances to invoke to perform the post-packaging.
     *
     * @return the list of post packaging tasks
     */
    private List<ModulePostPackagingTask> getPostPackagingTasks() {
        final List<ModulePostPackagingTask> postPackagingTasks = new ArrayList<ModulePostPackagingTask>(1);
        if (useCache) {
            postPackagingTasks.add(new SaveModuleStructurePostPackagingTask(cacheFile));
        }
        // TODO add lib scanning to detect duplicates
        return postPackagingTasks;
    }

    /**
     * ModulePackagingContext default implementation
     */
    protected class DefaultModulePackagingContext implements ModulePackagingContext {

        private final ArtifactFactory artifactFactory;

        private final ModuleStructure moduleStructure;

        private final File moduleDirectory;

        private final List<FileUtils.FilterWrapper> filterWrappers;

        private List<String> nonFilteredFileExtensions;

        private boolean filteringDeploymentDescriptors;

        public DefaultModulePackagingContext() {
            this.moduleDirectory = null;
            this.moduleStructure = null;
            this.filterWrappers = null;
            this.artifactFactory = null;
        }

        public DefaultModulePackagingContext(File moduleDirectory, final ModuleStructure moduleStructure, List<FileUtils.FilterWrapper> filterWrappers,
                                             List<String> nonFilteredFileExtensions, boolean filteringDeploymentDescriptors, ArtifactFactory artifactFactory) {
            this.moduleDirectory = moduleDirectory;
            this.moduleStructure = moduleStructure;
            this.filterWrappers = filterWrappers;
            this.artifactFactory = artifactFactory;
            this.filteringDeploymentDescriptors = filteringDeploymentDescriptors;
            if( nonFilteredFileExtensions != null){
                this.nonFilteredFileExtensions = nonFilteredFileExtensions;
            } else {
                this.nonFilteredFileExtensions = Collections.emptyList();
            }
        }

        public MavenProject getProject() {
            return project;
        }

        public File getModuleDirectory() {
            return moduleDirectory;
        }

        public File getClassesDirectory() {
            return classesDirectory;
        }

        public Log getLog() {
            return ModuleMojo.this.getLog();
        }

        public String getOutputFileNameMapping() {
            return outputFileNameMapping;
        }

        public File getModuleSourceDirectory() {
            return moduleSourceDirectory;
        }

        public String getModuleSourceTargetDirectory() {
            return moduleSourceTargetDirectory;
        }

        public String[] getModuleSourceIncludes() {
            return getIncludes();
        }

        public String[] getModuleSourceExcludes() {
            return getExcludes();
        }

        public boolean archiveClasses() {
            return archiveClasses;
        }

        public boolean isAttachClasses() {
            return attachClasses;
        }

        public ArchiverManager getArchiverManager() {
            return archiverManager;
        }

        public MavenArchiveConfiguration getArchive() {
            return archive;
        }

        public JarArchiver getJarArchiver() {
            return jarArchiver;
        }

        public List<?> getFilters() {
            return filters;
        }

        public ModuleStructure getModuleStructure() {
            return moduleStructure;
        }

        public MavenFileFilter getMavenFileFilter() {
            return mavenFileFilter;
        }

        public List<FileUtils.FilterWrapper> getFilterWrappers() {
            return filterWrappers;
        }

        public boolean isNonFilteredExtension(String fileName) {
            return !mavenResourcesFiltering.filteredFileExtension(fileName, nonFilteredFileExtensions);
        }

        public boolean isFilteringDeploymentDescriptors() {
            return filteringDeploymentDescriptors;
        }

        public ArtifactFactory getArtifactFactory() {
            return this.artifactFactory;
        }

        public ModuleResource[] getModuleResources() {
            return moduleResources;
        }

        public String getManifestEncoding() {
            return manifestEncoding;
        }

        public File getDescriptorsDirectory() {
            return descriptorsDir;
        }

        public void setDescriptorsDirectory(File directory) {
            setDescriptorsDirectory(directory);
        }

        public String getDescriptorsEncoding() {
            return descriptorsEncoding;
        }

        public boolean isDescriptorsN2AApply() {
            return descriptorsN2AApply;
        }

        public PlainEncodingConfig getDescriptorsN2AConfig() {
            return descriptorsN2AConfig;
        }

        public File getWorkDirectory() {
            return workDirectory;
        }

        public ModuleResource getModuleSourceResource() {
            ModuleResource defaultModuleLocation = new ModuleResource();
            defaultModuleLocation.setDirectory(moduleSourceDirectory.getAbsolutePath());
            defaultModuleLocation.setExcludes(new ArrayList<String>(Arrays.asList(getModuleSourceExcludes())));
            defaultModuleLocation.setFiltering(true);
            defaultModuleLocation.setIncludes(new ArrayList<String>(Arrays.asList(getModuleSourceIncludes())));
            defaultModuleLocation.setModuleTargetPath(moduleSourceTargetDirectory);
            defaultModuleLocation.setN2aApply(true);
            defaultModuleLocation.setSystemModule(true);
            return defaultModuleLocation;
        }

        public ModuleResource getClassesResource() {
            ModuleResource defaultModuleLocation = new ModuleResource();
            String targetDir = new File(moduleSourceTargetDirectory, "classes").getPath();

            defaultModuleLocation.setDirectory(classesDirectory.getAbsolutePath());
            defaultModuleLocation.setFiltering(false);
            defaultModuleLocation.setOpencmsTargetPath("classes");
            defaultModuleLocation.setModuleTargetPath(targetDir);
            defaultModuleLocation.setSystemModule(true);
            return defaultModuleLocation;
        }

        public ModuleResource getLibResource() {
            ModuleResource defaultModuleLocation = new ModuleResource();

            String targetDir = new File(moduleSourceTargetDirectory, "lib").getPath();
            String libDirectory;
            if (moduleSourceTargetDirectory != null) {
                libDirectory = new File(new File(getModuleDirectory(), moduleSourceTargetDirectory), "lib").getAbsolutePath();
            } else {
                libDirectory = new File(getModuleDirectory(), "lib").getAbsolutePath();
            }

            defaultModuleLocation.setDirectory(libDirectory);
            defaultModuleLocation.setFiltering(false);
            defaultModuleLocation.setOpencmsTargetPath("lib");
            defaultModuleLocation.setModuleTargetPath(targetDir);
            defaultModuleLocation.setSystemModule(true);
            return defaultModuleLocation;
        }
    }

	public void setPackagingIncludes(String packagingIncludes) {
		this.packagingIncludes = packagingIncludes;
	}

	public String getOutputDirectory() {
		return outputDirectory;
	}

	public void setOutputDirectory(String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public String getModuleName() {
		return moduleName;
	}

	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	public JarArchiver getModuleArchiver() {
		return moduleArchiver;
	}

	public void setModuleArchiver(JarArchiver zipArchiver) {
		this.moduleArchiver = zipArchiver;
	}

	public MavenProjectHelper getProjectHelper() {
		return projectHelper;
	}

	public void setProjectHelper(MavenProjectHelper projectHelper) {
		this.projectHelper = projectHelper;
	}

	public boolean isPrimaryArtifact() {
		return primaryArtifact;
	}

	public void setPrimaryArtifact(boolean primaryArtifact) {
		this.primaryArtifact = primaryArtifact;
	}

	public String getClassesClassifier() {
		return classesClassifier;
	}

	public void setClassesClassifier(String classesClassifier) {
		this.classesClassifier = classesClassifier;
	}

	public boolean isFailOnMissingManifestXml() {
		return failOnMissingManifestXml;
	}

	public void setFailOnMissingManifestXml(boolean failOnMissingWebXml) {
		this.failOnMissingManifestXml = failOnMissingWebXml;
	}
}
