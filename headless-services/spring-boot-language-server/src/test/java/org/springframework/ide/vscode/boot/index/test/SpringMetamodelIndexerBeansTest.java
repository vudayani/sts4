/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringMetamodelIndexerBeansTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void testSpringIndexExists() throws Exception {
		assertNotNull(springIndex);
	}

	@Test
	void testBeansNameAndTypeFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "bean1");

		assertEquals(1, beans.length);
		assertEquals("bean1", beans[0].getName());
		assertEquals("org.test.BeanClass1", beans[0].getType());
	}

	@Test
	void testBeansDefintionLocationFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "bean1");

		String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
		Location location = new Location(docUri, new Range(new Position(15, 1), new Position(15, 6)));
		assertEquals(location, beans[0].getLocation());
	}

	@Test
	void testBeansNameAndTypeFromComponentAnnotatedClassExists() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "constructorInjectionService");

		assertEquals(1, beans.length);
		assertEquals("constructorInjectionService", beans[0].getName());
		assertEquals("org.test.injections.ConstructorInjectionService", beans[0].getType());
	}

	@Test
	void testBeansDefintionLocationFromComponentAnnotatedClass() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "constructorInjectionService");

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConstructorInjectionService.java").toUri().toString();
		Location location = new Location(docUri, new Range(new Position(6, 0), new Position(6, 8)));
		assertEquals(location, beans[0].getLocation());
	}

	@Test
	void testBeansNameAndTypeFromConfigurationAnnotatedClassExists() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "configurationWithoutInjection");

		assertEquals(1, beans.length);
		assertEquals("configurationWithoutInjection", beans[0].getName());
		assertEquals("org.test.injections.ConfigurationWithoutInjection", beans[0].getType());
	}

	@Test
	void testBeansDefinitionLocationFromConfigurationAnnotatedClass() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "configurationWithoutInjection");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithoutInjection.java").toUri().toString();
		assertEquals(docUri, beans[0].getLocation().getUri());
	}
	
	@Test
	void testBeanNoInjectionPointsFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithoutInjections");
		assertEquals(1, beans.length);

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(0, injectionPoints.length);
		assertSame(DefaultValues.EMPTY_INJECTION_POINTS, injectionPoints);
	}
	
	@Test
	void testBeanInjectionPointsFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "manualBeanWithConstructor");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithInjections.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		Location ip1Location = new Location(docUri, new Range(new Position(12, 73), new Position(12, 78)));
		assertEquals(ip1Location, injectionPoints[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		Location ip2Location = new Location(docUri, new Range(new Position(12, 91), new Position(12, 96)));
		assertEquals(ip2Location, injectionPoints[1].getLocation());
	}

	@Test
	void testBeanInjectionPointsFromConstructor() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "constructorInjectionService");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConstructorInjectionService.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		Location ip1Location = new Location(docUri, new Range(new Position(12, 47), new Position(12, 52)));
		assertEquals(ip1Location, injectionPoints[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		Location ip2Location = new Location(docUri, new Range(new Position(12, 65), new Position(12, 70)));
		assertEquals(ip2Location, injectionPoints[1].getLocation());
	}

	@Test
	void testBeanInjectionPointsFromAutowiredFields() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "autowiredInjectionService");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/AutowiredInjectionService.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		Location ip1Location = new Location(docUri, new Range(new Position(12, 20), new Position(12, 25)));
		assertEquals(ip1Location, injectionPoints[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		Location ip2Location = new Location(docUri, new Range(new Position(16, 20), new Position(16, 25)));
		assertEquals(ip2Location, injectionPoints[1].getLocation());
	}
	
	@Test
	void testBeanFromSpringDataRepository() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "customerRepository");

		assertEquals(1, beans.length);
		Bean repositoryBean = beans[0];

		assertEquals("customerRepository", repositoryBean.getName());
		assertEquals("org.test.springdata.CustomerRepository", repositoryBean.getType());
		
		assertTrue(repositoryBean.isTypeCompatibleWith("org.test.springdata.CustomerRepository"));
		assertTrue(repositoryBean.isTypeCompatibleWith("org.springframework.data.repository.CrudRepository"));

		InjectionPoint[] injectionPoints = repositoryBean.getInjectionPoints();
		assertEquals(0, injectionPoints.length);
		assertSame(DefaultValues.EMPTY_INJECTION_POINTS, injectionPoints);
	}

	@Test
	void testBeansWithSupertypes() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithSupertypes");
		assertEquals(1, beans.length);
		
		assertTrue(beans[0].isTypeCompatibleWith("java.lang.Object"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.AbstractBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.Interface1OfBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.Interface2OfBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.InterfaceOfAbstractBean"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.BaseClassOfAbstractBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.BeanWithSupertypes"));
		
		assertFalse(beans[0].isTypeCompatibleWith("java.lang.String"));
		assertFalse(beans[0].isTypeCompatibleWith("java.util.Comparator"));
	}
	
	@Test
	void testAnnotationMetadataFromComponentBeans() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "mainClass");
		assertEquals(1, beans.length);
		
		Bean mainClassBean = beans[0];
		AnnotationMetadata[] annotations = mainClassBean.getAnnotations();
		
		assertEquals(4, annotations.length);
		assertEquals("org.springframework.boot.autoconfigure.SpringBootApplication", annotations[0].getAnnotationType());
		assertFalse(annotations[0].isMetaAnnotation());
		
		assertEquals("org.springframework.boot.SpringBootConfiguration", annotations[1].getAnnotationType());
		assertTrue(annotations[1].isMetaAnnotation());

		assertEquals("org.springframework.context.annotation.Configuration", annotations[2].getAnnotationType());
		assertTrue(annotations[2].isMetaAnnotation());

		assertEquals("org.springframework.stereotype.Component", annotations[3].getAnnotationType());
		assertTrue(annotations[3].isMetaAnnotation());
	}

	@Test
	void testAnnotationMetadataFromBeanMethodBean() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "bean3");
		assertEquals(1, beans.length);
		
		Bean mainClassBean = beans[0];
		AnnotationMetadata[] annotations = mainClassBean.getAnnotations();
		
		assertEquals(3, annotations.length);
		
		AnnotationMetadata beanAnnotation = annotations[0];
		assertEquals("org.springframework.context.annotation.Bean", beanAnnotation.getAnnotationType());
		assertFalse(annotations[0].isMetaAnnotation());
		assertEquals(0, annotations[0].getAttributes().size());
		
		AnnotationMetadata qualifierAnnotation = annotations[1];
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierAnnotation.getAnnotationType());
		Map<String, String[]> attributes = qualifierAnnotation.getAttributes();
		assertEquals(1, attributes.size());
		assertTrue(attributes.containsKey("value"));
		assertArrayEquals(new String[] {"qualifier1"}, attributes.get("value"));

		AnnotationMetadata profileAnnotation = annotations[2];
		assertEquals("org.springframework.context.annotation.Profile", profileAnnotation.getAnnotationType());
		assertFalse(profileAnnotation.isMetaAnnotation());
		
		attributes = profileAnnotation.getAttributes();
		assertEquals(1, attributes.size());
		assertTrue(attributes.containsKey("value"));
		assertArrayEquals(new String[] {"testprofile","testprofile2"}, attributes.get("value"));
	}
	
	@Test
	void testAnnotationMetadataFromBeanMethodWithInjectionPointAnnotations() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithAnnotationsOnInjectionPoints");
		assertEquals(1, beans.length);
		
		Bean bean = beans[0];
		AnnotationMetadata[] annotations = bean.getAnnotations();
		
		assertEquals(2, annotations.length);
		
		AnnotationMetadata beanAnnotation = annotations[0];
		AnnotationMetadata dependsonAnnotation = annotations[1];

		assertEquals("org.springframework.context.annotation.Bean", beanAnnotation.getAnnotationType());
		assertEquals("org.springframework.context.annotation.DependsOn", dependsonAnnotation.getAnnotationType());
		
		Map<String, String[]> dependsOnAttributes = dependsonAnnotation.getAttributes();
		assertEquals(1, dependsOnAttributes.size());
		assertArrayEquals(new String[] {"bean1", "bean2"}, dependsOnAttributes.get("value"));
		
		InjectionPoint[] injectionPoints = bean.getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		AnnotationMetadata[] annotationsFromPoint1 = injectionPoints[0].getAnnotations();
		AnnotationMetadata[] annotationsFromPoint2 = injectionPoints[1].getAnnotations();
		
		assertEquals(1, annotationsFromPoint1.length);
		assertEquals(1, annotationsFromPoint2.length);
		
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", annotationsFromPoint1[0].getAnnotationType());
		Map<String, String[]> attributesFromParam1 = annotationsFromPoint1[0].getAttributes();
		assertEquals(1, attributesFromParam1.size());
		assertArrayEquals(new String[] {"q1"}, attributesFromParam1.get("value"));
		
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", annotationsFromPoint2[0].getAnnotationType());
		Map<String, String[]> attributesFromParam2 = annotationsFromPoint2[0].getAttributes();
		assertEquals(1, attributesFromParam2.size());
		assertArrayEquals(new String[] {"q2"}, attributesFromParam2.get("value"));
	}
	
	@Test
	void testAnnotationMetadataFromComponentClass() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "configurationWithInjectionsAndAnnotations");
		assertEquals(1, beans.length);
		
		Bean bean = beans[0];

		InjectionPoint[] injectionPoints = bean.getInjectionPoints();
		assertEquals(0, injectionPoints.length);
		
		AnnotationMetadata[] annotations = bean.getAnnotations();
		
		assertEquals(4, annotations.length);
		
		AnnotationMetadata configurationAnnotation= annotations[0];
		AnnotationMetadata qualifierAnnotation = annotations[1];
		AnnotationMetadata runtimeHintsAnnotation = annotations[2];
		AnnotationMetadata componentMetaAnnotation = annotations[3];

		assertEquals("org.springframework.context.annotation.Configuration", configurationAnnotation.getAnnotationType());
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierAnnotation.getAnnotationType());
		assertEquals("org.springframework.context.annotation.ImportRuntimeHints", runtimeHintsAnnotation.getAnnotationType());
		assertEquals("org.springframework.stereotype.Component", componentMetaAnnotation.getAnnotationType());
		
		Map<String, String[]> qualifierAttributes = qualifierAnnotation.getAttributes();
		assertEquals(1, qualifierAttributes.size());
		assertArrayEquals(new String[] {"qualifier"}, qualifierAttributes.get("value"));
		
		Map<String, String[]> runtimeHintsAttributes = runtimeHintsAnnotation.getAttributes();
		assertEquals(1, runtimeHintsAttributes.size());
		assertArrayEquals(new String[] {"org.test.injections.DummyRuntimeHintsRegistrar"}, runtimeHintsAttributes.get("value"));
	}
	
	@Test
	void testAnnotationMetadataFromInjectionPointsFromAutowiredFields() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "autowiredInjectionService");
		assertEquals(1, beans.length);

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		AnnotationMetadata[] annotationsPoint1 = injectionPoints[0].getAnnotations();
		assertEquals(1, annotationsPoint1.length);
		assertEquals("org.springframework.beans.factory.annotation.Autowired", annotationsPoint1[0].getAnnotationType());
		assertFalse(annotationsPoint1[0].isMetaAnnotation());
		assertEquals(0, annotationsPoint1[0].getAttributes().size());

		AnnotationMetadata[] annotationsPoint2 = injectionPoints[1].getAnnotations();
		assertEquals(2, annotationsPoint2.length);
		
		AnnotationMetadata autowiredFromPoint2 = annotationsPoint2[0];
		assertEquals("org.springframework.beans.factory.annotation.Autowired", autowiredFromPoint2.getAnnotationType());
		assertFalse(autowiredFromPoint2.isMetaAnnotation());
		assertEquals(0, autowiredFromPoint2.getAttributes().size());

		AnnotationMetadata qualifierFromPoint2 = annotationsPoint2[1];
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierFromPoint2.getAnnotationType());
		assertFalse(qualifierFromPoint2.isMetaAnnotation());
		assertEquals(1, qualifierFromPoint2.getAttributes().size());
		
		Map<String, String[]> qualifierAttributes = qualifierFromPoint2.getAttributes();
		assertEquals(1, qualifierAttributes.size());
		assertArrayEquals(new String[] {"qual1"}, qualifierAttributes.get("value"));
	}
	
	@Test
	void testAnnotationMetadataFromSpringDataRepository() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "customerRepository");

		assertEquals(1, beans.length);
		
		AnnotationMetadata[] annotations = beans[0].getAnnotations();
		assertEquals(2, annotations.length);
		
		AnnotationMetadata qualifierAnnotation = annotations[0];
		AnnotationMetadata profileAnnotation = annotations[1];
		
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierAnnotation.getAnnotationType());
		assertFalse(qualifierAnnotation.isMetaAnnotation());
		
		Map<String, String[]> qualifierAttributes = qualifierAnnotation.getAttributes();
		assertEquals(1, qualifierAttributes.size());
		assertArrayEquals(new String[] {"repoQualifier"}, qualifierAttributes.get("value"));
		
		assertEquals("org.springframework.context.annotation.Profile", profileAnnotation.getAnnotationType());
		assertFalse(profileAnnotation.isMetaAnnotation());
		
		Map<String, String[]> profileAttributes = profileAnnotation.getAttributes();
		assertEquals(1, profileAttributes.size());
		assertArrayEquals(new String[] {"prof1", "prof2"}, profileAttributes.get("value"));
	}


	
}
