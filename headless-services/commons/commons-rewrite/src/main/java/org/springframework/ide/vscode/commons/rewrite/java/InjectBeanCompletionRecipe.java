package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.ArrayList;
import java.util.List;

import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;

public class InjectBeanCompletionRecipe extends Recipe {

	@Override
	public @DisplayName String getDisplayName() {
		return "Inject bean completions";
	}

	@Override
	public @Description String getDescription() {
		return "Automates the injection of a specified bean into Spring components by adding the necessary field and import, creating the constructor if it doesn't exist, and injecting the bean as a constructor parameter.";
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
	
	public InjectBeanCompletionRecipe(String fullyQualifiedName, String fieldName, String classFqName) {
		this.fullyQualifiedName = fullyQualifiedName;
		this.fieldName = fieldName;
		this.classFqName = classFqName;
    }
	
	@Override
    public List<Recipe> getRecipeList() {
		List<Recipe> list = new ArrayList<>();
		list.add(new AddFieldRecipe(fullyQualifiedName, classFqName));
		list.add(new ConstructorInjectionRecipe(fullyQualifiedName, fieldName, classFqName));
		return list;
    }

}
