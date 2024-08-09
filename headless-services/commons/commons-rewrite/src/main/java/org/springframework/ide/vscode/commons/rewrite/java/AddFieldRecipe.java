package org.springframework.ide.vscode.commons.rewrite.java;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddFieldRecipe extends Recipe {

	@Override
	public String getDisplayName() {
		return "Add field";
	}

	@Override
	public String getDescription() {
		return "Add field desccription.";
	}

	@NonNull
	@Nullable
	String fullyQualifiedName;
	
	@NonNull
	@Nullable
	String classFqName;

	@JsonCreator
	public AddFieldRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedName, @NonNull @JsonProperty("classFqName") String classFqName) {
		this.fullyQualifiedName = fullyQualifiedName;
		this.classFqName = classFqName;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {

		return new JavaIsoVisitor<ExecutionContext>() {

			JavaType.FullyQualified fullyQualifiedType = JavaType.ShallowClass.build(fullyQualifiedName);
			String fieldType = getFieldType(fullyQualifiedType);
			String fieldName = getFieldName(fullyQualifiedType);
			
			private final JavaTemplate fieldTemplate = JavaTemplate.builder("private final %s %s;"
					.formatted(fieldType, fieldName))
					.javaParser(JavaParser.fromJavaVersion()
							.dependsOn(
								"""
								package %s;
								
								public interface %s {}
								""".formatted(fullyQualifiedType.getPackageName(), fullyQualifiedType.getClassName()),
								"""
								package %s;
								
								public class A {
									public class %s {
										
									}
								}
								""".formatted(fullyQualifiedType.getPackageName(), fullyQualifiedType.getClassName()))
							)
					.imports(fullyQualifiedType.getFullyQualifiedName())
					.contextSensitive()
					.build();

			@Override
			public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
				if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {

					// Check if the class already has the field
					boolean hasOwnerRepoField = classDecl.getBody().getStatements().stream()
							.filter(J.VariableDeclarations.class::isInstance).map(J.VariableDeclarations.class::cast)
							.anyMatch(varDecl -> varDecl.getTypeExpression() != null
									&& varDecl.getTypeExpression().toString().equals(fieldType));

					if (!hasOwnerRepoField) {
						classDecl = classDecl.withBody(fieldTemplate.apply(new Cursor(getCursor(), classDecl.getBody()),
								classDecl.getBody().getCoordinates().firstStatement()));

						maybeAddImport(fullyQualifiedType.getFullyQualifiedName(), false);
					}
					return classDecl;
				}
				classDecl = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
				return classDecl;
			}
		};
	}
	
	private static String getFieldName(JavaType.FullyQualified fullyQualifiedType) {
		return Character.toLowerCase(fullyQualifiedType.getClassName().charAt(0)) + fullyQualifiedType.getClassName().substring(1);
	}
	
	private static String getFieldType(JavaType.FullyQualified fullyQualifiedType) {
		if(fullyQualifiedType.getOwningClass() != null) {
			String[] parts = fullyQualifiedType.getFullyQualifiedName().split("\\.");
	        if (parts.length < 2) {
	            return fullyQualifiedType.getClassName();
	        }
	        return parts[parts.length - 2] + "." + parts[parts.length - 1];
		}
			
        return fullyQualifiedType.getClassName();
    }
}
