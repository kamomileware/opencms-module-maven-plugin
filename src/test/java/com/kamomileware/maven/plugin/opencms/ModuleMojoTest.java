package com.kamomileware.maven.plugin.opencms;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import com.kamomileware.maven.plugin.opencms.ModuleMojo;

public class ModuleMojoTest extends AbstractMojoTestCase {

	/** {@inheritDoc} */
	protected void setUp() throws Exception {
		// required
		super.setUp();

	}

	/** {@inheritDoc} */
	protected void tearDown() throws Exception {
		// required
		super.tearDown();
	}

	/**
	 * @throws Exception
	 *             if any
	 */
	public void testSomething() throws Exception {
		File pom = getTestFile("src/test/resources/unit/module/first_test.xml");
		assertNotNull(pom);
		assertTrue(pom.exists());

		ModuleMojo myMojo = (ModuleMojo) lookupMojo("module", pom);
		assertNotNull(myMojo);
		myMojo.execute();
	}
}
