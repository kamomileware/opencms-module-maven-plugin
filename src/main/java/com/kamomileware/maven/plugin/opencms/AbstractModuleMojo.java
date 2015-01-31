package com.kamomileware.maven.plugin.opencms;

import com.kamomileware.maven.plugin.opencms.util.ModuleStructureSerializer;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base class for opencms-module maven plugin mojos.
 * Contains references to maven components and configurations.
 *
 * @author jagarcia
 */
@SuppressWarnings("deprecation")
public abstract class AbstractModuleMojo extends AbstractMojo {

  public static final String DEFAULT_FILE_NAME_MAPPING = "@{artifactId}@-@{version}@.@{extension}@";
  public static final String DEFAULT_FILE_NAME_MAPPING_CLASSIFIER = "@{artifactId}@-@{version}@-@{classifier}@.@{extension}@";
  public static final String PACKAGING_OPENCMS_MODULE = "opencms-module";
  protected static final String[] EMPTY_STRING_ARRAY = {};
  protected static final String MANIFEST_NAME = "manifest.xml";
  protected final ModuleStructureSerializer moduleStructureSerialier = new ModuleStructureSerializer();

  /**
   * The maven project.
   */
  @Parameter (defaultValue="${project}", readonly = true)
  protected MavenProject project;

  /**
   * The directory containing generated classes.
   */
  @Parameter (property="project.build.outputDirectory", readonly = true, required = true)
  protected File classesDirectory;

  /**
   * Whether classes (module/classes or lib directory) should be attached to
   * the project.
   */
  @Parameter (defaultValue = "true")
  protected boolean attachClasses = true;

  /**
   * Whether a JAR file will be created for the classes in the module. Using
   * this optional configuration parameter will make the generated classes to
   * be archived into a jar file and the classes directory will then be
   * excluded from the module.
   */
  @Parameter (property="archiveClasses", defaultValue = "false")
  protected boolean archiveClasses;

