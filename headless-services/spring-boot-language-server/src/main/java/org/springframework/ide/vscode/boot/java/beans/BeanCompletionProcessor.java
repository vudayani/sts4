package org.springframework.ide.vscode.boot.java.beans;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.CompletionItemKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.common.InformationTemplates;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.value.ValuePropertyKeyProposal;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.SimpleCompletionFactory;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.FuzzyMap.Match;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Udayani V
 */
public class BeanCompletionProcessor implements CompletionProvider {
	
	private static final Logger log = LoggerFactory.getLogger(BeanCompletionProcessor.class);
	
	private final JavaProjectFinder javaProjectFinder;
	private final SpringMetamodelIndex springIndex;
	
	public BeanCompletionProcessor(JavaProjectFinder javaProjectFinder, SpringMetamodelIndex springIndex) {
		this.javaProjectFinder = javaProjectFinder;
		this.springIndex = springIndex;
	}

	@Override
	public void provideCompletions(ASTNode node, int offset, TextDocument doc, Collection<ICompletionProposal> completions) {
		try {
			Optional<IJavaProject> optionalProject = this.javaProjectFinder.find(doc.getId());
			if (optionalProject.isEmpty()) {
				return;
			}
			
			IJavaProject project = optionalProject.get();
			log.info("Project : " , project.toString());
			
			if(node instanceof SimpleName) {
				Bean[] beans = this.springIndex.getBeansOfProject(project.getElementName());
				log.info(beans.toString());
				for(Bean bean : beans) {
					if (FuzzyMatcher.matchScore(node.toString(), bean.getName())!=0.0) {
//						completions.add(SimpleCompletionFactory.simpleProposal(doc, offset+node.toString().length(), node.toString(), CompletionItemKind.Text ,"TestingAssist", "detail", InformationTemplates.createCompletionDocumentation("id", null, "defaultvalue", null)));

						DocumentEdits edits = new DocumentEdits(doc, false);
						// TODO: Use the correct proposal kind
						DependsOnCompletionProposal proposal = new DependsOnCompletionProposal(edits, bean.getName()+"label", bean.getName()+"detail", null);

						completions.add(proposal);
					}
				}
			}
		} catch (Exception e) {
			log.error("problem while looking for bean completions", e);
		}
	}
	
	

}
