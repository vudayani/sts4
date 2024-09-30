package org.springframework.ide.vscode.boot.java.spel;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
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
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.spel.AnnotationParamSpelExtractor.Snippet;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.parser.spel.SpelLexer;
import org.springframework.ide.vscode.parser.spel.SpelParser;
import org.springframework.ide.vscode.parser.spel.SpelParser.BeanReferenceContext;
import org.springframework.ide.vscode.parser.spel.SpelParserBaseListener;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class SpelDefinitionProvider implements IJavaDefinitionProvider {

	protected static Logger logger = LoggerFactory.getLogger(SpelDefinitionProvider.class);

	private final SpringMetamodelIndex springIndex;

	private final CompilationUnitCache cuCache;

	private final AnnotationParamSpelExtractor[] spelExtractors = AnnotationParamSpelExtractor.SPEL_EXTRACTORS;

	public record TokenData(String text, int start, int end) {};

	public SpelDefinitionProvider(SpringMetamodelIndex springIndex, CompilationUnitCache cuCache) {
		this.springIndex = springIndex;
		this.cuCache = cuCache;
	}

	@Override
	public List<LocationLink> getDefinitions(CancelChecker cancelToken, IJavaProject project,
			TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		if (n instanceof StringLiteral) {
			StringLiteral valueNode = (StringLiteral) n;
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
				Arrays.stream(spelExtractors).map(e -> e.getSpelRegion(node)).filter(o -> o.isPresent())
						.map(o -> o.get()).forEach(snippet -> {
							List<TokenData> beanReferenceTokens = computeTokens(snippet, offset);
							if (beanReferenceTokens != null && beanReferenceTokens.size() > 0) {
								locationLink.addAll(findLocationLinksForBeanRef(project, offset, beanReferenceTokens));
							}

							Optional<Tuple2<String, String>> result = parseAndExtractMethodClassPairFromSpel(snippet,
									offset);
							result.ifPresent(tuple -> {
								locationLink.addAll(findLocationLinksForMethodRef(tuple.getT1(), tuple.getT2(), project));
							});
						});
				return super.visit(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				Arrays.stream(spelExtractors).map(e -> e.getSpelRegion(node)).filter(o -> o.isPresent())
						.map(o -> o.get()).forEach(snippet -> {
							List<TokenData> beanReferenceTokens = computeTokens(snippet, offset);
							if (beanReferenceTokens != null && beanReferenceTokens.size() > 0) {
								locationLink.addAll(findLocationLinksForBeanRef(project, offset, beanReferenceTokens));
							}
							parseAndExtractMethodClassPairFromSpel(snippet, offset);
							Optional<Tuple2<String, String>> result = parseAndExtractMethodClassPairFromSpel(snippet,
									offset);
							result.ifPresent(tuple -> {
								locationLink.addAll(findLocationLinksForMethodRef(tuple.getT1(), tuple.getT2(), project));
							});
						});

				return super.visit(node);
			}

		});
		return locationLink;
	}

	private List<LocationLink> findLocationLinksForBeanRef(IJavaProject project, int offset,
			List<TokenData> beanReferenceTokens) {
		return beanReferenceTokens.stream().flatMap(t -> findBeansWithName(project, t.text()).stream())
				.collect(Collectors.toList());
	}

	private List<LocationLink> findLocationLinksForMethodRef(String methodName, String className,
			IJavaProject project) {
		URI docUri = null;
		try {
			if (className.startsWith("T")) {
				String classFqName = className.substring(2, className.length() - 1);
				Optional<URL> sourceUrl = SourceLinks.source(project, classFqName);
				if (sourceUrl.isPresent()) {

					docUri = sourceUrl.get().toURI();
				}
			} else if (className.startsWith("@")) {
				String bean = className.substring(1);
				List<LocationLink> beanLoc = findBeansWithName(project, bean);
				if (beanLoc != null && beanLoc.size() > 0) {
					docUri = new URI(beanLoc.get(0).getTargetUri());
				}
			}

			if (docUri != null) {
				return findMethodPositionInDoc(docUri, methodName, project);
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return Collections.emptyList();
	}

	private List<LocationLink> findMethodPositionInDoc(URI docUrl, String methodName, IJavaProject project) {

		return cuCache.withCompilationUnit(project, docUrl, cu -> {
			List<LocationLink> locationLinks = new ArrayList<>();
			try {
				if (cu != null) {
					TextDocument document = new TextDocument(docUrl.toString(), LanguageId.JAVA);
					document.setText(cuCache.fetchContent(docUrl));
					cu.accept(new ASTVisitor() {

						@Override
						public boolean visit(MethodDeclaration node) {
							SimpleName nameNode = node.getName();
							if (nameNode.getIdentifier().equals(methodName)) {
								int start = nameNode.getStartPosition();
								int end = start + nameNode.getLength();
								DocumentRegion region = new DocumentRegion(document, start, end);
								try {
									Range docRange = document.toRange(region);
									locationLinks.add(new LocationLink(document.getUri(), docRange, docRange));
								} catch (BadLocationException e) {
									logger.error("", e);
								}
							}
							return super.visit(node);
						}
					});
				}
			} catch (URISyntaxException e) {
				logger.error("Error parsing the document url: " + docUrl);
			} catch (Exception e) {
				logger.error("error finding method location in doc '", e);
			}
			return locationLinks;
		});
	}

	private List<LocationLink> findBeansWithName(IJavaProject project, String beanName) {
		Bean[] beans = this.springIndex.getBeansWithName(project.getElementName(), beanName);
		return Arrays.stream(beans).map(bean -> {
			return new LocationLink(bean.getLocation().getUri(), bean.getLocation().getRange(),
					bean.getLocation().getRange());
		}).collect(Collectors.toList());
	}

	private List<TokenData> computeTokens(Snippet snippet, int offset) {
		SpelLexer lexer = new SpelLexer(CharStreams.fromString(snippet.text()));
		CommonTokenStream antlrTokens = new CommonTokenStream(lexer);
		SpelParser parser = new SpelParser(antlrTokens);

		List<TokenData> beanReferenceTokens = new ArrayList<>();

		lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
		parser.removeErrorListener(ConsoleErrorListener.INSTANCE);

		parser.addParseListener(new SpelParserBaseListener() {

			@Override
			public void exitBeanReference(BeanReferenceContext ctx) {
				if (ctx.IDENTIFIER() != null) {
					addTokenData(ctx.IDENTIFIER().getSymbol(), offset);
				}
				if (ctx.STRING_LITERAL() != null) {
					addTokenData(ctx.STRING_LITERAL().getSymbol(), offset);
				}
			}

			private void addTokenData(Token sym, int offset) {
				int start = sym.getStartIndex() + snippet.offset();
				int end = sym.getStartIndex() + sym.getText().length() + snippet.offset();
				if (isOffsetWithinToken(start, end, offset)) {
					beanReferenceTokens.add(new TokenData(sym.getText(), start, end));
				}
			}

			private boolean isOffsetWithinToken(int tokenStartIndex, int tokenEndIndex, int offset) {
				return tokenStartIndex <= (offset) && (offset) <= tokenEndIndex;
			}

		});

		parser.spelExpr();

		return beanReferenceTokens;
	}

	private Optional<Tuple2<String, String>> parseAndExtractMethodClassPairFromSpel(Snippet snippet, int offset) {
		SpelExpressionParser parser = new SpelExpressionParser();
		try {
			org.springframework.expression.Expression expression = parser.parseExpression(snippet.text());

			SpelExpression spelExpressionAST = (SpelExpression) expression;
			SpelNode rootNode = spelExpressionAST.getAST();
			return extractMethodClassPairFromSpelNodes(rootNode, null, snippet, offset);
		} catch (ParseException e) {
			logger.error("", e);
		}
		return Optional.empty();
	}

	private Optional<Tuple2<String, String>> extractMethodClassPairFromSpelNodes(SpelNode node, SpelNode parent,
			Snippet snippet, int offset) {
		if (node instanceof MethodReference && checkOffsetInMethodName(node, snippet.offset(), offset)) {
			MethodReference methodRef = (MethodReference) node;
			String methodName = methodRef.getName();
			String className = extractClassNameFromParent(parent);
	        if (className != null) {
	            return Optional.of(Tuples.of(methodName, className));
	        }
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			Optional<Tuple2<String, String>> result = extractMethodClassPairFromSpelNodes(node.getChild(i), node,
					snippet, offset);
			if (result.isPresent()) {
				return result;
			}
		}
		return Optional.empty();
	}
	
	private String extractClassNameFromParent(SpelNode parent) {
		if (parent != null) {
			if (parent instanceof PropertyOrFieldReference) {
				return ((PropertyOrFieldReference) parent).getName();
			} else if (parent instanceof TypeReference) {
				return ((TypeReference) parent).toStringAST();
			} else if (parent instanceof CompoundExpression) {
				for (int i = 0; i < parent.getChildCount(); i++) {
					SpelNode child = parent.getChild(i);
					if (child instanceof PropertyOrFieldReference || child instanceof BeanReference
							|| child instanceof TypeReference) {
						return child.toStringAST();
					}
				}
			}
		}
		return null;
	}

	private boolean checkOffsetInMethodName(SpelNode node, int nodeOffset, int offset) {
		int start = node.getStartPosition() + nodeOffset;
		int end = node.getEndPosition() + nodeOffset;
		return start <= (offset) && (offset) <= end;
	}

}