  /**
   * The Jar archiver needed for archiving classes directory into jar file
   * under WEB-INF/lib.
   */
  @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint="jar")
  protected JarArchiver jarArchiver;

  /**
   * The directory where the module is built.
   */
  @Parameter(defaultValue="${project.build.directory}/${project.build.finalName}", required = true)
  protected File moduleDirectory;

  /**
   * Single directory for extra files to include in the module.
   */
  @Parameter (defaultValue = "${basedir}/src/main/module", required = true)
  protected File moduleSourceDirectory;

  /**
   * Relative path where source directory will be placed in the module.
   */
  @Parameter
  protected String moduleSourceTargetDirectory;

  /**
   * The list of webResources we want to transfer.
   */
  @Parameter
  protected ModuleResource[] moduleResources;

  /**
   * Filters (property files) to include during the interpolation of the
   * pom.xml.
   */
  @Parameter
  protected List<?> filters;

  /**
   * The path to the web.xml file to use.
   */
  @Parameter (property="maven.module.manifestxml")
  protected File manifestXml;

  /**
   * Whether to generate the manifest if it doent exist
   */
  @Parameter (property="generate.manifest.xml", defaultValue="true")
  protected boolean generateManifestXml = true;

  /**
   * Charset to encode the generated module manifest;
   */
  @Parameter (property="manifest.encoding")
  protected String manifestEncoding;

  /**
   * Folder for storing manifest descriptors
   */
  @Parameter (property="module.descriptors.dir", defaultValue="${basedir}/src/main/manifest")
  protected File descriptorsDir;

  /**
   * Manifest descriptors encoding
   */
  @Parameter (property="module.descriptors.encoding", defaultValue="default")
  protected String descriptorsEncoding;

  /**
   * Whether native to ascii apply
   */
  @Parameter (property = "module.descriptors.n2aapply", defaultValue = "false")
  protected boolean descriptorsN2AApply;

  /**
   *
   */
  @Parameter
  protected PlainEncodingConfig descriptorsN2AConfig;

  /**
   * Directory to copy conversed to native resources into if needed
   */
  @Parameter (defaultValue = "${project.build.directory}/module/work", required = true)
  protected File workDirectory;

  /**
   * The file name mapping to use to copy libraries and tlds. If no file
   * mapping is set (default) the file is copied with its standard name.
   */
  @Parameter
  protected String outputFileNameMapping;

  /**
   * The file containing the module structure cache.
   */
  @Parameter (defaultValue="${project.build.directory}/module/work/webapp-cache.xml", required = true)
  protected File cacheFile;

  /**
   * Whether the cache should be used to save the status of the module accross
   * multiple runs.
   */
  @Parameter (property="useCache", defaultValue="true")
  protected boolean useCache = true;

  /**
   *
   */
  @Component (role=org.apache.maven.artifact.factory.ArtifactFactory.class)
  protected ArtifactFactory artifactFactory;

  /**
   * To look up Archiver/UnArchiver implementations
   */
  @Component (role=org.codehaus.plexus.archiver.manager.ArchiverManager.class)
  protected ArchiverManager archiverManager;

  /**
   *
   */
  @Component (role=org.apache.maven.shared.filtering.MavenFileFilter.class, hint = "default")
  protected MavenFileFilter mavenFileFilter;

  /**
   *
   */
  @Component (role=org.apache.maven.shared.filtering.MavenResourcesFiltering.class,hint = "default")
  protected MavenResourcesFiltering mavenResourcesFiltering;

  /**
   * The comma separated list of tokens to include when copying content of the
   * moduleSourceDirectory. Default is '**'.
   */
  @Parameter (alias="includes")
  protected String moduleSourceIncludes = "**";

  /**
   * The comma separated list of tokens to exclude when copying content of the
   * moduleSourceDirectory.
   */
  @Parameter (alias="excludes")
  protected String moduleSourceExcludes;

  /**
   * The comma separated list of tokens to exclude when copying content of the
   * moduleSourceDirectory.
   */
  @Parameter (alias="encoding")
  protected String moduleSourceEncoding;

  /**
   * A list of file extensions to not filtering. <b>will be used for
   * webResources and overlay filtering</b>
   */
  @Parameter
  protected List<String> nonFilteredFileExtensions;

  /**
   *
   */
  @Parameter (defaultValue="${session}", readonly = true, required = true)
  protected MavenSession session;

  /**
   * To filtering deployment descriptors <b>disabled by default</b>
   */
  @Parameter (property="maven.module.filteringManifestDescriptors", defaultValue = "false")
  protected boolean filteringDeploymentDescriptors = false;

  /**
   * To escape interpolated value with windows path c:\foo\bar will be
   * replaced with c:\\foo\\bar
   */
  @Parameter (property="maven.module.escapedBackslashesInFilePath", defaultValue = "false")
  protected boolean escapedBackslashesInFilePath = false;

  /**
   * Expression preceded with the String won't be interpolated \${foo} will be
   * replaced with ${foo}
   */
  @Parameter (property="maven.module.escapeString")
  protected String escapeString;

  /**
   * The archive configuration to use. See <a
   * href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
   * Archiver Reference</a>.
   */
  @Parameter
  protected MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

  /**
   *
   */
  @Parameter (defaultValue="${settings}", readonly = true)
  protected Settings settings;

  /**
   *
   */
  @Parameter (property="dryrun", defaultValue="false")
  protected boolean dryRun = false;

  /**
   * Name of the webapp aplication name for OpenCms.
   */
  @Parameter (property="opencms.webapp.name", defaultValue="")
  protected String openCmsWebappName;

  /**
   * Returns a string array of the excludes to be used when copying the
   * content of the module source directory.
   *
   * @return an array of tokens to exclude
   */
  protected String[] getExcludes() {
    List<String> excludeList = new ArrayList<String>();
    if (StringUtils.isNotEmpty(moduleSourceExcludes)) {
      excludeList.addAll(Arrays.asList(StringUtils.split(moduleSourceExcludes, ",")));
    }

    // if webXML is specified, omit the one in the source directory
    if (manifestXml != null && StringUtils.isNotEmpty(manifestXml.getName())) {
      excludeList.add("**/" + MANIFEST_NAME);
    }

    return (String[]) excludeList.toArray(EMPTY_STRING_ARRAY);
  }

  /**
   * Returns a string array of the includes to be used when assembling/copying
   * the module.
   *
   * @return an array of tokens to include
   */
  protected String[] getIncludes() {
    return StringUtils.split(StringUtils.defaultString(moduleSourceIncludes), ",");
  }

  /**
   * @param serverId
   * @return
   */
  protected Server getCredentialsFromServer(String serverId) {
    Server serverSettings = getSettings() == null ? null : getSettings().getServer(serverId);

    if (serverSettings != null) {
      getLog().info("Using authentication information for server: '" + serverId + "'.");
    } else {
      getLog().warn("Server authentication entry not found for: '" + serverId + "'.");
    }
    return serverSettings;
  }


  public MavenProject getProject() {
    return project;
  }

  public void setProject(MavenProject project) {
    this.project = project;
  }

  public File getClassesDirectory() {
    return classesDirectory;
  }

  public void setClassesDirectory(File classesDirectory) {
    this.classesDirectory = classesDirectory;
  }

  public File getModuleDirectory() {
    return moduleDirectory;
  }

  public void setModuleDirectory(File moduleDirectory) {
    this.moduleDirectory = moduleDirectory;
  }

  public File getModuleSourceDirectory() {
    return moduleSourceDirectory;
  }

  public void setModuleSourceDirectory(File moduleSourceDirectory) {
    this.moduleSourceDirectory = moduleSourceDirectory;
  }

  public String getModuleSourceTargetDirectory() {
    return moduleSourceTargetDirectory;
  }

  public void setModuleSourceTargetDirectory(String moduleSourceTargetDirectory) {
    if (moduleSourceTargetDirectory != null && moduleSourceTargetDirectory.contains("\\")) {
      moduleSourceTargetDirectory = moduleSourceTargetDirectory.replace('\\', '/');
    }

    this.moduleSourceTargetDirectory = moduleSourceTargetDirectory;
  }

  public File getManifestXml() {
    return manifestXml;
  }

  public void setManifestXml(File manifestXml) {
    this.manifestXml = manifestXml;
  }

  public String getDescriptorsEncoding() {
    return descriptorsEncoding;
  }

  public void setDescriptorsEncoding(String descriptorsEncoding) {
    this.descriptorsEncoding = descriptorsEncoding;
  }

  public boolean isDescriptorsN2AApply() {
    return descriptorsN2AApply;
  }

  public void setDescriptorsN2AApply(boolean descriptorsN2AApply) {
    this.descriptorsN2AApply = descriptorsN2AApply;
  }

  public PlainEncodingConfig getDescriptorsN2AConfig() {
    return descriptorsN2AConfig;
  }

  public void setDescriptorsN2AConfig(PlainEncodingConfig descriptorsN2AConfig) {
    this.descriptorsN2AConfig = descriptorsN2AConfig;
  }

  public boolean isGenerateManifestXml() {
    return generateManifestXml;
  }

  public void setGenerateManifestXml(boolean generateManifestXml) {
    this.generateManifestXml = generateManifestXml;
  }

  public String getOutputFileNameMapping() {
    return outputFileNameMapping;
  }

  public void setOutputFileNameMapping(String outputFileNameMapping) {
    this.outputFileNameMapping = outputFileNameMapping;
  }

  public boolean isAttachClasses() {
    return attachClasses;
  }

  public void setAttachClasses(boolean attachClasses) {
    this.attachClasses = attachClasses;
  }

  public boolean isArchiveClasses() {
    return archiveClasses;
  }

  public void setArchiveClasses(boolean archiveClasses) {
    this.archiveClasses = archiveClasses;
  }

  public JarArchiver getJarArchiver() {
    return jarArchiver;
  }

  public void setJarArchiver(JarArchiver jarArchiver) {
    this.jarArchiver = jarArchiver;
  }

  public ModuleResource[] getModuleResources() {
    return moduleResources;
  }

  public void setModulebResources(ModuleResource[] moduleResources) {
    this.moduleResources = moduleResources;
  }

  public List<?> getFilters() {
    return filters;
  }

  public void setFilters(List<?> filters) {
    this.filters = filters;
  }

  public File getWorkDirectory() {
    return workDirectory;
  }

  public void setWorkDirectory(File workDirectory) {
    this.workDirectory = workDirectory;
  }

  public String getModuleSourceIncludes() {
    return moduleSourceIncludes;
  }

  public void setModuleSourceIncludes(String moduleSourceIncludes) {
    this.moduleSourceIncludes = moduleSourceIncludes;
  }

  public String getModuleSourceExcludes() {
    return moduleSourceExcludes;
  }

  public void setModuleSourceExcludes(String moduleSourceExcludes) {
    this.moduleSourceExcludes = moduleSourceExcludes;
  }

  public String getModuleSourceEncoding() {
    return moduleSourceEncoding;
  }

  public String getEncoding() {
    return this.moduleSourceEncoding;
  }

  public MavenArchiveConfiguration getArchive() {
    return archive;
  }

  public List<String> getNonFilteredFileExtensions() {
    return nonFilteredFileExtensions;
  }

  public void setNonFilteredFileExtensions(List<String> nonFilteredFileExtensions) {
    this.nonFilteredFileExtensions = nonFilteredFileExtensions;
  }

  public ArtifactFactory getArtifactFactory() {
    return this.artifactFactory;
  }

  public void setArtifactFactory(ArtifactFactory artifactFactory) {
    this.artifactFactory = artifactFactory;
  }

  public Settings getSettings() {
    return settings;
  }

  public void setSettings(Settings settings) {
    this.settings = settings;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }
}
