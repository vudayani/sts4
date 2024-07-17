package org.springframework.ide.vscode.boot.java.beans;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.rewrite.ORDocUtils;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.AddFieldRecipe;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.text.IDocument;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;

public class BeanCompletionProposal implements ICompletionProposal {

	private DocumentEdits edits;
	private IDocument doc;
	private String label;
	private String detail;
	private Renderable documentation;
	private RewriteRefactorings rewriteRefactorings;

	private Gson gson;

	public BeanCompletionProposal(DocumentEdits edits, IDocument doc, String label, String detail,
			Renderable documentation, RewriteRefactorings rewriteRefactorings) {
		this.edits = edits;
		this.doc = doc;
		this.label = label;
		this.detail = detail;
		this.documentation = documentation;
		this.rewriteRefactorings = rewriteRefactorings;
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
	public String getLabel() {
		return this.label;
	}

	@Override
	public CompletionItemKind getKind() {
		return CompletionItemKind.Constructor;
	}

	@Override
	public DocumentEdits getTextEdit() {
		return this.edits;
	}

	@Override
	public String getDetail() {
		return this.detail;
	}

	@Override
	public Renderable getDocumentation() {
		return this.documentation;
	}

	@Override
	public Optional<java.util.function.Supplier<DocumentEdits>> getAdditionalEdit() {
		FixDescriptor f = new FixDescriptor(AddFieldRecipe.class.getName(), List.of(this.doc.getUri()),"Inject bean completions")
				.withParameters(Map.of("fullyQualifiedName", this.detail))
				.withRecipeScope(RecipeScope.NODE);
		JsonElement jsonElement = gson.toJsonTree(f);
		CompletableFuture<WorkspaceEdit> workspaceEdits = this.rewriteRefactorings.createEdit(jsonElement);

		CompletableFuture<Optional<DocumentEdits>> docEditsFuture = workspaceEdits.thenApply(workspaceEdit -> {
//	        System.out.println("Workspace Edit: " + workspaceEdit.getDocumentChanges().toString());
			Optional<DocumentEdits> docEdits = ORDocUtils.computeDocumentEdits(workspaceEdit, doc);
//	        System.out.println(docEdits.get().toString());
			return docEdits;

		});

		return Optional.of(() -> {
			try {
				if (docEditsFuture != null && !docEditsFuture.isDone()) {
					Optional<DocumentEdits> docEdits = docEditsFuture.get();
					return docEdits.orElse(null);
				} else {
					return null;
				}
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				return null;
			}
		});
	}
}
