package org.springframework.ide.vscode.boot.java.spel;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.boot.java.spel.AnnotationParamSpelExtractor.Snippet;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

/**
 * @author Udayani V
 */
public class QueryCodeLensProvider implements CodeLensProvider {

	private static final String QUERY = "Query";
	private static final String FQN_QUERY = "org.springframework.data.jpa.repository." + QUERY;
	private static final String SPEL_EXPRESSION_QUERY = "Explain the following SpEL Expression in detail: \n";
    private static final String JPQL_QUERY = "Explain the following query in detail. If the query contains any SpEL expressions, explain those parts as well: \n";
    private static final String CMD = "vscode-spring-boot.query.explain";
	private final AnnotationParamSpelExtractor[] spelExtractors = AnnotationParamSpelExtractor.SPEL_EXTRACTORS;

	@Override
	public void provideCodeLenses(CancelChecker cancelToken, TextDocument document, CompilationUnit cu,
			List<CodeLens> resultAccumulator) {
		cu.accept(new ASTVisitor() {

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				Arrays.stream(spelExtractors).map(e -> e.getSpelRegion(node)).filter(o -> o.isPresent())
						.map(o -> o.get()).forEach(snippet -> {
							provideCodeLens(cancelToken, node, document, snippet, resultAccumulator);
						});

				if (isQueryAnnotation(node)) {
					provideCodeLens(cancelToken, node, document, node.getValue(), resultAccumulator);
				}

				return super.visit(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				Arrays.stream(spelExtractors).map(e -> e.getSpelRegion(node)).filter(o -> o.isPresent())
						.map(o -> o.get()).forEach(snippet -> {
							provideCodeLens(cancelToken, node, document, snippet, resultAccumulator);
						});

				if (isQueryAnnotation(node)) {
					for (Object value : node.values()) {
						if (value instanceof MemberValuePair) {
							MemberValuePair pair = (MemberValuePair) value;
							if ("value".equals(pair.getName().getIdentifier())) {
								provideCodeLens(cancelToken, node, document, pair.getValue(), resultAccumulator);
								break;
							}
						}
					}
				}

				return super.visit(node);
			}
		});
	}

	protected void provideCodeLens(CancelChecker cancelToken, Annotation node, TextDocument document, Snippet snippet,
			List<CodeLens> resultAccumulator) {
		cancelToken.checkCanceled();

		if (snippet != null) {
			try {

				CodeLens codeLens = new CodeLens();
				codeLens.setRange(document.toRange(snippet.offset(), snippet.text().length()));

				Command cmd = new Command();
				cmd.setTitle("Explain Spel Expression using Copilot");
				cmd.setCommand(CMD);
				cmd.setArguments(ImmutableList.of(SPEL_EXPRESSION_QUERY + snippet.text(), document.toRange(snippet.offset(), snippet.text().length())));
				codeLens.setCommand(cmd);

				resultAccumulator.add(codeLens);
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	protected void provideCodeLens(CancelChecker cancelToken, Annotation node, TextDocument document,
			Expression valueExp, List<CodeLens> resultAccumulator) {
		cancelToken.checkCanceled();

		if (valueExp != null) {
			try {

				CodeLens codeLens = new CodeLens();
				codeLens.setRange(document.toRange(valueExp.getStartPosition(), valueExp.getLength()));

				Command cmd = new Command();
				cmd.setTitle("Explain Query using Copilot");
				cmd.setCommand(CMD);
				cmd.setArguments(ImmutableList.of(JPQL_QUERY + valueExp.toString(), document.toRange(valueExp.getStartPosition(), valueExp.getLength())));
				codeLens.setCommand(cmd);

				resultAccumulator.add(codeLens);
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	private static boolean isQueryAnnotation(Annotation a) {
		return FQN_QUERY.equals(a.getTypeName().getFullyQualifiedName())
				|| QUERY.equals(a.getTypeName().getFullyQualifiedName());
	}

}
