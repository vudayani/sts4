package org.springframework.ide.vscode.boot.java.beans;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Udayani V
 */
public class BeanCompletionProcessor implements CompletionProvider {

	private static final Logger log = LoggerFactory.getLogger(BeanCompletionProcessor.class);

	private final JavaProjectFinder javaProjectFinder;
	private final SpringMetamodelIndex springIndex;
	private final RewriteRefactorings rewriteRefactorings;

	public BeanCompletionProcessor(JavaProjectFinder javaProjectFinder, SpringMetamodelIndex springIndex,
			RewriteRefactorings rewriteRefactorings) {
		this.javaProjectFinder = javaProjectFinder;
		this.springIndex = springIndex;
		this.rewriteRefactorings = rewriteRefactorings;
	}

	@Override
	public void provideCompletions(ASTNode node, int offset, TextDocument doc,
			Collection<ICompletionProposal> completions) {
		try {
			Optional<IJavaProject> optionalProject = this.javaProjectFinder.find(doc.getId());
			if (optionalProject.isEmpty()) {
				return;
			}

			IJavaProject project = optionalProject.get();

			if (node instanceof SimpleName) {
				Bean[] beans = this.springIndex.getBeansOfProject(project.getElementName());

				for (Bean bean : beans) {
					if (FuzzyMatcher.matchScore(node.toString(), bean.getName()) != 0.0) {
						DocumentEdits edits = new DocumentEdits(doc, false);
						BeanCompletionProposal proposal = new BeanCompletionProposal(edits, doc, bean.getName(),
								bean.getType(), null, rewriteRefactorings);
						completions.add(proposal);
					}
				}
			}
		} catch (Exception e) {
			log.error("problem while looking for bean completions", e);
		}
	}

}
