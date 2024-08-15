package org.springframework.ide.vscode.commons.rewrite.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.tree.ParseError;

public class InjectBeanCompletionRecipeTest implements RewriteTest {
	
	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new ConstructorInjectionRecipe("com.example.test.OwnerRepository", "ownerRepository", "com.example.demo.A"))
				.parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
	}
	
	public static void runRecipeAndAssert(Recipe recipe, String beforeSourceStr, String sourceStrPassed, String expectedSourceStr, String dependsOn) {
        JavaParser javaParser = JavaParser.fromJavaVersion().dependsOn(dependsOn).build();

        List<SourceFile> list = javaParser.parse(beforeSourceStr).map(sf -> {
            if (sf instanceof ParseError pe) {
                return pe.getErroneous();
            }
            return sf;
        }).toList();
        SourceFile beforeSource = list.get(0);

        assertThat(beforeSource.printAll()).isEqualTo(sourceStrPassed);

        InMemoryLargeSourceSet ss = new InMemoryLargeSourceSet(list);
        RecipeRun recipeRun = recipe.run(ss, new InMemoryExecutionContext(t -> {
            throw new RuntimeException(t);
        }));
        org.openrewrite.Result res = recipeRun.getChangeset().getAllResults().get(0);
        assertThat(res.getAfter().printAll()).isEqualTo(expectedSourceStr);
    }
	
	@Test
	void injectFieldIntoNewConstructor() {
		
		String beforeSourceStr = """
              package com.example.demo;
              
              import org.springframework.stereotype.Controller;
      
              @Controller
              public class A {
              
				  public void test() {
				  }
				  
              }
            """;
		
		String sourceStrPassed = """
              package com.example.demo;
                
              import org.springframework.stereotype.Controller;

              @Controller
              public class A {
              
				  public void test() {
				  }
              
              }
            """;

        String expectedSourceStr = """
        package com.example.demo;
                
        import com.example.test.OwnerRepository;
import org.springframework.stereotype.Controller;

@Controller
        public class A {

            private final OwnerRepository ownerRepository;

    A(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

public void test() {
}

        }
            """;

		String dependsOn = """
				    package com.example.test;
				    public interface OwnerRepository{}
				""";

        Recipe recipe = new InjectBeanCompletionRecipe("com.example.test.OwnerRepository", "ownerRepository", "com.example.demo.A");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	}
	

}
