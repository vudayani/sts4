/*******************************************************************************
 * Copyright (c) 2022, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.rewrite;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.openrewrite.Parser.Input;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.internal.RecipeIntrospectionUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.IndefiniteProgressTask;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixEdit;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixHandler;
import org.springframework.ide.vscode.commons.languageserver.util.CodeActionResolver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.rewrite.java.ORAstUtils;
import org.springframework.ide.vscode.commons.rewrite.java.RangeScopedRecipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

public class RewriteRefactorings implements CodeActionResolver, QuickfixHandler {
	
	public static final String REWRITE_RECIPE_QUICKFIX = "org.openrewrite.rewrite";
		
	private static final Logger log = LoggerFactory.getLogger(RewriteRefactorings.class);
	
	private RewriteRecipeRepository recipeRepo;
	
	private SimpleLanguageServer server;

	private JavaProjectFinder projectFinder;

	private Gson gson;
		
	public RewriteRefactorings(SimpleLanguageServer server, JavaProjectFinder projectFinder, RewriteRecipeRepository recipeRepo) {
		this.server = server;
		this.projectFinder = projectFinder;
		this.recipeRepo = recipeRepo;
		this.gson = new GsonBuilder()
				.registerTypeAdapter(RecipeScope.class, (JsonDeserializer<RecipeScope>) (json, type, context) -> {
					try {
						return RecipeScope.values()[json.getAsInt()];
					} catch (Exception e) {
						return null;
					}
				})
				.create();
	}

	@Override
	public Mono<QuickfixEdit> createEdits(Object p) {
		if (p instanceof JsonElement) {
			return Mono.fromFuture(createEdit((JsonElement) p).thenApply(we -> new QuickfixEdit(we, null)));
		}
		return null;
	}

	
	@Override
	public CompletableFuture<WorkspaceEdit> resolve(CodeAction codeAction) {
		if (codeAction.getData() instanceof JsonElement) {
			try {
				return createEdit((JsonElement) codeAction.getData());
			} catch (Exception e) {
				log.error("", e);
			}
		}
		return CompletableFuture.completedFuture(null);
	}
	
	public CompletableFuture<WorkspaceEdit> createEdit(JsonElement o) {
		FixDescriptor data = gson.fromJson(o, FixDescriptor.class);
		if (data != null && data.getRecipeId() != null) {
			return perform(data).thenApply(we -> we.orElse(null));
		}
		return null;
	}
	
	private CompletableFuture<Optional<WorkspaceEdit>> perform(FixDescriptor data) {
		Optional<IJavaProject> project = projectFinder.find(new TextDocumentIdentifier(data.getDocUris().get(0)));
		if (project.isPresent()) {
			boolean projectWide = data.getRecipeScope() == RecipeScope.PROJECT;
			IndefiniteProgressTask progress = server.getProgressService().createIndefiniteProgressTask(UUID.randomUUID().toString(), data.getLabel(), "Parsing files...");
			return createRecipe(data).thenCompose(r -> {
				if (r == null) {
					log.warn("Code Action failed to resolve. Could not create recipe created with id '" + data.getRecipeId() + "'.");
				}
				List<SourceFile> cus = new ArrayList<>();
				if (projectWide) {
					JavaParser jp = ORAstUtils.createJavaParserBuilder(project.get()).dependsOn(data.getTypeStubs()).build();
					List<Input> inputs = ORAstUtils.getParserInputs(server.getTextDocumentService(), project.get());
					cus.addAll(ORAstUtils.parseInputs(jp, inputs, null));
				} else {
					JavaParser jp = ORAstUtils.createJavaParserBuilder(project.get()).dependsOn(data.getTypeStubs()).build();
					List<Input> inputs = data.getDocUris().stream().map(URI::create).map(Paths::get).map(p -> ORAstUtils.getParserInput(server.getTextDocumentService(), p)).collect(Collectors.toList());
					cus.addAll(ORAstUtils.parseInputs(jp, inputs, null));
				}
				return recipeRepo.computeWorkspaceEditAwareOfPreview(r, cus, progress, projectWide).whenComplete((o, t) -> progress.done());
			});
		}
		return CompletableFuture.completedFuture(Optional.empty());
	}
	
	private CompletableFuture<Optional<Class<?>>> findRecipeClass(String className) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Optional<Class<?>> opt = Optional.of(getClass().getClassLoader().loadClass(className));
				return opt;
			} catch (Exception e) {
				// ignore
				log.info("Didn't find the recipe class '%s' trying recipe repository".formatted(className));
				return Optional.empty();
			}
		});
	}

	private CompletableFuture<Recipe> createRecipe(FixDescriptor d) {
		return findRecipeClass(d.getRecipeId())
		.thenCompose(optRecipeClass -> optRecipeClass
				.map(recipeClass -> CompletableFuture.completedFuture(RecipeIntrospectionUtils.constructRecipe(recipeClass)))
				.orElseGet(() -> recipeRepo.getRecipe(d.getRecipeId()).thenApply(opt -> opt.orElseThrow())))
		.thenApply(r -> {
			if (d.getParameters() != null) {
                setParameters(r, d.getParameters());
            }
			if (d.getRecipeScope() == RecipeScope.NODE) {
				if (d.getRangeScope() == null) {
					throw new IllegalArgumentException("Missing scope AST node!");
				} else {
					if (r instanceof RangeScopedRecipe) {
						((RangeScopedRecipe) r).setRange(d.getRangeScope());
					} else {
						r = ORAstUtils.nodeRecipe(r, j -> {
							if (j != null) {
								 Range range = j.getMarkers().findFirst(Range.class).orElse(null);
								 if (range != null) {
									 // Rewrite range end offset is up to not including hence -1
									 return d.getRangeScope().getStart().getOffset() <= range.getStart().getOffset() && range.getEnd().getOffset() - 1 <= d.getRangeScope().getEnd().getOffset();  
								 }
							}
							return false;
						});
					}
				}
			}
			return r;
		});
	}
	
	/**
	 * Sets the parameters for a given recipe. If the recipe is a DeclarativeRecipe,
	 * it iterates over its sub-recipes and sets the parameters for each sub-recipe.
	 */
	private void setParameters(Recipe recipe, Map<String, Object> parameters) {
	    if (recipe instanceof DeclarativeRecipe) {
	        List<Recipe> subRecipes = ((DeclarativeRecipe) recipe).getRecipeList();
	        for (Recipe subRecipe : subRecipes) {
	            setParameters(subRecipe, parameters);
	        }
	    } else {
	    	for (Entry<String, Object> entry : parameters.entrySet()) {
				try {
					Field field = findField(recipe, entry.getKey());
	                if (field != null) {
	                	field.setAccessible(true);
	                	field.set(recipe, entry.getValue());
	                }
				} catch (Exception e) {
					log.error("", e);;
				}
			}
	    }
	}

	private Field findField(Object obj, String fieldName) {
	    Class<?> clazz = obj.getClass();
	    while (clazz != null) {
	        try {
	            return clazz.getDeclaredField(fieldName);
	        } catch (NoSuchFieldException e) {
	            clazz = clazz.getSuperclass();
	        }
	    }
	    return null;
	}
}
