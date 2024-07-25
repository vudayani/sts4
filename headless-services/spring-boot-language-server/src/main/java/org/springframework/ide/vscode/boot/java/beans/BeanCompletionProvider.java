package org.springframework.ide.vscode.boot.java.beans;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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
public class BeanCompletionProvider implements CompletionProvider {

	private static final Logger log = LoggerFactory.getLogger(BeanCompletionProvider.class);

	private final JavaProjectFinder javaProjectFinder;
	private final SpringMetamodelIndex springIndex;
	private final RewriteRefactorings rewriteRefactorings;

	public BeanCompletionProvider(JavaProjectFinder javaProjectFinder, SpringMetamodelIndex springIndex,
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
			TypeDeclaration topLevelClass = findTopLevelClass(node);
	        if (topLevelClass == null) {
	            return;
	        }

			if (node instanceof SimpleName && isSpringComponent(topLevelClass)) {
	            String className = getFullyQualifiedName(topLevelClass);
				Bean[] beans = this.springIndex.getBeansOfProject(project.getElementName());
				for (Bean bean : beans) {
					if (FuzzyMatcher.matchScore(node.toString(), bean.getName()) != 0.0) {
						DocumentEdits edits = new DocumentEdits(doc, false);
						edits.replace(offset - node.toString().length(), offset, bean.getName());
						BeanCompletionProposal proposal = new BeanCompletionProposal(edits, doc, bean.getName(),
								bean.getType(),className, null, rewriteRefactorings);
						completions.add(proposal);
					}
				}
			}
		} catch (Exception e) {
			log.error("problem while looking for bean completions", e);
		}
	}
	
	private static boolean isSpringComponent(TypeDeclaration node) {		
		for (IAnnotationBinding annotation : node.resolveBinding().getAnnotations()) {
            String annotationName = annotation.getAnnotationType().getQualifiedName();
            if (annotationName.equals("org.springframework.stereotype.Component") ||
                annotationName.equals("org.springframework.stereotype.Service") ||
                annotationName.equals("org.springframework.stereotype.Repository") ||
                annotationName.equals("org.springframework.stereotype.Controller") ||
                annotationName.equals("org.springframework.context.annotation.Configuration")) {
                return true;
            }
        }
        return false;
	}
	
	private static TypeDeclaration findTopLevelClass(ASTNode node) {
		ASTNode current = node;
		while (current != null && !(current instanceof CompilationUnit)) {
	        if (current.getParent() instanceof CompilationUnit && current instanceof TypeDeclaration) {
	            return (TypeDeclaration) current;
	        }
	        current = current.getParent();
	    }
	    return null;
	}
	
	private static String getFullyQualifiedName(TypeDeclaration typeDecl) {
	    CompilationUnit cu = (CompilationUnit) typeDecl.getRoot();
	    String packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "";
	    String typeName = typeDecl.getName().getFullyQualifiedName();
	    return packageName.isEmpty() ? typeName : packageName + "." + typeName;
	}

}
