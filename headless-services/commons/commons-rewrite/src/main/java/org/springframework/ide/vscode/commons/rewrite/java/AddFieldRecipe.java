package org.springframework.ide.vscode.commons.rewrite.java;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.tree.J;

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
	String fullyQualifiedBeanName;

	@JsonCreator
	public AddFieldRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedBeanName) {
		this.fullyQualifiedBeanName = fullyQualifiedBeanName;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {

		return new JavaIsoVisitor<ExecutionContext>() {

			private final JavaTemplate fieldTemplate = JavaTemplate.builder("private final #{} #{};").build();

			@Override
			public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
//				super.visitClassDeclaration(classDecl, ctx);
				System.out.println(TreeVisitingPrinter.printTree(getCursor()));
				
				// Check if the class already has the field
				boolean hasOwnerRepoField = classDecl.getBody().getStatements().stream()
						.filter(J.VariableDeclarations.class::isInstance).map(J.VariableDeclarations.class::cast)
						.anyMatch(varDecl -> varDecl.getTypeExpression() != null
								&& varDecl.getTypeExpression().toString().equals(fullyQualifiedBeanName));
				
				if (!hasOwnerRepoField) {
//                	JavaType.FullyQualified typeFqn = TypeUtils.asFullyQualified(fullyQualifiedBeanName);
					// Add import for testing purpose
					maybeAddImport("com.example.demo.OwnerRepository");

					classDecl = classDecl.withBody(fieldTemplate.apply(new Cursor(getCursor(), classDecl.getBody()),
							classDecl.getBody().getCoordinates().firstStatement(), fullyQualifiedBeanName,
							Character.toLowerCase(fullyQualifiedBeanName.charAt(0))
									+ fullyQualifiedBeanName.substring(1)));

				}
				return classDecl;
			}
		};
	}
}
