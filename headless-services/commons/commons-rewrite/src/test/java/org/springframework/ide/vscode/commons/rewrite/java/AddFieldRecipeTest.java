package org.springframework.ide.vscode.commons.rewrite.java;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class AddFieldRecipeTest implements RewriteTest {
	
	@Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddFieldRecipe("com.example.demo.OwnerRepository"))
        .parser(JavaParser.fromJavaVersion()
        		.logCompilationWarningsAndErrors(true)
                .dependsOn(
                    """
                    package com.example.demo;
                    
                    public class OwnerRepository {
                        // Stub definition for type resolution
                    }
                    """
                ));
    }
	
	@Test
    void addPrimitiveField() {
        rewriteRun(
        	spec -> spec.recipe(new AddFieldRecipe("String")),
            java(
                """
                    package com.example.demo;
                    
                    import com.example.demo.OwnerRepository;
                    class FooBar {
            		    
            		    public void test() {

            		    }
                    
                    }
                """,
                """
                    package com.example.demo;
                    
                    import com.example.demo.OwnerRepository;
                    class FooBar {
                    
                        private final String string;
            		    
            		    public void test() {

            		    }
                    
                    }               
                """
              )
        );
    }
	
	@Test
    void addCustomField() {
        rewriteRun(
        	spec -> spec.recipe(new AddFieldRecipe("OwnerRepository")),
            java(
                """
                    package com.example.demo;
                    
                    import com.example.demo.OwnerRepository;
                    class FooBar {
            		    
            		    public void test() {

            		    }
                    
                    }
                """,
                """
                    package com.example.demo;
                    
                    import com.example.demo.OwnerRepository;
                    class FooBar {
                    
                        private final OwnerRepository ownerRepository;
            		    
            		    public void test() {

            		    }
                    
                    }               
                """
              )
        );
    }

}
