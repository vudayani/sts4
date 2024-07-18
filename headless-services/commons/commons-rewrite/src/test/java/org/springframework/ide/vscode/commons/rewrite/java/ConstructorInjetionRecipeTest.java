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

public class ConstructorInjetionRecipeTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new ConstructorInjectionRecipe("ownerRepository", "com.example.demo.A"))
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
	void fieldIntoNewConstructor() {
		
		String beforeSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  private final OwnerRepository ownerRepository;
              
              }
            """;
		
		String sourceStrPassed = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  private final OwnerRepository ownerRepository;
              
              }
            """;

        String expectedSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;

              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  A(OwnerRepository ownerRepository) {
                      this.ownerRepository = ownerRepository;
                  }
              
              }
            """;

		String dependsOn = """
				    package com.example.test;
				    public interface OwnerRepository{}
				""";

        Recipe recipe = new ConstructorInjectionRecipe("ownerRepository", "com.example.demo.A");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	}
	
	@Test
	void fieldIntoExistingSingleConstructor() {
		
		String beforeSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  A() {
                  }
              
              }
            """;
		
		String sourceStrPassed = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  A() {
                  }
              
              }
            """;

        String expectedSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;

              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  A(OwnerRepository ownerRepository) {
                      this.ownerRepository = ownerRepository;
                  }
              
              }
            """;

		String dependsOn = """
				    package com.example.test;
				    public interface OwnerRepository{}
				""";

        Recipe recipe = new ConstructorInjectionRecipe("ownerRepository", "com.example.demo.A");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	}
	
	@Test
	void fieldIntoAutowiredConstructor() {
		
		String beforeSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  @Autowired
                  A() {
                  }
              
              }
            """;
		
		String sourceStrPassed = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  @Autowired
                  A() {
                  }
              
              }
            """;

        String expectedSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {
              
                  private final OwnerRepository ownerRepository;
                  
                  @Autowired
                  A(OwnerRepository ownerRepository) {
                      this.ownerRepository = ownerRepository;
                  }
              
              }
            """;

		String dependsOn = """
				    package com.example.test;
				    public interface OwnerRepository{}
				""";

        Recipe recipe = new ConstructorInjectionRecipe("ownerRepository", "com.example.demo.A");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	}
	
	@Test
	void fieldIntoExistingConstructorWithFields() {
		
		String beforeSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  String a;
                  
                  private final OwnerRepository ownerRepository;
                  
                  A(String a) {
					this.a = a;
				  }
              }
            """;
		
		String sourceStrPassed = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  String a;
                  
                  private final OwnerRepository ownerRepository;
                  
                  A(String a) {
					this.a = a;
				  }
              }
            """;

        String expectedSourceStr = """
              package com.example.demo;
                
              import com.example.test.OwnerRepository;
              
              public class A {
              
                  String a;
                  
                  private final OwnerRepository ownerRepository;
                  
                  A(String a, OwnerRepository ownerRepository) {
					this.a = a;
					this.ownerRepository = ownerRepository;        		                    		            
				  }
              }
            """;

		String dependsOn = """
				    package com.example.test;
				    public interface OwnerRepository{}
				""";

        Recipe recipe = new ConstructorInjectionRecipe("ownerRepository", "com.example.demo.A");
        runRecipeAndAssert(recipe, beforeSourceStr, sourceStrPassed, expectedSourceStr, dependsOn);
	}
	
	
//	@Test
//    void fieldIntoExistingSingleConstructor() {
//        //language=java
//        rewriteRun(
//          java(
//            """
//              package com.example.demo;
//
//              import com.example.demo.OwnerRepository;
//              
//              public class A {
//              
//                  private final OwnerRepository ownerRepository;
//              
//              }
//              """,
//            """
//              package com.example.demo;
//              
//              import com.example.demo.OwnerRepository;
//              
//              public class A {
//              
//                  private final OwnerRepository ownerRepository;
//                  
//                  A(OwnerRepository ownerRepository) {
//                      this.ownerRepository = ownerRepository;
//                  }
//                 
//              }
//              """
//          )
//        );
//    }

}
