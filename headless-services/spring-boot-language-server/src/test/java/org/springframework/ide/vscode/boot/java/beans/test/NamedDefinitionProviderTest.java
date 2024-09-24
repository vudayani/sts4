/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class NamedDefinitionProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;
	private Bean bean1;
	private Bean bean2;

	private String tempJavaDocUri1;
	private String tempJavaDocUri2;
	private String tempJavaDocUri;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-symbols-for-jakarta-javax/").toURI());

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);

        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TestDependsOnClass.java").toUri().toString();
        tempJavaDocUri1 = directory.toPath().resolve("src/main/java/org/test/TempClass1.java").toUri().toString();
        tempJavaDocUri2 = directory.toPath().resolve("src/main/java/org/test/TempClass2.java").toUri().toString();

        bean1 = new Bean("bean1", "type1", new Location(tempJavaDocUri1, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {});
		bean2 = new Bean("bean2", "type2", new Location(tempJavaDocUri2, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {});
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
	}
	
	@Test
	public void testNamedClassRefersToBeanDefinitionLink() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import jakarta.inject.Named;

				@Named("bean1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

		LocationLink expectedLocation = new LocationLink(tempJavaDocUri1,
				beans[0].getLocation().getRange(), beans[0].getLocation().getRange(),
				null);

		editor.assertLinkTargets("bean1", List.of(expectedLocation));
	}

	@Test
	public void testNamedDependencyRefersToBeanDefinitionLink() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import jakarta.inject.Named;

				@Component
				public class TestDependsOnClass {
				
					public void setDependency(@Named("bean1") Object bean) {
					}
				
				}""", tempJavaDocUri);
		
        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

		LocationLink expectedLocation = new LocationLink(tempJavaDocUri1,
				beans[0].getLocation().getRange(), beans[0].getLocation().getRange(),
				null);

		editor.assertLinkTargets("bean1", List.of(expectedLocation));
	}

	@Test
	public void testNamedDependencyRefersToNonExistingBeanDefinitionLink() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import jakarta.inject.Named;

				@Component
				public class TestDependsOnClass {
				
					public void setDependency(@Named("XXX") Object bean1) {
					}
				
				}""", tempJavaDocUri1);
		
		editor.assertNoLinkTargets("XXX");
	}

}
