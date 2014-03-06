package com.camomileware.maven.plugin.opencms;

import java.io.File;
import java.util.List;

/**
 * Configuration Bean for Native2Asscii task
 * 
 * @author jagarcia
 *
 */
public class PlainEncodingConfig {

	public File _src;
	public File _dest;
	
	/**
	 * File extension to use in renaming output files
	 */
	private String ext;
	/**
	 * The native encoding the files are in (default is the default encoding for the JVM)
	 */
	private String encoding;
	/**
	 * list of patterns of files that must be included. All files are included when omitted
	 */
	private List<String> includes;
	/**
	 * list of patterns of files that must be excluded. No files (except default excludes) are excluded when omitted.
	 */
	private List<String> excludes;


	public PlainEncodingConfig() {
	}


	public String getExt() {
		return ext;
	}

	public void setExt(String ext) {
		this.ext = ext;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public List<String> getIncludes() {
		return includes;
	}

	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}


	public List<String> getExcludes() {
		return excludes;
	}


	public void setExcludes(List<String> excludes) {
		this.excludes = excludes;
	}
}
