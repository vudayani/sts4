package org.springframework.ide.vscode.commons.rewrite.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

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

public class AddFieldRecipeTest implements RewriteTest {
	
	@Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddFieldRecipe("com.example.test.OwnerRepository"))
        .parser(JavaParser.fromJavaVersion()
        		.logCompilationWarningsAndErrors(true));
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
	
	// This adds a new field when the LST is valid
	@Test
    void addField() {
        rewriteRun(
        	spec -> spec.recipe(new AddFieldRecipe("com.example.test.OwnerRepository")),
            java(
                """
                    package com.example.demo;
                    
                  import com.example.test.OwnerRepository;
                    
                  class FooBar {
            		    
            		    public void test() {}
                    
                    }
                """,
                """
                package com.example.demo;
                
              import com.example.test.OwnerRepository;
               
              class FooBar {
                
                  private final OwnerRepository ownerRepository;
        		    
        		    public void test() {}
                
                }
                """
              )
        );
    }
	
	// The test parses invalid LST and then applies the recipe
	@Test
	void addFieldWithIncompleteLST() {
		
		String beforeSourceStr = """
                package com.example.demo;
                
                class FooBar {
                    
                    public void test() {
                        ownerR

                    }
                
                }
            """;
		
		String sourceStrPassed = """
                package com.example.demo;
                
                class FooBar {
                    
                    public void test() {}
                
                }
            """;

        String expectedSourceStr = """
                package com.example.demo;
                
                class FooBar {
                
                    private final OwnerRepository ownerRepository;
                    
                    public void test() {}
                
                }
            """;

        String dependsOn = """
                package com.example.test;
                public interface OwnerRepository{}
            """;

        Recipe recipe = new AddFieldRecipe("com.example.demo.OwnerRepository");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	} 
	
	@Test
	void addFieldAndImport() {
		
		String beforeSourceStr = """
                package com.example.demo;
                
                class FooBar {
                    
                    public void test() {
                        ownerR

                    }
                
                }
            """;
		
		String sourceStrPassed = """
                package com.example.demo;
                
                class FooBar {
                    
                    public void test() {}
                
                }
            """;

        String expectedSourceStr = """
                package com.example.demo;
                
            import com.example.test.OwnerRepository;
                
            class FooBar {
                
                    private final OwnerRepository ownerRepository;
                    
                    public void test() {}
                
                }
            """;

        String dependsOn = """
                package com.example.test;
                public interface OwnerRepository{}
            """;

        Recipe recipe = new AddFieldRecipe("com.example.test.OwnerRepository");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	} 
	
	@Test
    void addNestedField() {
		
		String beforeSourceStr = """
                package com.example.demo;
                
                class FooBar {
                    
                    public void test() {
                        ownerR

                    }
                
                }
            """;
		
		String sourceStrPassed = """
                package com.example.demo;
                
                class FooBar {
                    
                    public void test() {}
                
                }
            """;

        String expectedSourceStr = """
                package com.example.demo;
                
            import com.example.test.Inner.OwnerRepository;
                
            class FooBar {
                
                    private final Inner.OwnerRepository ownerRepository;
                    
                    public void test() {}
                
                }
            """;

        String dependsOn = """
                package com.example.test;
                public interface OwnerRepository{}
            """;

        Recipe recipe = new AddFieldRecipe("com.example.test.Inner.OwnerRepository");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
    }

}
