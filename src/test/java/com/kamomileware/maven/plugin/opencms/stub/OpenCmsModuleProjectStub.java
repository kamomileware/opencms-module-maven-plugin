package com.kamomileware.maven.plugin.opencms.stub;

import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

public class OpenCmsModuleProjectStub extends MavenProjectStub {
	/**
	 * Default constructor
	 */
	public OpenCmsModuleProjectStub() {
	}

	/** {@inheritDoc} */
	public List<ArtifactRepository> getRemoteArtifactRepositories() {
		ArtifactRepository repository = new DefaultArtifactRepository(
				"central", "http://repo.maven.apache.org/maven2",
				new DefaultRepositoryLayout());

		return Collections.singletonList(repository);
	}
}