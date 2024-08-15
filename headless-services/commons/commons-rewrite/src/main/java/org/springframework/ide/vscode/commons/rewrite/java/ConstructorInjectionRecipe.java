package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConstructorInjectionRecipe extends Recipe {

	@Override
	public @DisplayName String getDisplayName() {
		return "Add bean injection";
	}

	@Override
	public @Description String getDescription() {
		return "Add bean injection.";
	}

	@NonNull
	@Nullable
	String fullyQualifiedName;

	@NonNull
	@Nullable
	String fieldName;

	@NonNull
	@Nullable
	String classFqName;

	@JsonCreator
	public ConstructorInjectionRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedName,
			@NonNull @JsonProperty("fieldName") String fieldName,
			@NonNull @JsonProperty("classFqName") String classFqName) {
		this.fullyQualifiedName = fullyQualifiedName;
		this.fieldName = fieldName;
		this.classFqName = classFqName;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {

		return new CustomFieldIntoConstructorParameterVisitor(classFqName, fieldName);
	}

	class CustomFieldIntoConstructorParameterVisitor extends JavaVisitor<ExecutionContext> {

		private final String classFqName;
		private final String fieldName;
		private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

		public CustomFieldIntoConstructorParameterVisitor(String classFqName, String fieldName) {
			this.classFqName = classFqName;
			this.fieldName = fieldName;
		}

		@Override
		public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

			if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {
				List<MethodDeclaration> constructors = classDecl.getBody().getStatements().stream()
						.filter(J.MethodDeclaration.class::isInstance).map(J.MethodDeclaration.class::cast)
						.filter(MethodDeclaration::isConstructor).collect(Collectors.toList());
				boolean applicable = false;
				if (constructors.isEmpty()) {
					applicable = true;
				} else if (constructors.size() == 1) {
					MethodDeclaration c = constructors.get(0);
					getCursor().putMessage("applicableConstructor", c);
					applicable = isNotConstructorInitializingField(c, fieldName);
				} else {
					List<MethodDeclaration> autowiredConstructors = constructors.stream()
							.filter(constr -> constr.getLeadingAnnotations().stream()
									.map(a -> TypeUtils.asFullyQualified(a.getType())).filter(Objects::nonNull)
									.map(FullyQualified::getFullyQualifiedName).anyMatch(AUTOWIRED::equals))
							.limit(2).collect(Collectors.toList());
					if (autowiredConstructors.size() == 1) {
						MethodDeclaration c = autowiredConstructors.get(0);
						getCursor().putMessage("applicableConstructor", autowiredConstructors.get(0));
						applicable = isNotConstructorInitializingField(c, fieldName);
					}
				}
				if (applicable) {
					return super.visitClassDeclaration(classDecl, ctx);
				}
			}
			return super.visitClassDeclaration(classDecl, ctx);
		}

		public static boolean isNotConstructorInitializingField(MethodDeclaration c, String fieldName) {
			return c.getBody() == null || c.getBody().getStatements().stream().filter(J.Assignment.class::isInstance)
					.map(J.Assignment.class::cast).noneMatch(a -> {
						Expression expr = a.getVariable();
						if (expr instanceof J.FieldAccess) {
							J.FieldAccess fa = (J.FieldAccess) expr;
							if (fieldName.equals(fa.getSimpleName()) && fa.getTarget() instanceof J.Identifier) {
								J.Identifier target = (J.Identifier) fa.getTarget();
								if ("this".equals(target.getSimpleName())) {
									return true;
								}
							}
						}
						if (expr instanceof J.Identifier) {
							JavaType.Variable fieldType = c.getMethodType().getDeclaringType().getMembers().stream()
									.filter(v -> fieldName.equals(v.getName())).findFirst().orElse(null);
							if (fieldType != null) {
								J.Identifier identifier = (J.Identifier) expr;
								return fieldType.equals(identifier.getFieldType());
							}
						}
						return false;
					});
		}

		@Override
		public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
				ExecutionContext ctx) {

			Cursor blockCursor = getCursor().dropParentUntil(it -> it instanceof J.Block || it == Cursor.ROOT_VALUE);
			if (!(blockCursor.getValue() instanceof J.Block)) {
				return multiVariable;
			}
			VariableDeclarations mv = multiVariable;
			if (blockCursor.getParent() != null && blockCursor.getParent().getValue() instanceof ClassDeclaration
					&& multiVariable.getVariables().size() == 1
					&& fieldName.equals(multiVariable.getVariables().get(0).getName().getSimpleName())) {
				if (mv.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
					Space prefix = Space.firstPrefix(mv.getVariables());
					J.Modifier m = new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null,
							J.Modifier.Type.Final, Collections.emptyList());
					if (mv.getModifiers().isEmpty()) {
						mv = mv.withTypeExpression(mv.getTypeExpression().withPrefix(prefix));
					} else {
						m = m.withPrefix(prefix);
					}
					mv = mv.withModifiers(ListUtils.concat(mv.getModifiers(), m));
				}
				MethodDeclaration constructor = blockCursor.getParent().getMessage("applicableConstructor");
				ClassDeclaration c = blockCursor.getParent().getValue();
				TypeTree fieldType = TypeTree.build(fullyQualifiedName);
				if (constructor == null) {
					doAfterVisit(new AddConstructorVisitor(c.getSimpleName(), fieldName, fieldType));
				} else {
					doAfterVisit(new AddConstructorParameterAndAssignment(constructor, fieldName, fieldType));
				}
			}
			return mv;
		}
	}

	private static class AddConstructorVisitor extends JavaVisitor<ExecutionContext> {
		private final String className;
		private final String fieldName;
		private final TypeTree type;

		public AddConstructorVisitor(String className, String fieldName, TypeTree type) {
			this.className = className;
			this.fieldName = fieldName;
			this.type = type;
		}

		@Override
		public J visitBlock(Block block, ExecutionContext p) {
			J result = (Block) super.visitBlock(block, p);
			if (getCursor().getParent() != null) {
				Object n = getCursor().getParent().getValue();
				if (n instanceof ClassDeclaration) {
					ClassDeclaration classDecl = (ClassDeclaration) n;
					JavaType.FullyQualified typeFqn = TypeUtils.asFullyQualified(type.getType());
					if (typeFqn != null && classDecl.getKind() == ClassDeclaration.Kind.Type.Class
							&& className.equals(classDecl.getSimpleName())) {
						JavaTemplate.Builder template = JavaTemplate.builder(""
                                + classDecl.getSimpleName() + "(" + getFieldType(typeFqn) + " " + fieldName + ") {\n"
                                + "this." + fieldName + " = " + fieldName + ";\n"
                                + "}\n"
                        ).contextSensitive();
						FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
						if (fq != null) {
							template.imports(fq.getFullyQualifiedName());
							maybeAddImport(fq);
						}
						Optional<Statement> firstMethod = block.getStatements().stream()
								.filter(MethodDeclaration.class::isInstance).findFirst();

						return firstMethod
								.map(statement -> (J) template.build().apply(getCursor(),
										statement.getCoordinates().before()))
								.orElseGet(() -> template.build().apply(getCursor(),
										block.getCoordinates().lastStatement()));
					}
				}
			}
			return result;
		}
	}

	private static class AddConstructorParameterAndAssignment extends JavaIsoVisitor<ExecutionContext> {
		private final MethodDeclaration constructor;
		private final String fieldName;
		private final String methodType;

		public AddConstructorParameterAndAssignment(MethodDeclaration constructor, String fieldName, TypeTree type) {
			this.constructor = constructor;
			this.fieldName = fieldName;
			JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
			if (fq != null) {
				methodType = getFieldType(fq);
			} else {
				throw new IllegalArgumentException("Unable to determine parameter type");
			}
		}

		@Override
		public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
			J.MethodDeclaration md = super.visitMethodDeclaration(method, p);
			if (md == this.constructor && md.getBody() != null) {
				List<J> params = md.getParameters().stream().filter(s -> !(s instanceof J.Empty))
						.collect(Collectors.toList());
				String paramsStr = Stream
						.concat(params.stream().map(s -> "#{}"), Stream.of(methodType + " " + fieldName))
						.collect(Collectors.joining(", "));

				md = JavaTemplate.builder(paramsStr).contextSensitive().build().apply(getCursor(),
						md.getCoordinates().replaceParameters(), params.toArray());
				updateCursor(md);

				// noinspection ConstantConditions
				md = JavaTemplate.builder("this." + fieldName + " = " + fieldName + ";").contextSensitive().build()
						.apply(getCursor(), md.getBody().getCoordinates().lastStatement());
			}
			return md;
		}
	}

	private static String getFieldType(JavaType.FullyQualified fullyQualifiedType) {
		if (fullyQualifiedType.getOwningClass() != null) {
			String[] parts = fullyQualifiedType.getFullyQualifiedName().split("\\.");
			if (parts.length < 2) {
				return fullyQualifiedType.getClassName();
			}
			return parts[parts.length - 2] + "." + parts[parts.length - 1];
		}

		return fullyQualifiedType.getClassName();
	}
}