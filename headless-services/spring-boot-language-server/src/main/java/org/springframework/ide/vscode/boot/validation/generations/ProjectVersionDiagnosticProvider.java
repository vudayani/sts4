/*******************************************************************************
 * Copyright (c) 2022 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.io.File;
import java.net.URI;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.openrewrite.java.tree.J.If;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.java.Version;

import com.google.common.collect.ImmutableList;

public class ProjectVersionDiagnosticProvider {

	public static final String BOOT_VERSION_VALIDATION_CODE = "BOOT_VERSION_VALIDATION_CODE";
	private static final String[] BUILD_FILES = new String[] { "pom.xml", "build.gradle", "build.gradle.kts" };
	private static final String SPRING_BOOT_PROJECT_SLUG = "spring-boot";

	private final SpringProjectsProvider provider;
	private final VersionValidators validators;

	public ProjectVersionDiagnosticProvider(SpringProjectsProvider provider, VersionValidators validationConditions) {
		this.provider = provider;
		this.validators = validationConditions;
	}

	/**
	 * 
	 * @return Non-null list of Diagnostics for the given Java project. Can be empty if no diagnostics are
	 *         applicable.
	 * @throws If error encountered while getting diagnostics
	 */
	public List<SpringProjectDiagnostic> getDiagnostics(IJavaProject javaProject) throws Exception {

		URI uri = getBuildFileUri(javaProject);
		if (uri == null) {
			return ImmutableList.of();
		}

		ResolvedSpringProject springProject = provider.getProject(SPRING_BOOT_PROJECT_SLUG);
		Version javaProjectVersion = SpringProjectUtil.getDependencyVersion(javaProject, springProject.getSlug());

		if (javaProjectVersion == null) {
			throw new Exception("Unable to resolve version for project: " + javaProject.getLocationUri().toString());
		}

		Generation javaProjectGeneration = getGenerationForJavaProject(javaProject, springProject);

		if (javaProjectGeneration == null) {
			throw new Exception("Unable to find Spring Project Generation for project: " + javaProjectVersion.toString());
		}

		VersionValidation validation = null;

		for (VersionValidator validator : validators.getValidators()) {
			if (validator.isEnabled()) {
				validation = validator.getValidation(springProject, javaProjectGeneration, javaProjectVersion);
				if (validation != null) {
					break;
				}
			}
		}

		if (validation != null) {

			DiagnosticSeverity severity = validation.getSeverity();
			Version toUpgrade = validation.getVersionToUprade();

			StringBuffer msg = new StringBuffer();
			msg.append(validation.getMessage());
			
			if (toUpgrade != null) {
				msg.append('\n');
				msg.append("Consider upgrading to a newer supported version: ");
				msg.append(toUpgrade.toString());
			}

			Diagnostic diagnostic = new Diagnostic();
			diagnostic.setCode(BOOT_VERSION_VALIDATION_CODE);
			diagnostic.setMessage(msg.toString());

			Range range = new Range();
			Position start = new Position();
			start.setLine(0);
			start.setCharacter(0);
			range.setStart(start);
			Position end = new Position();
			end.setLine(0);
			end.setCharacter(1);
			range.setEnd(end);
			diagnostic.setRange(range);
			diagnostic.setSeverity(severity);

			setQuickfix(diagnostic);

			return ImmutableList.of(new SpringProjectDiagnostic(diagnostic, uri));

		}

		return ImmutableList.of();
	}

	private Generation getGenerationForJavaProject(IJavaProject javaProject, ResolvedSpringProject springProject)
			throws Exception {
		List<Generation> genList = springProject.getGenerations();
		Version javaProjectVersion = SpringProjectUtil.getDependencyVersion(javaProject, springProject.getSlug());

		// Find the generation belonging to the dependency
		for (Generation gen : genList) {
			Version genVersion = getVersion(gen);
			if (genVersion.getMajor() == javaProjectVersion.getMajor()
					&& genVersion.getMinor() == javaProjectVersion.getMinor()) {
				return gen;
			}
		}
		return null;
	}

	private void setQuickfix(Diagnostic diagnostic) {
		// TODO: Fix this when open rewrite recipe quickfix becomes available.
		Diagnostic refDiagnostic = new Diagnostic(diagnostic.getRange(), diagnostic.getMessage(),
				diagnostic.getSeverity(), diagnostic.getSource());
		CodeAction ca = new CodeAction();
		ca.setKind(CodeActionKind.QuickFix);
		ca.setTitle("Validation FIX");
		ca.setDiagnostics(List.of(refDiagnostic));
		String commandId = "";
		ca.setCommand(new Command("Validation FIX", commandId, ImmutableList.of()));
		diagnostic.setData(ca);
	}

	protected URI getBuildFileUri(IJavaProject javaProject) throws Exception {

		File file = null;
		for (String fileName : BUILD_FILES) {
			file = SpringProjectUtil.getFile(javaProject, fileName);
			if (file != null) {
				return file.toURI();
			}
		}

		return null;
	}

	protected File getSpringBootDependency(IJavaProject project) {
		List<File> libs = SpringProjectUtil.getLibrariesOnClasspath(project, "spring-boot");
		return libs != null && libs.size() > 0 ? libs.get(0) : null;
	}

	protected Version getVersion(Generation generation) throws Exception {
		return SpringProjectUtil.getVersionFromGeneration(generation.getName());
	}
}
