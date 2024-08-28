package org.springframework.ide.vscode.boot.java.spel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.TypeReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.IJavaDefinitionProvider;
import org.springframework.ide.vscode.boot.java.spel.AnnotationParamSpelExtractor.Snippet;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.parser.spel.SpelLexer;
import org.springframework.ide.vscode.parser.spel.SpelParser;
import org.springframework.ide.vscode.parser.spel.SpelParser.BeanReferenceContext;
import org.springframework.ide.vscode.parser.spel.SpelParserBaseListener;

public class SpelDefinitionProvider implements IJavaDefinitionProvider {

	protected static Logger logger = LoggerFactory.getLogger(SpelDefinitionProvider.class);

	private final SpringMetamodelIndex springIndex;

	private final AnnotationParamSpelExtractor[] spelExtractors = AnnotationParamSpelExtractor.SPEL_EXTRACTORS;
	
	private List<Token> beanReferenceTokens = new ArrayList<>();
	private List<Token> methodReferenceTokens = new ArrayList<>();

	public SpelDefinitionProvider(SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
	}

	@Override
	public List<LocationLink> getDefinitions(CancelChecker cancelToken, IJavaProject project, CompilationUnit cu,
			ASTNode n, int offset) {
		if (n instanceof StringLiteral) {
			StringLiteral valueNode = (StringLiteral) n;
			logger.info("value node " + valueNode);

			ASTNode parent = ASTUtils.getNearestAnnotationParent(valueNode);

			if (parent != null && parent instanceof Annotation) {
				Annotation a = (Annotation) parent;
				IAnnotationBinding binding = a.resolveAnnotationBinding();
				if (binding != null && binding.getAnnotationType() != null
						&& Annotations.VALUE.equals(binding.getAnnotationType().getQualifiedName())) {
					return parseSpelAndFetchLocation(cancelToken, project, cu, offset);
				}
			}
		}
		return Collections.emptyList();
	}

	private List<LocationLink> parseSpelAndFetchLocation(CancelChecker cancelToken, IJavaProject project,
			CompilationUnit cu, int offset) {
		List<LocationLink> locationLink = new ArrayList<>();
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(SingleMemberAnnotation node) {
				logger.info("offset : " + offset);
				Arrays.stream(spelExtractors).map(e -> e.getSpelRegion(node)).filter(o -> o.isPresent())
						.map(o -> o.get()).forEach(snippet -> {
							computeTokens(snippet.text(), offset);
							if (beanReferenceTokens != null && beanReferenceTokens.size() > 0) {
								int adjustedOffset = computeAdjustedOffset(offset, node.toString(), snippet);
								locationLink.addAll(
										findLocationLinksForOffsetTokens(project, adjustedOffset, beanReferenceTokens));
							}

//							parseAndExtractMethodNamesFromSpel(snippet.text());
						});
				return super.visit(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {

				Arrays.stream(spelExtractors).map(e -> e.getSpelRegion(node)).filter(o -> o.isPresent())
						.map(o -> o.get()).forEach(snippet -> {
							computeAdjustedOffset(offset, node.toString(), snippet);
							computeTokens(snippet.text(), 0);
							if (beanReferenceTokens != null && beanReferenceTokens.size() > 0) {
								int adjustedOffset = computeAdjustedOffset(offset, node.toString(), snippet);
								locationLink.addAll(
										findLocationLinksForOffsetTokens(project, adjustedOffset, beanReferenceTokens));
							}

//							parseAndExtractMethodNamesFromSpel(snippet.text());
						});

				return super.visit(node);
			}

		});
		return locationLink;
	}

	private int computeAdjustedOffset(int offset, String node, Snippet snippet) {
		return offset - node.indexOf(snippet.text());
	}

	private List<LocationLink> findBeansWithName(IJavaProject project, String beanName) {
		Bean[] beans = this.springIndex.getBeansWithName(project.getElementName(), beanName);

		return Arrays.stream(beans).map(bean -> {
			return new LocationLink(bean.getLocation().getUri(), bean.getLocation().getRange(),
					bean.getLocation().getRange());
		}).collect(Collectors.toList());
	}

	private List<LocationLink> findLocationLinksForOffsetTokens(IJavaProject project, int offset, List<Token> tokens) {
		return tokens.stream().filter(t -> isOffsetWithinToken(t, offset))
				.flatMap(t -> findBeansWithName(project, t.getText()).stream()).collect(Collectors.toList());
	}

	private boolean isOffsetWithinToken(Token token, int offset) {
		int startIndex = token.getStartIndex();
		int endIndex = startIndex + token.getText().length();
		return startIndex <= (offset) && (offset) <= endIndex;
	}

	private void computeTokens(String text, int offset) {
		SpelLexer lexer = new SpelLexer(CharStreams.fromString(text));
		CommonTokenStream antlrTokens = new CommonTokenStream(lexer);
		SpelParser parser = new SpelParser(antlrTokens);

		lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
		parser.removeErrorListener(ConsoleErrorListener.INSTANCE);

		parser.addParseListener(new SpelParserBaseListener() {

			@Override
			public void exitBeanReference(BeanReferenceContext ctx) {
				if (ctx.IDENTIFIER() != null) {
					beanReferenceTokens.add(ctx.IDENTIFIER().getSymbol());
				}
				if (ctx.STRING_LITERAL() != null) {
					beanReferenceTokens.add(ctx.STRING_LITERAL().getSymbol());
				}
			}

		});

		parser.spelExpr();
	}

	private Set<String> parseAndExtractMethodNamesFromSpel(String spelExpression) {
		Set<String> methodNames = new HashSet<>();
		SpelExpressionParser parser = new SpelExpressionParser();
		try {
			org.springframework.expression.Expression expression = parser.parseExpression(spelExpression);

			SpelExpression spelExpressionAST = (SpelExpression) expression;
			SpelNode rootNode = spelExpressionAST.getAST();
			Map<String, String> methodClasses = new HashMap<>();
			extractMethodClassesFromSpelNodes(rootNode, null, methodClasses);
			methodClasses.entrySet().stream().forEach(p -> System.out.println(p.getKey() + " " + p.getValue()));
		} catch (ParseException e) {
			System.out.println("error" + e);
		}
		return methodNames;
	}

	private static void extractMethodClassesFromSpelNodes(SpelNode node, SpelNode parent,
			Map<String, String> methodClasses) {
		if (node instanceof MethodReference) {
			MethodReference methodRef = (MethodReference) node;
			String methodName = methodRef.getName();
			String className = null;
			System.out.println("method " + methodName);
			System.out.println("parent " + parent.toStringAST().toString());
			if (parent != null) {
				if (parent instanceof PropertyOrFieldReference) {
					className = ((PropertyOrFieldReference) parent).getName();
				} else if (parent instanceof TypeReference) {
					className = ((TypeReference) parent).toStringAST();
				}
			}

			if (parent instanceof CompoundExpression) {
				for (int i = 0; i < parent.getChildCount(); i++) {
					SpelNode child = parent.getChild(i);
					if (child instanceof PropertyOrFieldReference || child instanceof BeanReference
							|| child instanceof TypeReference) {
//	                    return extractReferenceName(child);
//						System.out.println("Found " + child.toStringAST().toString());
						return;
					}
				}
			}

			if (className != null) {
				methodClasses.put(methodName, className);
			}
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			extractMethodClassesFromSpelNodes(node.getChild(i), node, methodClasses);
		}
	}

}
